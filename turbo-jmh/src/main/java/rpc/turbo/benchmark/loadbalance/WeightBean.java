package rpc.turbo.benchmark.loadbalance;

import rpc.turbo.loadbalance.Weightable;

public class WeightBean implements Weightable {

	public final int index;
	public final int weight;

	public WeightBean(int index, int weight) {
		this.index = index;
		this.weight = weight;
	}

	@Override
	public int weight() {
		return weight;
	}

}
