package rpc.turbo.util.concurrent;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import rpc.turbo.config.TurboConstants;

/**
 * very carefull
 * 
 * @author Hank
 *
 */
public class ThreadLocalHeapByteBuffer {
	private static final int ATTACHMENT_INDEX = AttachmentThreadUtils.nextVarIndex();
	private static final Supplier<ByteBuffer> supplier = () -> ByteBuffer.allocate(TurboConstants.MAX_FRAME_LENGTH);

	public static ByteBuffer current() {
		ByteBuffer byteBuffer = AttachmentThreadUtils.getOrUpdate(ATTACHMENT_INDEX, supplier);

		byteBuffer.clear();
		return byteBuffer;
	}
}
