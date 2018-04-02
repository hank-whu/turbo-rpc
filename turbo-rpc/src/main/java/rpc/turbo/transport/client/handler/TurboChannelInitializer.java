package rpc.turbo.transport.client.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import rpc.turbo.config.TurboConstants;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.transport.client.codec.RequestEncoder;
import rpc.turbo.transport.client.codec.ResponseDecoder;
import rpc.turbo.transport.client.future.FutureContainer;

public class TurboChannelInitializer extends ChannelInitializer<SocketChannel> {

	private final Serializer serializer;

	public TurboChannelInitializer(Serializer serializer) {
		this.serializer = serializer;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		FutureContainer futureContainer = new FutureContainer();

		ch.pipeline()//
				.addLast("encoder", new RequestEncoder(serializer, futureContainer))//
				.addLast("decoder", new ResponseDecoder(TurboConstants.MAX_FRAME_LENGTH, serializer, futureContainer))//
				.addLast("handler", new TurboClientHandler(futureContainer));
	}
}
