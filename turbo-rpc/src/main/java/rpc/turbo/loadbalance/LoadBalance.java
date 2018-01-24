package rpc.turbo.loadbalance;

import java.util.List;

/**
 * 负载均衡，有状态的，线程安全的
 * 
 * @author Hank
 *
 */
public interface LoadBalance<T extends Weightable> {

	/**
	 * 可多次重复设置
	 * 
	 * @param weightables
	 */
	void setWeightables(List<T> weightables);

	/**
	 * 选出一个来
	 * 
	 * @return
	 */
	T select();
}
