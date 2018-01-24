package rpc.turbo.transport.client;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.netty.channel.EventLoopGroup;
import rpc.turbo.annotation.TurboService;
import rpc.turbo.common.RemoteContext;
import rpc.turbo.common.TurboConnectService;
import rpc.turbo.config.HostPort;
import rpc.turbo.config.client.AppConfig;
import rpc.turbo.filter.RpcClientFilter;
import rpc.turbo.invoke.Invoker;
import rpc.turbo.invoke.InvokerUtils;
import rpc.turbo.loadbalance.Weightable;
import rpc.turbo.param.EmptyMethodParam;
import rpc.turbo.param.MethodParam;
import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;
import rpc.turbo.protocol.ResponseStatus;
import rpc.turbo.remote.RemoteException;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.transport.client.future.ResponseFutureContainer;
import rpc.turbo.util.concurrent.AtomicMuiltInteger;
import rpc.turbo.util.concurrent.ConcurrentIntToIntArrayMap;
import rpc.turbo.util.concurrent.ConcurrentIntegerSequencer;

final class ConnectorContext implements Weightable, Closeable {
	private static final Log logger = LogFactory.getLog(ConnectorContext.class);

	private final AppConfig appConfig;
	public final HostPort serverAddress;

	private final int connectCount;
	private final ResponseFutureContainer futureContainer = new ResponseFutureContainer();
	private final NettyClientConnector connector;
	private final ConcurrentIntegerSequencer sequencer = new ConcurrentIntegerSequencer(0, true);
	private final Semaphore requestWaitSemaphore;
	private final AtomicMuiltInteger errorCounter;
	private final int globalTimeout;
	private final CopyOnWriteArrayList<RpcClientFilter> filters;

	private final Method heartbeatMethod;
	private final String heartbeatServiceMethodName;

	private final ConcurrentIntToIntArrayMap methodIdToServiceIdMap = new ConcurrentIntToIntArrayMap();
	private volatile Map<String, Integer> serviceMethodNameToServiceIdMap;
	private volatile int weight;
	private volatile boolean isClosed = false;

	ConnectorContext(EventLoopGroup eventLoopGroup, AppConfig appConfig, Serializer serializer,
			CopyOnWriteArrayList<RpcClientFilter> filters, HostPort serverAddress) {
		this.appConfig = appConfig;
		this.connectCount = appConfig.getConnectPerServer();

		this.connector = new NettyClientConnector(eventLoopGroup, serializer, futureContainer, serverAddress,
				connectCount);
		this.serverAddress = serverAddress;

		this.errorCounter = new AtomicMuiltInteger(connectCount);
		this.requestWaitSemaphore = new Semaphore(appConfig.getMaxRequestWait());

		this.globalTimeout = appConfig.getGlobalTimeout();

		this.filters = filters;

		try {
			heartbeatMethod = TurboConnectService.class.getDeclaredMethod("heartbeat");
			heartbeatServiceMethodName = InvokerUtils.getServiceMethodName(appConfig.getGroup(), appConfig.getApp(),
					heartbeatMethod);
		} catch (Exception e) {
			throw new RemoteException("error on init", e);
		}
	}

	boolean isSupport(String serviceMethodName) {
		if (serviceMethodNameToServiceIdMap == null) {
			return false;
		}

		return serviceMethodNameToServiceIdMap.containsKey(serviceMethodName);
	}

	boolean heartbeat() {

		return IntStream//
				.range(0, connector.connectCount())//
				.mapToObj(index -> {

					int requestId = sequencer.next();
					Request request = new Request();
					request.setServiceId(TurboConnectService.SERVICE_HEARTBEAT);
					request.setRequestId(requestId);

					CompletableFuture<Response> future = new CompletableFuture<>();
					futureContainer.addFuture(requestId, future, TurboService.DEFAULT_TIME_OUT);

					try {
						requestWaitSemaphore.acquire();

						boolean allowSend = doRequestFilter(request, heartbeatMethod, heartbeatServiceMethodName);
						if (allowSend) {
							connector.send(index, request);
						} else {
							future.completeExceptionally(new RemoteException(RpcClientFilter.CLIENT_FILTER_DENY, false));
						}
					} catch (Exception e) {
						future.completeExceptionally(e);
					}

					CompletableFuture<Boolean> result = handleResult(request, future);

					return result;
				})//
				.allMatch(future -> {
					try {
						return future.join();
					} catch (Throwable e) {
						if (logger.isWarnEnabled()) {
							logger.warn(serverAddress + " heartbeat error", e);
						}

						return false;
					}
				});
	}

	/**
	 * 远程调用，无参，无失败回退
	 * 
	 * @param serviceId
	 *            远程serviceId
	 * @param timeout
	 *            millseconds
	 * @return
	 */
	<T> CompletableFuture<T> execute(int serviceId, long timeout) {
		return execute(serviceId, timeout, null, null);
	}

