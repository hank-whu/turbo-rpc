package rpc.turbo.config;

import java.lang.reflect.Method;

import rpc.turbo.annotation.TurboService;
import rpc.turbo.invoke.InvokerUtils;

/**
 * 服务端、客户端通用
 * 
 * @author Hank
 *
 */
public class MethodConfig {
	/** 服务方法 */
	public final Method method;
	/** 版本 */
	public final String version;
	/** millseconds */
	public final long timeout;
	/** 忽略 */
	public final boolean ignore;
	/** rest路径 */
	public final String rest;

	/**
	 * @param method
	 *            服务方法
	 */
	public MethodConfig(Method method) {
		this.method = method;

		this.version = version(method);
		this.timeout = timeout(method);
		this.ignore = ignore(method);
		this.rest = rest(method);
	}

	public MethodConfig(Method method, String version, long timeout, boolean ignore, String rest) {
		this.method = method;
		this.version = version;
		this.timeout = timeout;
		this.ignore = ignore;
		this.rest = rest;
	}

	private String version(Method method) {
		String version = TurboService.DEFAULT_VERSION;

		TurboService config = method.getDeclaringClass().getAnnotation(TurboService.class);
		if (config == null) {
			config = method.getAnnotation(TurboService.class);
		}

		if (config != null) {
			version = config.version();
		}

		int delimterIndex = version.indexOf('.');
		if (delimterIndex > 0) {
			version = version.substring(0, delimterIndex);
		}

		return version;
	}

	private long timeout(Method method) {
		long timeout = TurboService.DEFAULT_TIME_OUT;

		TurboService config = method.getDeclaringClass().getAnnotation(TurboService.class);
		if (config == null) {
			config = method.getAnnotation(TurboService.class);
		}

		if (config != null) {
			timeout = config.timeout();
		}

		if (timeout < 1) {
			timeout = TurboService.DEFAULT_TIME_OUT;
		}

		return timeout;
	}

	private boolean ignore(Method method) {
		boolean ignore = TurboService.DEFAULT_IGNORE;

		TurboService config = method.getDeclaringClass().getAnnotation(TurboService.class);

		if (config != null) {
			ignore = config.ignore();
		}

		if (!ignore) {
			config = method.getAnnotation(TurboService.class);

			if (config != null) {
				ignore = config.ignore();
			}
		}

		return ignore;
	}

	private String rest(Method method) {
		return InvokerUtils.getRestPath(method);
	}

	@Override
	public String toString() {
		return "RemoteMethodConfig{" + //
				"method=" + method + //
				", version='" + version + '\'' + //
				", timeout=" + timeout + //
				", ignore=" + ignore + //
				", rest='" + rest + '\'' + //
				'}';
	}
}
