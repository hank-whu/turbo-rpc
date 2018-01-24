package rpc.turbo.boot;

import org.springframework.beans.factory.Aware;

import rpc.turbo.server.TurboServer;

/**
 * TurboServer切入点, 实现类必须是被spring管理的
 */
public interface TurboServerAware extends Aware {

	/**
	 * 初始化完成后调用
	 * 
	 * @param turboServer
	 */
	void setTurboServer(TurboServer turboServer);

}
