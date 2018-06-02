package rpc.turbo.benchmark.map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.jctools.maps.NonBlockingHashMapLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import rpc.turbo.util.concurrent.ConcurrentIntToObjectArrayMap;

@State(Scope.Benchmark)
public class ConcurrentHashMapBenchmark {
	public static final int CONCURRENCY = Runtime.getRuntime().availableProcessors();

	private final ConcurrentIntToObjectArrayMap<Integer> intArrayMap = new ConcurrentIntToObjectArrayMap<>();
	private final ConcurrentHashMap<Integer, Integer> concurrentMap = new ConcurrentHashMap<>();
	private final NonBlockingHashMapLong<Integer> nonBlockingHashMapLong = new NonBlockingHashMapLong<>();

	public ConcurrentHashMapBenchmark() {
		for (int i = 0; i < 1024 * 256; i++) {
			intArrayMap.put(i, i);
			concurrentMap.put(i, i);
			nonBlockingHashMapLong.put(i, i);
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void _do_nothing() {
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public Integer intArrayMap() {
		int random = ThreadLocalRandom.current().nextInt(1024 * 256);
		intArrayMap.put(random, null);
		intArrayMap.put(random, random);
		return intArrayMap.get(512);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public Integer getOrUpdateIntArrayMap() {
		int random = ThreadLocalRandom.current().nextInt(1024 * 256);
		intArrayMap.put(random, null);
		intArrayMap.put(random, random);
		return intArrayMap.getOrUpdate(17, () -> 1);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public Integer concurrentMap() {
		int random = ThreadLocalRandom.current().nextInt(1024 * 256);
		concurrentMap.remove(random);
		concurrentMap.put(random, random);
		return concurrentMap.get(512);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public Integer nonBlockingHashMapLong() {
		int random = ThreadLocalRandom.current().nextInt(1024 * 256);
		nonBlockingHashMapLong.remove(random);
		nonBlockingHashMapLong.put(random, random);
		return nonBlockingHashMapLong.get(512);
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()//
				.include(ConcurrentHashMapBenchmark.class.getSimpleName())//
				.warmupIterations(5)//
				.measurementIterations(5)//
				.threads(CONCURRENCY)//
				.forks(1)//
				.build();

		new Runner(opt).run();
	}

}
