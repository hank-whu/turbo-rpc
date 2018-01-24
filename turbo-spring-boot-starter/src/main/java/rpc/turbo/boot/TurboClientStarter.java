package rpc.turbo.boot;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;

import rpc.turbo.annotation.TurboFailover;
import rpc.turbo.annotation.TurboService;
import rpc.turbo.client.TurboClient;
import rpc.turbo.config.client.ClientConfig;
import rpc.turbo.util.ReflectUtils;

public class TurboClientStarter implements BeanFactoryPostProcessor, BeanPostProcessor, Ordered {
	private static final Log logger = LogFactory.getLog(TurboClientStarter.class);

	private ConfigurableListableBeanFactory beanFactory;
	private TurboClient turboClient;

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;

		try {
			ClientConfig clientConfig = ClientConfig.parse("turbo-client.conf");
			turboClient = new TurboClient(clientConfig);
		} catch (com.typesafe.config.ConfigException configException) {
			if (logger.isErrorEnabled()) {
				logger.error("turbo-client.conf 格式错误，无法开启TurboClient!", configException);
			}

			throw configException;
		} catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("类路径中找不到 turbo-client.conf，无法开启TurboClient!", e);
			}

			throw e;
		}

		Collection<Class<?>> turboClassList = extractTurboServiceClassList(beanFactory);

		for (Class<?> turboClass : turboClassList) {
			registerTurboService(turboClass);
		}
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (bean == null) {
			return bean;
		}

		tryInjectTurboServiceField(bean);
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean == null) {
			return bean;
		}

		if (bean instanceof TurboClientAware) {
			((TurboClientAware) bean).setTurboClient(turboClient);
		}

		Class<?> clazz = bean.getClass();
		TurboFailover turboFailover = clazz.getAnnotation(TurboFailover.class);

		if (turboFailover == null) {
			return bean;
		}

		if (logger.isInfoEnabled()) {
			logger.info("扫描到Failover实例，重置TurboFailover: " + clazz.getName() + turboFailover);
		}

		turboClient.setFailover(turboFailover.service(), bean);

		return bean;
	}

	@PreDestroy
	public void close() {
		if (turboClient == null) {
			return;
		}

		try {
			turboClient.close();
		} catch (Throwable e) {
			if (logger.isErrorEnabled()) {
				logger.error("TurboClient关闭失败!", e);
			}
		}
	}

	// 具体实现，下面的不用关注

	private void registerTurboService(Class<?> turboClass) {
		if (turboClient.getService(turboClass) != null) {
			return;
		}

		String[] exists = beanFactory.getBeanNamesForType(turboClass);
		// 如果已经注册则忽略，防止autowired byType冲突
		if (exists != null && exists.length > 0) {
			if (logger.isInfoEnabled()) {
				logger.info("spring中已存在: " + turboClass.getName() + ", 将不注册相应的远程服务对象, 通过反射对需要的字段直接赋值");
			}

			return;
		}

		String beanName = UPPER_CAMEL.to(LOWER_CAMEL, turboClass.getSimpleName());

		if (logger.isWarnEnabled() && beanFactory.containsBean(beanName)) {
			String existClass = null;

			BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
			if (beanDefinition != null) {
				existClass = beanDefinition.getBeanClassName();
			}

			if (existClass == null) {
				try {
					existClass = beanFactory.getBean(beanName).getClass().getName();
				} catch (Throwable t) {
				}
			}

			if (existClass != null) {
				String oldBeanName = beanName;
				beanName = turboClass.getName();

				logger.warn("spring中存在冲突的类名: [" + oldBeanName + "] " //
						+ existClass + "(existed), " + turboClass.getName() + "(current)" //
						+ ", 使用类全名称注册: " + beanName//
						+ ", 将只能通过 byType 或者 @Qualifier(\"" + beanName + "\") 使用");
			}
		}

		turboClient.register(turboClass);
		turboClient.setFailover(turboClass, null);
		Object serviceBean = turboClient.getService(turboClass);

		// 无法设置为primary，要防止autowired byType冲突
		beanFactory.registerSingleton(beanName, serviceBean);

		if (logger.isInfoEnabled()) {
			logger.info("spring中注册远程服务: " + beanName + "@" + turboClass.getName());
		}
	}

	private Collection<Class<?>> extractTurboServiceClassList(ConfigurableListableBeanFactory beanFactory) {
		LocalDateTime startTime = LocalDateTime.now();
		Set<Class<?>> turboServiceSet = new HashSet<>();
		String[] beanNames = beanFactory.getBeanDefinitionNames();

		for (int i = 0; i < beanNames.length; i++) {
			String beanName = beanNames[i];
			BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
			String beanClassName = beanDefinition.getBeanClassName();

			extractTurboServiceClass(turboServiceSet, beanClassName);
		}

		if (logger.isInfoEnabled()) {
			LocalDateTime finishTime = LocalDateTime.now();
			Duration duration = Duration.between(startTime, finishTime);

			String turboServiceString = turboServiceSet//
					.stream()//
					.map(clazz -> clazz.getName())//
					.collect(Collectors.joining(",", "[", "]"));

			logger.info("扫描到TurboService: " + turboServiceString);
			logger.info("扫描TurboService耗时: " + duration);
		}

		return turboServiceSet;
	}

	private void extractTurboServiceClass(Set<Class<?>> turboServiceSet, String beanClassName) {
		if (beanClassName == null || beanClassName.startsWith("org.springframework.")) {
			return;
		}

		Class<?> beanClass;
		try {
			beanClass = Class.forName(beanClassName, false, beanFactory.getBeanClassLoader());
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}

		Collection<Class<?>> allDependClass = ReflectUtils.getAllDependClass(beanClass, clazz -> {
			if (!clazz.isInterface()) {// 只支持接口
				return false;
			}

			TurboService turboService = clazz.getAnnotation(TurboService.class);

			if (turboService == null) {
				return false;
			}

			return true;
		});

		turboServiceSet.addAll(allDependClass);
	}

	private void tryInjectTurboServiceField(Object bean) {
		if (bean == null) {
			return;
		}

		Class<?> beanClass = bean.getClass();

		while (beanClass != Object.class) {
			tryInjectTurboServiceField(beanClass, bean);
			beanClass = beanClass.getSuperclass();
		}
	}

	private void tryInjectTurboServiceField(Class<?> beanClass, Object bean) {
		Field[] fields = beanClass.getDeclaredFields();
		for (int j = 0; j < fields.length; j++) {
			Field field = fields[j];
			Class<?> fieldClass = field.getType();

			if (!fieldClass.isInterface()) {// 只支持接口
				continue;
			}

			TurboService turboService = fieldClass.getAnnotation(TurboService.class);

			if (turboService == null) {
				continue;
			}

			Object serviceBean = turboClient.getService(fieldClass);
			if (serviceBean == null) {
				turboClient.register(fieldClass);
				turboClient.setFailover(fieldClass, null);

				serviceBean = turboClient.getService(fieldClass);
			}

			try {
				field.setAccessible(true);
				field.set(bean, serviceBean);
			} catch (Throwable t) {
				if (logger.isErrorEnabled()) {
					logger.error("手动注入TurboService失败，" + field, t);
				}

				continue;
			}
		}
	}
}
