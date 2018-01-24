package rpc.turbo.loadbalance;

/**
 * 
 * @author Hank
 *
 * @param <T>
 */
public interface LoadBalanceFactory<T extends Weightable> {

	/**
	 * 创建一个新的LoadBalance
	 * 
	 * @return
	 */
	LoadBalance<T> newLoadBalance();
}
