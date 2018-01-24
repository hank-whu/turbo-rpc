package rpc.turbo.transport.client.codec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import rpc.turbo.protocol.Request;
import rpc.turbo.serialization.Serializer;

public class RequestEncoder extends MessageToByteEncoder<Request> {
	private static final Log logger = LogFactory.getLog(RequestEncoder.class);

	private final Serializer serializer;

	public RequestEncoder(Serializer serializer) {
		this.serializer = serializer;
	}

	protected void encode(ChannelHandlerContext ctx, Request request, ByteBuf buffer) throws Exception {
		serializer.writeRequest(buffer, request);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

		if (logger.isErrorEnabled()) {
			logger.error("Exception caught on " + ctx.channel(), cause);
		}

		ctx.channel().close();
	}
}
