package rpc.turbo.benchmark.server;

import java.util.Map;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import rpc.turbo.benchmark.service.UserService;
import rpc.turbo.benchmark.service.UserServiceServerImpl;
import rpc.turbo.config.HostPort;
import rpc.turbo.server.TurboServer;

public class RestServerBenchmark {
	public static void main(String[] args) throws Exception {
		ResourceLeakDetector.setLevel(Level.DISABLED);
		// CtClass.debugDump = "d:/debugDump";

		try (TurboServer server = new TurboServer("shop", "auth");) {
			Map<Class<?>, Object> services = Map.of(UserService.class, new UserServiceServerImpl());
			server.registerService(services);

			server.startRestServer(new HostPort("0.0.0.0", 8080));
			server.waitUntilShutdown();
		}
	}
}
