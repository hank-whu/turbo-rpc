package rpc.turbo.benchmark.serialization;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.DefaultStreamFactory;
import com.esotericsoftware.kryo.util.MapReferenceResolver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import rpc.turbo.benchmark.bean.Page;
import rpc.turbo.benchmark.bean.User;
import rpc.turbo.benchmark.service.UserService;
import rpc.turbo.benchmark.service.UserServiceServerImpl;
import rpc.turbo.serialization.kryo.ByteBufInput;
import rpc.turbo.serialization.kryo.ByteBufOutput;
import rpc.turbo.serialization.kryo.FastClassResolver;

@State(Scope.Thread)
public class KryoBenchmark {
	public static final int CONCURRENCY = Runtime.getRuntime().availableProcessors();

	ByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
	ByteBuf userBuffer = allocator.directBuffer(1024 * 1024 * 8, 1024 * 1024 * 8);
	ByteBuf listBuffer = allocator.directBuffer(1024 * 1024 * 8, 1024 * 1024 * 8);

	private final Kryo kryo = new Kryo(new FastClassResolver(), new MapReferenceResolver(), new DefaultStreamFactory());

	private final ByteBufOutput output = new ByteBufOutput(null);
	private final ByteBufInput input = new ByteBufInput(null);

	private final UserService userService = new UserServiceServerImpl();

	private final User user = userService.getUser(123456789L).join();
	private final Page<User> userPage = userService.listUser(0).join();

	public KryoBenchmark() {

		try {
			userBuffer.clear();
			output.setBuffer(userBuffer);
			kryo.writeClassAndObject(output, user);

			System.out.println("userBytes.length：" + userBuffer.writerIndex());
			System.out.println(deserializeUser());
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			listBuffer.clear();
			output.setBuffer(listBuffer);
			kryo.writeClassAndObject(output, userPage);

			System.out.println("userPageBytes.length：" + listBuffer.writerIndex());
			System.out.println(deserializeUserPage());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public void serializeUser() throws Exception {
		userBuffer.clear();
		output.setBuffer(userBuffer);
		kryo.writeClassAndObject(output, user);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public User deserializeUser() throws Exception {
		userBuffer.readerIndex(0);
		input.setBuffer(userBuffer);

		return (User) kryo.readClassAndObject(input);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public void serializeUserList() throws Exception {
		listBuffer.clear();
		output.setBuffer(listBuffer);
		kryo.writeClassAndObject(output, userPage);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@SuppressWarnings("unchecked")
	public Page<User> deserializeUserPage() throws Exception {
		listBuffer.readerIndex(0);
		input.setBuffer(listBuffer);

		return (Page<User>) kryo.readClassAndObject(input);
	}

	public static void main(String[] args) throws Exception {

		Options opt = new OptionsBuilder()//
				.include(KryoBenchmark.class.getName())//
				.include(ProtostuffBenchmark.class.getName())//
				.warmupIterations(5)//
				.measurementIterations(5)//
				.threads(CONCURRENCY)//
				.forks(1)//
				.build();

		new Runner(opt).run();

	}

}
