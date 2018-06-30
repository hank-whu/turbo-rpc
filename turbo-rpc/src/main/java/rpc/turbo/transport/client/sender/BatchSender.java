package rpc.turbo.transport.client.sender;

import java.io.IOException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.internal.shaded.org.jctools.queues.atomic.MpscGrowableAtomicArrayQueue;
import rpc.turbo.transport.client.future.RequestWithFuture;
import rpc.turbo.util.FastClearableArrayList;

public class BatchSender implements Sender {
	public static final int MAX_SEND_BUFFER_SIZE = 16;
	public static final int MAX_SEND_LOOP_COUNT = 16;

	private final Channel channel;
	private final ChannelPromise voidPromise;
	private final EventLoop eventLoop;

	private final MpscGrowableAtomicArrayQueue<RequestWithFuture> queue //
			= new MpscGrowableAtomicArrayQueue<>(8, 4 * MAX_SEND_BUFFER_SIZE);
	private final FastClearableArrayList<RequestWithFuture> sendBuffer //
			= new FastClearableArrayList<>();

	private final Runnable batchSendTask = () -> doBatchSend();

	public BatchSender(Channel channel) {
		this.channel = channel;
		this.voidPromise = channel.voidPromise();
		this.eventLoop = channel.eventLoop();
	}

	@Override
	public void send(RequestWithFuture request) {
		while (!queue.offer(request)) {
			// 已经满了，必须要清理
			eventLoop.execute(batchSendTask);
		}

		if (!queue.isEmpty()) {
			eventLoop.execute(batchSendTask);
		}
	}

	private void doBatchSend() {
		if (queue.isEmpty()) {
			return;
		}

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
				channel.write(sendBuffer, voidPromise);
			}

			sendBuffer.clear();

			if (queue.isEmpty()) {
				break;
			}
		}

		channel.flush();
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

}
