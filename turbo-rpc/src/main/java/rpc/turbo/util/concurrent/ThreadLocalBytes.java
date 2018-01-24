package rpc.turbo.util.concurrent;

import java.util.function.Supplier;

import rpc.turbo.config.TurboConstants;

/**
 * very carefull
 * 
 * @author Hank
 *
 */
public final class ThreadLocalBytes {
	private static final int ATTACHMENT_INDEX = AttachmentThreadUtils.nextVarIndex();
	private static final Supplier<byte[]> supplier = () -> new byte[TurboConstants.MAX_FRAME_LENGTH];

	public static byte[] current() {
		return AttachmentThreadUtils.getOrUpdate(ATTACHMENT_INDEX, supplier);
	}
}
