package rpc.turbo.loadbalance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 速度略慢 115.069 ± 1.193 ops/us
 * 
 * @author Hank
 *
 * @param <T>
 *            必须为Weightable子类
 */
public class RandomLoadBalance<T extends Weightable> implements LoadBalance<T> {

	protected volatile WeightableGroup<T> weightableGroup = null;

	@Override
	public void setWeightables(List<T> weightables) {
		weightableGroup = new WeightableGroup<>(weightables);
	}

	@Override
	public T select() {
		if (weightableGroup == null) {
			return null;
		}

		int sum = weightableGroup.sum();

		if (sum < 2) {
			return weightableGroup.get(0);
		}

		int seed = ThreadLocalRandom.current().nextInt(sum);
		return weightableGroup.get(seed);
	}

}