	/**
	 * 远程调用
	 * 
	 * @param serviceId
	 *            远程serviceId
	 * @param timeout
	 *            millseconds
	 * @param methodParam
	 *            方法参数对象，无参类型为null
	 * @param failoverInvoker
	 *            失败回退
	 * @return
	 */
	<T> CompletableFuture<T> execute(int serviceId, long timeout, MethodParam methodParam,
			Invoker<CompletableFuture<?>> failoverInvoker) {

		if (isClosed) {
			throw new RemoteException("已关闭的连接!");
		}

		int requestId = sequencer.next();

		for (int i = 0; i < connectCount; i++) {// 最多循环一遍
			if (isZombie(channelIndex(requestId))) {
				requestId = sequencer.next();
				continue;
			}

			if (isZombie()) {
				throw new RemoteException("this connector is zombie");
			}

			break;
		}

		Request request = new Request();
		request.setServiceId(serviceId);
		request.setRequestId(requestId);

		if (methodParam instanceof EmptyMethodParam) {
			request.setMethodParam(null);
		} else {
			request.setMethodParam(methodParam);
		}

		if (globalTimeout > 0) {
			timeout = globalTimeout;
		}

		CompletableFuture<Response> future = new CompletableFuture<>();
		futureContainer.addFuture(requestId, future, timeout);

		try {
			requestWaitSemaphore.acquire();

			boolean allowSend = doRequestFilter(request);
			if (allowSend) {
				connector.send(channelIndex(request), request);
			} else {
				future.completeExceptionally(new RemoteException(RpcClientFilter.CLIENT_FILTER_DENY, false));
			}
		} catch (Exception e) {
			future.completeExceptionally(e);
		}

		if (failoverInvoker == null) {
			return handleResult(request, future);
		} else {
			return handleResult(request, future, failoverInvoker, methodParam);
		}
	}

	private int channelIndex(int requestId) {
		return requestId % connectCount;
	}

	private int channelIndex(Request request) {
		return channelIndex(request.getRequestId());
	}

