package rpc.turbo.transport.client.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import rpc.turbo.transport.client.codec.RequestEncoder;
import rpc.turbo.transport.client.codec.ResponseDecoder;
import rpc.turbo.transport.client.future.ResponseFutureContainer;
import rpc.turbo.config.TurboConstants;
import rpc.turbo.serialization.Serializer;

public class TurboChannelInitializer extends ChannelInitializer<SocketChannel> {

	private final ResponseFutureContainer futureContainer;
	private final Serializer serializer;

	public TurboChannelInitializer(ResponseFutureContainer futureContainer, Serializer serializer) {
		this.futureContainer = futureContainer;
		this.serializer = serializer;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ch.pipeline()//
				.addLast("encoder", new RequestEncoder(serializer))//
				.addLast("decoder", new ResponseDecoder(TurboConstants.MAX_FRAME_LENGTH, serializer))//
				.addLast("handler", new TurboClientHandler(futureContainer));
	}
}
