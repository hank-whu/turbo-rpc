package rpc.turbo.boot;

import org.springframework.beans.factory.Aware;

import rpc.turbo.client.TurboClient;

/**
 * TurboClient切入点, 实现类必须是被spring管理的
 */
public interface TurboClientAware extends Aware {

	/**
	 * 初始化完成后调用
	 * 
	 * @param turboClient
	 */
	void setTurboClient(TurboClient turboClient);

}
