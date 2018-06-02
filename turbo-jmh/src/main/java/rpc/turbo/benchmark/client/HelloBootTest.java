package rpc.turbo.benchmark.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;

import rpc.turbo.benchmark.service.HelloService;
import rpc.turbo.benchmark.service.UserService;
import rpc.turbo.boot.EnableTurboClient;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@SpringBootApplication(scanBasePackages = { "com.hello" })
@EnableTurboClient
public class HelloBootTest {

	@Autowired
	HelloService helloService;


	public static void main(String[] args) {
		SpringApplication.run(HelloBootTest.class, args);
	}
}
