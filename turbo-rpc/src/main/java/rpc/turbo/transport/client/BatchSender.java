package rpc.turbo.transport.client;

import java.util.Objects;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.shaded.org.jctools.queues.atomic.MpscGrowableAtomicArrayQueue;
import rpc.turbo.transport.client.future.RequestWithFuture;
import rpc.turbo.util.FastClearableArrayList;

public class BatchSender {
	public static final int MAX_SEND_BUFFER_SIZE = 16;
	public static final int MAX_SEND_LOOP_COUNT = 16;

	private volatile Channel channel;

	private final MpscGrowableAtomicArrayQueue<RequestWithFuture> queue //
			= new MpscGrowableAtomicArrayQueue<>(8, 4 * MAX_SEND_BUFFER_SIZE);
	private final FastClearableArrayList<RequestWithFuture> sendBuffer //
			= new FastClearableArrayList<>();

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public void send(RequestWithFuture request) {
		Objects.requireNonNull(request, "request is null");

		while (!queue.offer(request)) {
			// 已经满了，必须要清理
			channel.eventLoop().execute(doBatchSend);
		}

		if (!queue.isEmpty()) {
			channel.eventLoop().execute(doBatchSend);
		}
	}

	private final Runnable doBatchSend = () -> {

		if (queue.isEmpty()) {
			return;
		}

		final Channel finalChannel = channel;
		final ChannelPromise voidPromise = finalChannel.voidPromise();

		for (int r = 0; r < MAX_SEND_LOOP_COUNT; r++) {
			for (int i = 0; i < MAX_SEND_BUFFER_SIZE; i++) {
				RequestWithFuture request = queue.poll();

				if (request != null) {
					sendBuffer.add(request);
				} else {
					break;
				}
			}

			if (!sendBuffer.isEmpty()) {
				finalChannel.write(sendBuffer, voidPromise);
			}

			sendBuffer.clear();

			if (queue.isEmpty()) {
				break;
			}
		}

		finalChannel.flush();
	};

}
