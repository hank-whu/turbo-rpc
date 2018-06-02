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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import rpc.turbo.benchmark.bean.Page;
import rpc.turbo.benchmark.bean.User;
import rpc.turbo.benchmark.service.UserService;
import rpc.turbo.benchmark.service.UserServiceServerImpl;
import rpc.turbo.serialization.jackson.JacksonMapper;

@State(Scope.Thread)
public class JacksonBenchmark {
	public static final int CONCURRENCY = Runtime.getRuntime().availableProcessors();

	ByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
	ByteBuf userBuffer = allocator.directBuffer(1024 * 1024 * 8, 1024 * 1024 * 8);
	ByteBuf listBuffer = allocator.directBuffer(1024 * 1024 * 8, 1024 * 1024 * 8);

	private final JacksonMapper jacksonMapper = new JacksonMapper();

	private final UserService userService = new UserServiceServerImpl();

	private final User user = userService.getUser(123456789L).join();
	private final Page<User> userPage = userService.listUser(0).join();

	public JacksonBenchmark() {

		try {
			serializeUser();

			System.out.println("userBytes.length：" + userBuffer.writerIndex());

			System.out.println(deserializeUser());
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			serializeUserList();

			System.out.println("userPageBytes.length：" + listBuffer.writerIndex());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public void serializeUser() throws Exception {
		userBuffer.clear();
		jacksonMapper.write(userBuffer, user);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public User deserializeUser() throws Exception {
		userBuffer.readerIndex(0);
		return jacksonMapper.read(userBuffer, User.class);
	}

	@Benchmark
	@BenchmarkMode({ Mode.Throughput })
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public void serializeUserList() throws Exception {
		listBuffer.clear();
		jacksonMapper.write(listBuffer, userPage);
	}

	public static void main(String[] args) throws Exception {

		Options opt = new OptionsBuilder()//
				.include(JacksonBenchmark.class.getName())//
				.warmupIterations(3)//
				.measurementIterations(3)//
				.threads(CONCURRENCY)//
				.forks(1)//
				.build();

		new Runner(opt).run();

	}

}
