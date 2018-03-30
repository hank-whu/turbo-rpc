package rpc.turbo.transport.client.codec;

import static rpc.turbo.config.TurboConstants.EXPIRE_PERIOD;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.transport.client.future.FutureContainer;
import rpc.turbo.transport.client.future.RequestWithFuture;

public class RequestEncoder extends MessageToByteEncoder<RequestWithFuture> {
	private static final Log logger = LogFactory.getLog(RequestEncoder.class);

	private final Serializer serializer;
	private final FutureContainer futureContainer;

	public RequestEncoder(Serializer serializer, FutureContainer futureContainer) {
		this.serializer = serializer;
		this.futureContainer = futureContainer;
	}

	protected void encode(ChannelHandlerContext ctx, RequestWithFuture requestWithFuture, ByteBuf buffer)
			throws Exception {
		futureContainer.add(requestWithFuture);
		serializer.writeRequest(buffer, requestWithFuture.getRequest());

		requestWithFuture.setRequest(null);// help to gc
	}

	@Override
	public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
			ChannelPromise promise) throws Exception {
		super.connect(ctx, remoteAddress, localAddress, promise);

		ctx.executor().scheduleAtFixedRate(//
				() -> futureContainer.doExpireJob(1), //
				EXPIRE_PERIOD, EXPIRE_PERIOD, TimeUnit.MILLISECONDS);
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		super.close(ctx, promise);

		futureContainer.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

		if (logger.isErrorEnabled()) {
			logger.error("Exception caught on " + ctx.channel(), cause);
		}

		ctx.channel().close();

		futureContainer.close();
	}
}