	/**
	 * 处理返回值，无失败回退
	 * 
	 * @param request
	 * @param future
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> CompletableFuture<T> handleResult(final Request request, final CompletableFuture<Response> future) {

		final Method method;
		final String serviceMethodName;
		if (filters.size() == 0) {
			method = null;
			serviceMethodName = null;
		} else {
			method = RemoteContext.getRemoteMethod();
			serviceMethodName = RemoteContext.getServiceMethodName();
		}

		return future.handle((response, throwable) -> {
			requestWaitSemaphore.release();

			boolean error = false;
			if (throwable != null) {
				if (logger.isWarnEnabled()) {
					logger.warn("request error, requestId: " + request.getRequestId(), throwable);
				}

				error = true;
			}

			if (!error && response == null) {
				String msg = "request error, requestId: " + request.getRequestId();
				if (logger.isWarnEnabled()) {
					logger.warn(msg);
				}

				error = true;
			}

			if (!error && response.getStatusCode() != ResponseStatus.OK) {
				String msg = " status code is" + response.getStatusCode() + " reason is " + response.getResult();

				if (logger.isWarnEnabled()) {
					logger.warn(msg);
				}

				error = true;
			}

			doResponseFilter(request, response, method, serviceMethodName, throwable);

			int channelIndex = channelIndex(request);
			if (error) {
				errorCounter.incrementAndGet(channelIndex);
				futureContainer.remove(request.getRequestId());

				return null;
			} else {
				errorCounter.reset(channelIndex);
				return (T) response.getResult();
			}
		});
	}

	/**
	 * 处理返回值，带失败回退
	 * 
	 * @param request
	 * @param future
	 * @param failoverInvoker
	 * @param methodParam
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> CompletableFuture<T> handleResult(//
			final Request request, //
			final CompletableFuture<Response> future, //
			final Invoker<CompletableFuture<?>> failoverInvoker, //
			final MethodParam methodParam) {

		final Method method;
		final String serviceMethodName;
		if (filters.size() == 0) {
			method = null;
			serviceMethodName = null;
		} else {
			method = RemoteContext.getRemoteMethod();
			serviceMethodName = RemoteContext.getServiceMethodName();
		}

		CompletableFuture<T> futureWithFailover = new CompletableFuture<>();

		future.whenComplete((response, throwable) -> {
			requestWaitSemaphore.release();

			boolean error = false;
			if (throwable != null) {
				if (logger.isWarnEnabled()) {
					logger.warn("request error, requestId: " + request.getRequestId(), throwable);
				}

				error = true;
			}

			if (!error && response == null) {
				if (logger.isWarnEnabled()) {
					logger.warn("request error, requestId: " + request.getRequestId());
				}

				error = true;
			}

			if (!error && response.getStatusCode() != ResponseStatus.OK) {
				String msg = " status code is" + response.getStatusCode() + " reason is " + response.getResult();

				if (logger.isWarnEnabled()) {
					logger.warn("request error, requestId: " + request.getRequestId() + msg);
				}

				error = true;
			}

			doResponseFilter(request, response, method, serviceMethodName, throwable);

			int channelIndex = channelIndex(request);
			if (error) {
				if (logger.isInfoEnabled()) {
					logger.info("远程调用发生错误，使用本地回退方法执行");
				}

				errorCounter.incrementAndGet(channelIndex);
				futureContainer.remove(request.getRequestId());

				failoverInvoker.invoke(methodParam).whenComplete((r, t) -> {
					if (t != null) {
						futureWithFailover.completeExceptionally(t);
					} else {
						futureWithFailover.complete((T) r);
					}
				});
			} else {
				errorCounter.reset(channelIndex);
				futureWithFailover.complete((T) response.getResult());
			}
		});

		return futureWithFailover;
	}

	private boolean doRequestFilter(Request request) {
		final int filterLength = filters.size();
		if (filterLength == 0) {
			return true;
		}

		RemoteContext.setServerAddress(connector.serverAddress);
		RemoteContext.setClientAddress(connector.clientAddress);
		// App中赋值 RemoteContext.setRemoteMethod(method);
		// RemoteContext.setServiceMethodName(serviceMethodName);

		for (int i = 0; i < filterLength; i++) {
			RpcClientFilter filter = filters.get(i);
			if (!filter.onSend(request)) {
				return false;
			}
		}

		return true;
	}

	private boolean doRequestFilter(Request request, Method method, String serviceMethodName) {
		final int filterLength = filters.size();
		if (filterLength == 0) {
			return true;
		}

		RemoteContext.setServerAddress(connector.serverAddress);
		RemoteContext.setClientAddress(connector.clientAddress);
		RemoteContext.setRemoteMethod(method);
		RemoteContext.setServiceMethodName(serviceMethodName);

		for (int i = 0; i < filterLength; i++) {
			RpcClientFilter filter = filters.get(i);
			if (!filter.onSend(request)) {
				return false;
			}
		}

		return true;
	}

	private void doResponseFilter(Request request, Response response, Method method, String serviceMethodName,
			Throwable throwable) {
		final int filterLength = filters.size();
		if (filterLength == 0) {
			return;
		}

		RemoteContext.setServerAddress(connector.serverAddress);
		RemoteContext.setClientAddress(connector.clientAddress);
		RemoteContext.setRemoteMethod(method);
		RemoteContext.setServiceMethodName(serviceMethodName);

		if (response.getStatusCode() == ResponseStatus.OK) {
			for (int i = 0; i < filterLength; i++) {
				RpcClientFilter filter = filters.get(i);
				filter.onRecive(request, response);
			}
		} else {
			for (int i = 0; i < filterLength; i++) {
				RpcClientFilter filter = filters.get(i);
				filter.onError(request, response, throwable);
			}
		}
	}

	int getServiceId(int methodId) {
		return methodIdToServiceIdMap.get(methodId);
	}

	void putServiceId(String serviceMethodName, int methodId) {
		Integer serviceId = serviceMethodNameToServiceIdMap.get(serviceMethodName);
		if (serviceId == null) {
			return;
		}

		methodIdToServiceIdMap.put(methodId, serviceId);
	}

	void clear() {
		methodIdToServiceIdMap.clear();
	}

	public void setServiceMethodNameToServiceIdMap(Map<String, Integer> serviceMethodNameToServiceIdMap) {
		this.serviceMethodNameToServiceIdMap = serviceMethodNameToServiceIdMap;
	}

	public void setWeight(int weight) {
		this.weight = weight;
	}

	void doExpireJob() {
		futureContainer.doExpireJob();
	}

	boolean isZombie() {

		int sum = 0;
		boolean allZombie = true;

		for (int i = 0; i < connectCount; i++) {
			int error = errorCounter.get(i);
			sum += error;

			if (error < appConfig.getConnectErrorThreshold()) {
				allZombie = false;
			}
		}

		return sum >= appConfig.getServerErrorThreshold() || allZombie;
	}

	/**
	 * 
	 * @param index
	 * @return
	 */
	private boolean isZombie(int index) {
		return errorCounter.get(index) >= appConfig.getConnectErrorThreshold();
	}

	void connect() throws InterruptedException {
		connector.connect();
		errorCounter.resetAll();
	}

	@Override
	public int weight() {
		return weight;
	}

	public boolean isClosed() {
		return isClosed;
	}

	@Override
	public void close() throws IOException {
		if (isClosed) {
			return;
		}

		isClosed = true;
		futureContainer.close();
		connector.close();
	}

}
