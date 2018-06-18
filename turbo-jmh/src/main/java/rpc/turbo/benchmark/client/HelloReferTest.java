package rpc.turbo.benchmark.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import rpc.turbo.benchmark.service.HelloService;

@Component
public class HelloReferTest {
	@Autowired
	HelloService helloService;

	public void doSomeThing(String msg) {
		helloService.hello(msg)
			.thenAccept(message -> System.out.println(message));
	}
}
