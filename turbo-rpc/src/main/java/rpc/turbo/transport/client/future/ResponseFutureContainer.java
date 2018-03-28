package rpc.turbo.transport.client.future;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import rpc.turbo.annotation.TurboService;
import rpc.turbo.protocol.Response;
import rpc.turbo.transport.client.exception.ConnectionException;
import rpc.turbo.transport.client.exception.ResponseTimeoutException;
import rpc.turbo.util.SystemClock;

/**
 * 
 * @author Hank
 *
 */
public final class ResponseFutureContainer implements Closeable {
	private volatile boolean isClosing = false;

	private final ConcurrentHashMap<Integer, FutureWithExpire<Response>> futureMap = //
			new ConcurrentHashMap<>(32);

	public void addFuture(int requestId, CompletableFuture<Response> future) {
		addFuture(requestId, future, TurboService.DEFAULT_TIME_OUT);
	}

	public void addFuture(int requestId, CompletableFuture<Response> future, long timeout) {
		if (future.isDone()) {
			return;
		}

		if (isClosing) {
			throw new ConnectionException("it's closed");
		}

		long expireTime = timeout + SystemClock.fast().mills();

		futureMap.put(requestId, new FutureWithExpire<>(future, expireTime));
	}

	public void remove(int requestId) {
		futureMap.remove(requestId);
	}

	public void notifyResponse(Response response) {
		if (response == null) {
			return;
		}

		FutureWithExpire<Response> futureWithExpire = futureMap.remove(response.getRequestId());

		if (futureWithExpire == null) {
			return;
		}

		futureWithExpire.future.complete(response);
	}

	/**
	 * 外部线程周期性调用
	 */
	public void doExpireJob() {
		if (isClosing) {
			return;
		}

		futureMap.forEach((key, value) -> {
			doExpire(key, value);
		});
	}

	private void doExpire(int requestId, FutureWithExpire<Response> futureWithExpire) {
		CompletableFuture<Response> future = futureWithExpire.future;

		if (future.isDone()) {
			return;
		}

		long currentTime = SystemClock.fast().mills();
		if (futureWithExpire.expireTime > currentTime) {
			return;
		}

		future.completeExceptionally(ResponseTimeoutException.NONE_STACK_TRACE);

		futureMap.remove(requestId);
	}

	/**
	 * 会尝试平滑退出, 不会实际抛出异常
	 */
	@Override
	public void close() throws IOException {
		// 尝试平滑退出
		for (int i = 0; i < TurboService.DEFAULT_TIME_OUT; i += 100) {
			doExpireJob();

			if (futureMap.isEmpty()) {
				break;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				break;
			}
		}

		isClosing = true;
	}
}