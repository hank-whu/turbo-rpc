package rpc.turbo.benchmark.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import rpc.turbo.benchmark.service.UserService;
import rpc.turbo.boot.EnableTurboServer;

@SpringBootApplication(scanBasePackages = { "rpc.turbo.benchmark.service", "rpc.turbo.benchmark.server" })
@EnableTurboServer
public class TruboServerBootTest {

	@Autowired
	UserService userService;

	public static void main(String[] args) {
		SpringApplication.run(TruboServerBootTest.class, args);
	}
}
