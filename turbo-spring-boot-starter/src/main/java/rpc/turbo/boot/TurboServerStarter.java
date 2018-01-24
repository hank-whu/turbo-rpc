package rpc.turbo.boot;

import static rpc.turbo.util.tuple.Tuple.tuple;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import rpc.turbo.annotation.TurboFailover;
import rpc.turbo.annotation.TurboService;
import rpc.turbo.config.server.ServerConfig;
import rpc.turbo.invoke.ServerInvokerFactory;
import rpc.turbo.server.TurboServer;
import rpc.turbo.util.tuple.Tuple2;
import rpc.turbo.util.tuple.Tuple3;

@Configuration
@ConditionalOnClass({ TurboService.class, EnableTurboServer.class })
@Order(Ordered.LOWEST_PRECEDENCE)
public class TurboServerStarter {
	private static final Log logger = LogFactory.getLog(TurboServerStarter.class);

	@Autowired
	private GenericApplicationContext applicationContext;

	private TurboServer turboServer;

	@PreDestroy
	public void close() {
		if (turboServer == null) {
			return;
		}

		try {
			turboServer.close();
		} catch (Throwable e) {
			if (logger.isErrorEnabled()) {
				logger.error("TurboServer关闭失败!", e);
			}
		}
	}

	@PostConstruct
	public void startTuroboServer() {

		ServerConfig serverConfig;
		try {
			serverConfig = ServerConfig.parse("turbo-server.conf");
		} catch (com.typesafe.config.ConfigException configException) {
			if (logger.isErrorEnabled()) {
				logger.error("turbo-server.conf 格式错误，无法开启TurboServer!", configException);
			}

			return;
		} catch (Throwable e) {
			if (logger.isErrorEnabled()) {
				logger.error("类路径中找不到 turbo-server.conf，无法开启TurboServer!", e);
			}

			return;
		}

		@SuppressWarnings("rawtypes")
		Collection<Tuple3<TurboService, Class, Object>> turboServiceList = getTurboServiceList();

		if (turboServiceList.isEmpty()) {
			if (logger.isErrorEnabled()) {
				logger.error("找不到有效的 TurboService，无法开启TurboServer!");
			}

			return;
		}

		ServerInvokerFactory invokerFactory = new ServerInvokerFactory(serverConfig.getGroup(), serverConfig.getApp());
		turboServiceList.forEach(t3 -> {
			invokerFactory.register(t3._2, t3._3);
		});

		try {
			turboServer = new TurboServer(serverConfig, invokerFactory);
			turboServer.startAndRegisterServer();

			Map<String, TurboServerAware> turboServerAwareMap//
					= applicationContext.getBeansOfType(TurboServerAware.class);

			turboServerAwareMap.forEach((key, value) -> value.setTurboServer(turboServer));
		} catch (Throwable e) {
			if (logger.isErrorEnabled()) {
				logger.error("TurboServer启动失败!", e);
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private Collection<Tuple3<TurboService, Class, Object>> getTurboServiceList() {
		Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);

		if (beans == null || beans.isEmpty()) {
			return List.of();
		}

		Map<Class, Tuple3<TurboService, Class, Object>> turboServiceMap = beans//
				.entrySet()//
				.parallelStream()//
				.map(kv -> {
					Object bean = kv.getValue();
					Tuple2<TurboService, Class> t2 = getTurboService(bean);

					if (t2 == null) {
						return null;
					}

					if (logger.isDebugEnabled()) {
						logger.debug("find TurboService: " + kv.getKey() + " " + t2._2.getName() + t2._1);
					}

					return tuple(t2._1, t2._2, bean);
				})//
				.filter(kv -> kv != null)//
				.collect(Collectors.toConcurrentMap(//
						t3 -> t3._2, //
						t3 -> t3, //
						(Tuple3<TurboService, Class, Object> v1, Tuple3<TurboService, Class, Object> v2) -> {
							if (logger.isWarnEnabled()) {
								TurboService turboService = v1._1;
								Class turboServiceClass = v1._2;

								Class implClass1 = v1._3.getClass();
								Class implClass2 = v2._3.getClass();

								logger.warn("存在冲突 TurboService: " + turboServiceClass.getName() + turboService //
										+ ", 生效: " + implClass1.getName() //
										+ ", 忽略: " + implClass2.getName());
							}

							return v1;
						}));

		return turboServiceMap.values();
	}

	@SuppressWarnings("rawtypes")
	private Tuple2<TurboService, Class> getTurboService(Object bean) {
		if (bean == null) {
			return null;
		}

		Class<?> beanClass = bean.getClass();

		if (beanClass.getAnnotation(TurboFailover.class) != null) {
			return null;
		}

		Class<?>[] interfaces = beanClass.getInterfaces();
		if (interfaces == null || interfaces.length == 0) {
			return null;
		}

		for (int i = 0; i < interfaces.length; i++) {
			Class<?> interfaceClass = interfaces[i];

			TurboService turboService = interfaceClass.getAnnotation(TurboService.class);
			if (turboService != null) {
				return tuple(turboService, interfaceClass);
			}
		}

		return null;
	}

}
