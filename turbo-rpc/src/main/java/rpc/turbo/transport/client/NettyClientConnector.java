package rpc.turbo.transport.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import rpc.turbo.config.HostPort;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.transport.client.future.RequestWithFuture;
import rpc.turbo.transport.client.handler.TurboChannelInitializer;

final class NettyClientConnector implements Closeable {
	private static final Log logger = LogFactory.getLog(NettyClientConnector.class);

	public final HostPort serverAddress;

	private final Serializer serializer;
	private final EventLoopGroup eventLoopGroup;
	private final int connectCount;

	public volatile HostPort clientAddress;
	private volatile Channel[] channels;

	/**
	 * 
	 * @param eventLoopGroup
	 * @param serializer
	 * @param futureContainer
	 * @param serverAddress
	 * @param connectCount
	 */
	NettyClientConnector(EventLoopGroup eventLoopGroup, //
			Serializer serializer, //
			HostPort serverAddress, int connectCount) {
		this.eventLoopGroup = eventLoopGroup;
		this.connectCount = connectCount;
		this.serverAddress = serverAddress;
		this.serializer = serializer;
	}

	int connectCount() {
		return connectCount;
	}

	/**
	 * 
	 * @param channelIndex
	 *            发送数据的channel
	 * 
	 * @param request
	 *            请求数据
	 */
	void send(int channelIndex, RequestWithFuture requestWithFuture) {
		Objects.requireNonNull(requestWithFuture, "request is null");

		Channel channel = channels[channelIndex];
		channel.writeAndFlush(requestWithFuture, channel.voidPromise());
	}

	void connect() throws InterruptedException {

		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(eventLoopGroup);

		// bootstrap.option(ChannelOption.TCP_NODELAY, true);
		bootstrap.option(ChannelOption.SO_REUSEADDR, true);
		bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

		if (eventLoopGroup instanceof EpollEventLoopGroup) {
			bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
			bootstrap.channel(EpollSocketChannel.class);
		} else if (eventLoopGroup instanceof NioEventLoopGroup) {
			bootstrap.channel(NioSocketChannel.class);
		}

		bootstrap.handler(new TurboChannelInitializer(serializer));

		Channel[] newChannels = new Channel[connectCount];
		for (int i = 0; i < connectCount; i++) {
			newChannels[i] = bootstrap.connect(serverAddress.host, serverAddress.port).sync().channel();

			if (logger.isInfoEnabled()) {
				logger.info(serverAddress + " connect " + i + "/" + connectCount);
			}
		}

		InetSocketAddress insocket = (InetSocketAddress) newChannels[0].localAddress();
		clientAddress = new HostPort(insocket.getAddress().getHostAddress(), 0);

		Channel[] old = channels;
		channels = newChannels;

		if (old != null) {
			for (int i = 0; i < old.length; i++) {
				try {
					old[i].close();
				} catch (Exception e) {
					if (logger.isWarnEnabled()) {
						logger.warn("关闭出错", e);
					}
				}
			}
		}
	}

	@Override
	public void close() throws IOException {

		if (channels == null) {
			return;
		}

		for (int i = 0; i < channels.length; i++) {
			try {
				channels[i].close();
			} catch (Exception e) {
				if (logger.isWarnEnabled()) {
					logger.warn("关闭出错", e);
				}
			}
		}

		channels = null;
	}

}
