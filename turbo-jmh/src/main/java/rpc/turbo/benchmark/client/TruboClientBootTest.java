package rpc.turbo.benchmark.client;

import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;

import rpc.turbo.benchmark.bean.User;
import rpc.turbo.benchmark.service.UserService;
import rpc.turbo.boot.EnableTurboClient;

@SpringBootApplication(scanBasePackages = { "rpc.turbo.benchmark.service", "rpc.turbo.benchmark.client" })
@EnableTurboClient
public class TruboClientBootTest {

	@Autowired
	GenericApplicationContext applicationContext;

	@Autowired
	UserService userService;

	@Autowired
	// @Qualifier("userService")
	UserService userService2;

	@PostConstruct
	public void test() {
		System.out.println(Arrays.toString(applicationContext.getBeanNamesForType(UserService.class)));
		System.out.println("userService: " + userService.getClass().getName());
		System.out.println("userService2: " + userService2.getClass().getName());

		System.out.println("getUser:");
		User user = userService.getUser(1).join();
		System.out.println(user);
		System.out.println("=====================");
		System.out.println();

		System.out.println("createUser:");
		System.out.println(userService2.createUser(user).join());
		System.out.println("=====================");
		System.out.println();

		System.out.println("listUser:");
		System.out.println(userService.listUser(1).join());
		System.out.println("=====================");
		System.out.println();
	}

	public static void main(String[] args) {
		SpringApplication.run(TruboClientBootTest.class, args);
	}
}
