package rpc.turbo.loadbalance;

public class RoundRobinLoadBalanceFactory<T extends Weightable> implements LoadBalanceFactory<T> {

	@Override
	public LoadBalance<T> newLoadBalance() {
		return new RoundRobinLoadBalance<>();
	}

}
