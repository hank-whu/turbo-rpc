package rpc.turbo.transport.server.rpc.handler;

import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import rpc.turbo.config.TurboConstants;
import rpc.turbo.filter.RpcServerFilter;
import rpc.turbo.invoke.ServerInvokerFactory;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.transport.server.rpc.codec.RequestDecoder;
import rpc.turbo.transport.server.rpc.codec.ResponseEncoder;

public class NettyRpcChannelInitializer extends ChannelInitializer<SocketChannel> {

	private final ServerInvokerFactory invokerFactory;
	private final Serializer serializer;
	private final CopyOnWriteArrayList<RpcServerFilter> filters;

	public NettyRpcChannelInitializer(ServerInvokerFactory invokerFactory, Serializer serializer,
			CopyOnWriteArrayList<RpcServerFilter> filters) {
		this.invokerFactory = invokerFactory;
		this.serializer = serializer;
		this.filters = filters;

	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ch.pipeline()//
				.addLast("encoder", new ResponseEncoder(serializer))//
				.addLast("decoder", new RequestDecoder(TurboConstants.MAX_FRAME_LENGTH, serializer))//
				.addLast("handler", new NettyRpcServerHandler(invokerFactory, filters));
	}
}
