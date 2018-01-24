package rpc.turbo.util.concurrent;

import java.util.function.Supplier;

import rpc.turbo.config.TurboConstants;

/**
 * very carefull
 * 
 * @author Hank
 *
 */
public class ThreadLocalStringBuilder {
	private static final int ATTACHMENT_INDEX = AttachmentThreadUtils.nextVarIndex();
	private static final Supplier<StringBuilder> supplier = () -> new StringBuilder();

	public static StringBuilder current() {
		StringBuilder builder = AttachmentThreadUtils.getOrUpdate(ATTACHMENT_INDEX, supplier);

		// 防止内存溢出
		if (builder.capacity() > TurboConstants.MAX_FRAME_LENGTH) {
			builder = new StringBuilder();
			AttachmentThreadUtils.put(ATTACHMENT_INDEX, builder);
		}

		builder.setLength(0);
		return builder;
	}
}
