package rpc.turbo.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import rpc.turbo.common.TurboConnectService;
import rpc.turbo.invoke.ServerInvokerFactory;

public final class TurboConnectServiceServerImpl implements TurboConnectService {

	private final ServerInvokerFactory invokerFactory;

	public TurboConnectServiceServerImpl(ServerInvokerFactory invokerFactory) {
		this.invokerFactory = invokerFactory;
	}

	@Override
	public CompletableFuture<Boolean> heartbeat() {
		return CompletableFuture.completedFuture(Boolean.TRUE);
	}

	@Override
	public CompletableFuture<List<String>> getClassRegisterList() {
		return CompletableFuture.completedFuture(invokerFactory.getClassRegisterList());
	}

	@Override
	public CompletableFuture<Map<String, Integer>> getMethodRegisterMap() {
		return CompletableFuture.completedFuture(invokerFactory.getMethodRegisterMap());
	}

	@Override
	public CompletableFuture<List<String>> getRestRegisterList() {
		return CompletableFuture.completedFuture(invokerFactory.getRestRegisterList());
	}

	@Override
	public CompletableFuture<Map<String, Integer>> getClassIdMap() {
		return CompletableFuture.completedFuture(invokerFactory.getClassIdMap());
	}

}
