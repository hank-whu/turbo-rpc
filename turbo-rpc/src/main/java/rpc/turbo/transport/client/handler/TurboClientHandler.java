package rpc.turbo.transport.client.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import rpc.turbo.protocol.Response;
import rpc.turbo.transport.client.future.FutureContainer;

public class TurboClientHandler extends SimpleChannelInboundHandler<Response> {
	private static final Log logger = LogFactory.getLog(TurboClientHandler.class);

	private final FutureContainer futureContainer;

	public TurboClientHandler(FutureContainer futureContainer) {
		this.futureContainer = futureContainer;
	}

	protected void channelRead0(ChannelHandlerContext ctx, Response response) throws Exception {
		futureContainer.notifyResponse(response);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (logger.isInfoEnabled()) {
			logger.info("channelActive: " + ctx.channel());
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

		if (logger.isErrorEnabled()) {
			logger.error("Exception caught on " + ctx.channel(), cause);
		}

		ctx.channel().close();
	}

}
