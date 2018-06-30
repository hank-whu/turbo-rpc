package rpc.turbo.transport.client.sender;

import java.io.Closeable;

import rpc.turbo.transport.client.future.RequestWithFuture;

public interface Sender extends Closeable {

	public void send(RequestWithFuture request);
}
