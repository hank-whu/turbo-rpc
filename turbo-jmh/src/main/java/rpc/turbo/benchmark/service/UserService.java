package rpc.turbo.benchmark.service;

import rpc.turbo.annotation.TurboService;
import rpc.turbo.benchmark.bean.Page;
import rpc.turbo.benchmark.bean.User;

import java.util.concurrent.CompletableFuture;

@TurboService(version = "1.0.0", rest = "user")
public interface UserService {

	/**
	 * 会自动注册为失败回退方法，当远程调用失败时执行
	 * 
	 * @param email
	 * @return
	 */
	@TurboService(version = "2.1.2", rest = "exist")
	default public CompletableFuture<Boolean> existUser(String email) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return CompletableFuture.completedFuture(Boolean.TRUE);
	}

	@TurboService(version = "2.1.2", rest = "create")
	public CompletableFuture<Boolean> createUser(User user);

	@TurboService(version = "2.1.2", rest = "get")
	public CompletableFuture<User> getUser(long id);

	@TurboService(version = "1.2.1", rest = "list")
	public CompletableFuture<Page<User>> listUser(int pageNo);

}
