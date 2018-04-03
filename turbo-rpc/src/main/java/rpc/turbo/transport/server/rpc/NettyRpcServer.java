package rpc.turbo.transport.server.rpc;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import rpc.turbo.config.HostPort;
import rpc.turbo.filter.RpcServerFilter;
import rpc.turbo.invoke.ServerInvokerFactory;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.transport.server.rpc.handler.NettyRpcChannelInitializer;

public class NettyRpcServer implements Closeable {

	private final HostPort hostPort;
	private final EventLoopGroup eventLoopGroup;
	private final ServerInvokerFactory invokerFactory;
	private final Serializer serializer;
	private final CopyOnWriteArrayList<RpcServerFilter> filters;

	private volatile Channel channel;

	public NettyRpcServer(EventLoopGroup eventLoopGroup, ServerInvokerFactory invokerFactory, Serializer serializer,
			CopyOnWriteArrayList<RpcServerFilter> filters, HostPort hostPort) {
		this.eventLoopGroup = eventLoopGroup;
		this.invokerFactory = invokerFactory;
		this.hostPort = hostPort;
		this.serializer = serializer;
		this.filters = filters;
	}

	public void start() throws InterruptedException {
		InetSocketAddress inet = new InetSocketAddress(hostPort.host, hostPort.port);

		ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.group(eventLoopGroup);

		bootstrap.option(ChannelOption.SO_REUSEADDR, true);

		bootstrap.option(ChannelOption.SO_RCVBUF, 256 * 1024);

		if (eventLoopGroup instanceof EpollEventLoopGroup) {
			bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
			bootstrap.channel(EpollServerSocketChannel.class);
		} else if (eventLoopGroup instanceof NioEventLoopGroup) {
			bootstrap.channel(NioServerSocketChannel.class);
		}

		bootstrap.childHandler(new NettyRpcChannelInitializer(invokerFactory, serializer, filters));

		bootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
		bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

		bootstrap.childOption(ChannelOption.SO_RCVBUF, 256 * 1024);
		bootstrap.childOption(ChannelOption.SO_SNDBUF, 256 * 1024);
		bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, //
				new WriteBufferWaterMark(1024 * 1024, 2048 * 1024));
		bootstrap.childOption(ChannelOption.SO_BACKLOG, 32 * 1024);

		channel = bootstrap.bind(inet).sync().channel();

		System.out.println("TurboRpcServer started. Listening on: " + hostPort);
	}

	@Override
	public void close() throws IOException {
		try {
			channel.closeFuture().sync();
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

}
