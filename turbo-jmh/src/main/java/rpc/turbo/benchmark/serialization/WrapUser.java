package rpc.turbo.benchmark.serialization;

import rpc.turbo.benchmark.bean.User;

public class WrapUser {
	private Object user;

	public Object getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

}
