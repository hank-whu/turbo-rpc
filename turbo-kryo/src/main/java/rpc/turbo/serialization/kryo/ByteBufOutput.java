package rpc.turbo.serialization.kryo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import io.netty.buffer.ByteBuf;
import rpc.turbo.serialization.SerializationConstants;
import rpc.turbo.util.UnsafeStringUtils;

public class ByteBufOutput extends Output {
	private static final int MAX_SAFE_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	protected ByteBuf byteBuf;

	/** Creates a new Output for writing to a ByteBuf. */
	public ByteBufOutput(ByteBuf buffer) {
		setBuffer(buffer);
	}

	/**
	 * Release a direct buffer. {@link #setBuffer(ByteBuf, int)} should be called
	 * before next write operations can be called.
	 * 
	 * NOTE: If Cleaner is not accessible due to SecurityManager restrictions,
	 * reflection could be used to obtain the "clean" method and then invoke it.
	 */
	public void release() {
		clear();
		byteBuf.release();
		byteBuf = null;
	}

	/**
	 * Sets the buffer that will be written to. maxCapacity is set to the specified
	 * buffer's capacity.
	 * 
	 * @see #setBuffer(ByteBuf, int)
	 */
	public void setBuffer(ByteBuf buffer) {
		if (buffer == null) {
			return;
		}

		setBuffer(buffer, buffer.capacity());
	}

	/**
	 * Sets the buffer that will be written to. The byte order, position and
	 * capacity are set to match the specified buffer. The total is set to 0. The
	 * {@link #setOutputStream(OutputStream) OutputStream} is set to null.
	 * 
	 * @param maxBufferSize
	 *            The buffer is doubled as needed until it exceeds maxCapacity and
	 *            an exception is thrown.
	 */
	public void setBuffer(ByteBuf buffer, int maxBufferSize) {
		if (buffer == null) {
			return;
		}

		if (maxBufferSize < -1) {
			throw new IllegalArgumentException("maxBufferSize cannot be < -1: " + maxBufferSize);
		}

		this.byteBuf = buffer;
		this.maxCapacity = maxBufferSize == -1 ? MAX_SAFE_ARRAY_SIZE : maxBufferSize;
		capacity = buffer.capacity();
		position = buffer.writerIndex();
		total = 0;
		outputStream = null;
	}

	/**
	 * Returns the buffer. The bytes between zero and {@link #position()} are the
	 * data that has been written.
	 */
	public ByteBuf getByteBuffer() {
		byteBuf.writerIndex(position);
		return byteBuf;
	}

	/**
	 * Returns a new byte array containing the bytes currently in the buffer between
	 * zero and {@link #position()}.
	 */
	public byte[] toBytes() {
		byte[] newBuffer = new byte[position];
		byteBuf.writerIndex(0);
		byteBuf.readBytes(newBuffer, 0, position);
		return newBuffer;
	}

	/** Sets the current position in the buffer. */
	public void setPosition(int position) {
		this.position = position;
		this.byteBuf.writerIndex(position);
	}

	/** Sets the position and total to zero. */
	public void clear() {
		byteBuf = null;
		position = 0;
		total = 0;
	}

	private void ensureSize(int length) {
		if (buffer != null && buffer.length >= length) {
			return;
		}

		buffer = new byte[alignSize(length)];
	}

	private static final int alignSize(int cap) {
		int n = cap - 1;

		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;

		return (n < 0) ? 1 : n + 1;
	}

	/** @return true if the buffer has been resized. */
	protected boolean require(int required) throws KryoException {
		if (capacity - position >= required) {
			return false;
		} else {
			byteBuf.capacity(position + required + 4);
			capacity = byteBuf.capacity();
			return true;
		}
	}

	// OutputStream

	/** Writes the buffered bytes to the underlying OutputStream, if any. */
	public void flush() throws KryoException {
		total += position;
		position = 0;
	}

	/**
	 * Flushes any buffered bytes and closes the underlying OutputStream, if any.
	 */
	public void close() throws KryoException {
		flush();

		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (IOException ignored) {
			}
		}
	}

	/** Writes a byte. */
	public void write(int value) throws KryoException {
		if (position == capacity) {
			require(1);
		}

		byteBuf.writeByte((byte) value);
		position++;
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void write(byte[] bytes) throws KryoException {
		if (bytes == null) {
			throw new IllegalArgumentException("bytes cannot be null.");
		}

		writeBytes(bytes, 0, bytes.length);
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void write(byte[] bytes, int offset, int length) throws KryoException {
		writeBytes(bytes, offset, length);
	}

	// byte

	public void writeByte(byte value) throws KryoException {
		byteBuf.writeByte(value);
		position++;
	}

	public void writeByte(int value) throws KryoException {
		byteBuf.writeByte((byte) value);
		position++;
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void writeBytes(byte[] bytes) throws KryoException {
		if (bytes == null) {
			throw new IllegalArgumentException("bytes cannot be null.");
		}

		writeBytes(bytes, 0, bytes.length);
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void writeBytes(byte[] bytes, int offset, int count) throws KryoException {
		if (bytes == null) {
			throw new IllegalArgumentException("bytes cannot be null.");
		}

		int copyCount = Math.min(capacity - position, count);

		while (true) {
			byteBuf.writeBytes(bytes, offset, copyCount);
			position += copyCount;
			count -= copyCount;

			if (count == 0) {
				return;
			}

			offset += copyCount;
			copyCount = Math.min(capacity, count);

			require(copyCount);
		}
	}

	// int

	/** Writes a 4 byte int. */
	public void writeInt(int value) throws KryoException {
		require(4);
		byteBuf.writeInt(value);
		position += 4;
	}

	public int writeInt(int value, boolean optimizePositive) throws KryoException {
		return writeIntS(value, optimizePositive);
	}

	public int writeVarInt(int val, boolean optimizePositive) throws KryoException {
		return writeIntS(val, optimizePositive);
	}

	private void writeStringFast(String value) {
		byteBuf.writerIndex(position);

		if (value == null) {
			writeByte(SerializationConstants.STRING_NULL);
			return;
		}

		int length = value.length();
		if (length == 0) {
			writeByte(SerializationConstants.STRING_EMPTY);
			return;
		}

		if (UnsafeStringUtils.isLatin1(value)) {
			writeByte(SerializationConstants.STRING_LATIN1);
			byte[] bytes = UnsafeStringUtils.getLatin1Bytes(value);

			writeVarInt(length, true);
			byteBuf.writeBytes(bytes, 0, length);
			position += length;
		} else {
			writeByte(SerializationConstants.STRING_UTF8);
			byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

			writeVarInt(bytes.length, true);
			byteBuf.writeBytes(bytes);
			position += bytes.length;
		}

	}

	// string

	/**
	 * Writes the length and string, or null. Short strings are checked and if ASCII
	 * they are written more efficiently, else they are written as UTF8. If a string
	 * is known to be ASCII, {@link #writeAscii(String)} may be used. The string can
	 * be read using {@link Input#readString()} or
	 * {@link Input#readStringBuilder()}.
	 * 
	 * @param value
	 *            May be null.
	 */
	@SuppressWarnings("deprecation")
	public void writeString(String value) throws KryoException {
		if (SerializationConstants.INCOMPATIBLE_FAST_STRING_FORMAT) {
			writeStringFast(value);
			return;
		}

		byteBuf.writerIndex(position);
		if (value == null) {
			writeByte(0x80); // 0 means null, bit 8 means UTF8.
			return;
		}
		int charCount = value.length();
		if (charCount == 0) {
			writeByte(1 | 0x80); // 1 means empty string, bit 8 means UTF8.
			return;
		}
		// Detect ASCII.
		boolean ascii = false;
		if (charCount > 1 && charCount < 64) {
			ascii = true;
			for (int i = 0; i < charCount; i++) {
				int c = value.charAt(i);
				if (c > 127) {
					ascii = false;
					break;
				}
			}
		}
		if (ascii) {
			if (capacity - position < charCount)
				writeAscii_slow(value, charCount);
			else {
				ensureSize(charCount);
				byte[] tmp = this.buffer;
				value.getBytes(0, charCount, tmp, 0);

				// byte[] tmp = value.getBytes();

				byteBuf.writeBytes(tmp, 0, charCount);

				position += charCount;
			}

			byteBuf.setByte(position - 1, (byte) (byteBuf.getByte(position - 1) | 0x80));
		} else {
			writeUtf8Length(charCount + 1);

			int charIndex = 0;

			if (capacity - position >= charCount) {
				// Try to write 8 bit chars.
				int position = this.position;
				for (; charIndex < charCount; charIndex++) {
					int c = value.charAt(charIndex);

					if (c > 127) {
						break;
					}

					byteBuf.setByte(position++, (byte) c);
				}

				this.position = position;
				byteBuf.writerIndex(position);
			}
			if (charIndex < charCount) {
				writeString_slow(value, charCount, charIndex);
			}

			byteBuf.writerIndex(position);
		}
	}

	/**
	 * Writes the length and CharSequence as UTF8, or null. The string can be read
	 * using {@link Input#readString()} or {@link Input#readStringBuilder()}.
	 * 
	 * @param value
	 *            May be null.
	 */
	public void writeString(CharSequence value) throws KryoException {
		if (value instanceof String) {
			writeString(value.toString());
			return;
		}

		if (SerializationConstants.INCOMPATIBLE_FAST_STRING_FORMAT) {
			writeStringFast(value.toString());
			return;
		}

		if (value == null) {
			writeByte(0x80); // 0 means null, bit 8 means UTF8.
			return;
		}
		int charCount = value.length();
		if (charCount == 0) {
			writeByte(1 | 0x80); // 1 means empty string, bit 8 means UTF8.
			return;
		}
		writeUtf8Length(charCount + 1);
		int charIndex = 0;
		if (capacity - position >= charCount) {
			// Try to write 8 bit chars.
			int position = this.position;
			for (; charIndex < charCount; charIndex++) {
				int c = value.charAt(charIndex);
				if (c > 127)
					break;
				byteBuf.setByte(position++, (byte) c);
			}
			this.position = position;
			byteBuf.writerIndex(position);
		}
		if (charIndex < charCount)
			writeString_slow(value, charCount, charIndex);
		byteBuf.writerIndex(position);
	}

	/**
	 * Writes a string that is known to contain only ASCII characters. Non-ASCII
	 * strings passed to this method will be corrupted. Each byte is a 7 bit
	 * character with the remaining byte denoting if another character is available.
	 * This is slightly more efficient than {@link #writeString(String)}. The string
	 * can be read using {@link Input#readString()} or
	 * {@link Input#readStringBuilder()}.
	 * 
	 * @param value
	 *            May be null.
	 */
	@SuppressWarnings("deprecation")
	public void writeAscii(String value) throws KryoException {
		if (value == null) {
			writeByte(0x80); // 0 means null, bit 8 means UTF8.
			return;
		}
		int charCount = value.length();
		if (charCount == 0) {
			writeByte(1 | 0x80); // 1 means empty string, bit 8 means UTF8.
			return;
		}
		if (capacity - position < charCount)
			writeAscii_slow(value, charCount);
		else {
			ensureSize(charCount);
			byte[] tmp = this.buffer;
			// java9 中更高效
			value.getBytes(0, charCount, tmp, 0);

			// byte[] tmp = value.getBytes();

			byteBuf.writeBytes(tmp, 0, charCount);
			position += charCount;
		}

		byteBuf.setByte(position - 1, (byte) (byteBuf.getByte(position - 1) | 0x80)); // Bit 8 means end of ASCII.
	}

	/**
	 * Writes the length of a string, which is a variable length encoded int except
	 * the first byte uses bit 8 to denote UTF8 and bit 7 to denote if another byte
	 * is present.
	 */
	private void writeUtf8Length(int value) {
		if (value >>> 6 == 0) {
			require(1);
			byteBuf.writeByte((byte) (value | 0x80)); // Set bit 8.
			position += 1;
		} else if (value >>> 13 == 0) {
			require(2);
			byteBuf.writeByte((byte) (value | 0x40 | 0x80)); // Set bit 7 and 8.
			byteBuf.writeByte((byte) (value >>> 6));
			position += 2;
		} else if (value >>> 20 == 0) {
			require(3);
			byteBuf.writeByte((byte) (value | 0x40 | 0x80)); // Set bit 7 and 8.
			byteBuf.writeByte((byte) ((value >>> 6) | 0x80)); // Set bit 8.
			byteBuf.writeByte((byte) (value >>> 13));
			position += 3;
		} else if (value >>> 27 == 0) {
			require(4);
			byteBuf.writeByte((byte) (value | 0x40 | 0x80)); // Set bit 7 and 8.
			byteBuf.writeByte((byte) ((value >>> 6) | 0x80)); // Set bit 8.
			byteBuf.writeByte((byte) ((value >>> 13) | 0x80)); // Set bit 8.
			byteBuf.writeByte((byte) (value >>> 20));
			position += 4;
		} else {
			require(5);
			byteBuf.writeByte((byte) (value | 0x40 | 0x80)); // Set bit 7 and 8.
			byteBuf.writeByte((byte) ((value >>> 6) | 0x80)); // Set bit 8.
			byteBuf.writeByte((byte) ((value >>> 13) | 0x80)); // Set bit 8.
			byteBuf.writeByte((byte) ((value >>> 20) | 0x80)); // Set bit 8.
			byteBuf.writeByte((byte) (value >>> 27));
			position += 5;
		}
	}

	private void writeString_slow(CharSequence value, int charCount, int charIndex) {
		for (; charIndex < charCount; charIndex++) {
			if (position == capacity) {
				require(Math.min(capacity, charCount - charIndex));
			}

			int c = value.charAt(charIndex);
			if (c <= 0x007F) {
				byteBuf.setByte(position++, (byte) c);
			} else if (c > 0x07FF) {
				byteBuf.setByte(position++, (byte) (0xE0 | c >> 12 & 0x0F));
				require(2);
				byteBuf.setByte(position++, (byte) (0x80 | c >> 6 & 0x3F));
				byteBuf.setByte(position++, (byte) (0x80 | c & 0x3F));
			} else {
				byteBuf.setByte(position++, (byte) (0xC0 | c >> 6 & 0x1F));
				require(1);
				byteBuf.setByte(position++, (byte) (0x80 | c & 0x3F));
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void writeAscii_slow(String value, int charCount) throws KryoException {
		ByteBuf buffer = this.byteBuf;
		int charIndex = 0;
		int charsToWrite = Math.min(charCount, capacity - position);
		while (charIndex < charCount) {
			ensureSize(charCount);
			byte[] tmp = this.buffer;// new byte[charCount];
			value.getBytes(charIndex, charIndex + charsToWrite, tmp, 0);
			buffer.writeBytes(tmp, 0, charsToWrite);
			// value.getBytes(charIndex, charIndex + charsToWrite, buffer, position);
			charIndex += charsToWrite;
			position += charsToWrite;
			charsToWrite = Math.min(charCount - charIndex, capacity);
		}
	}

	// float

	/** Writes a 4 byte float. */
	public void writeFloat(float value) throws KryoException {
		require(4);
		byteBuf.writeFloat(value);
		position += 4;
	}

	/**
	 * Writes a 1-5 byte float with reduced precision.
	 * 
	 * @param optimizePositive
	 *            If true, small positive numbers will be more efficient (1 byte)
	 *            and small negative numbers will be inefficient (5 bytes).
	 */
	public int writeFloat(float value, float precision, boolean optimizePositive) throws KryoException {
		return writeInt((int) (value * precision), optimizePositive);
	}

	// short

	/** Writes a 2 byte short. */
	public void writeShort(int value) throws KryoException {
		require(2);
		byteBuf.writeShort((short) value);
		position += 2;
	}

	// long

	/** Writes an 8 byte long. */
	public void writeLong(long value) throws KryoException {
		require(8);
		byteBuf.writeLong(value);
		position += 8;
	}

	public int writeLong(long value, boolean optimizePositive) throws KryoException {
		return writeLongS(value, optimizePositive);
	}

	public int writeVarLong(long value, boolean optimizePositive) throws KryoException {
		return writeLongS(value, optimizePositive);
	}

	/**
	 * Writes a 1-9 byte long.
	 * 
	 * @param optimizePositive
	 *            If true, small positive numbers will be more efficient (1 byte)
	 *            and small negative numbers will be inefficient (9 bytes).
	 */
	public int writeLongS(long value, boolean optimizePositive) throws KryoException {
		if (!optimizePositive)
			value = (value << 1) ^ (value >> 63);
		if (value >>> 7 == 0) {
			require(1);
			byteBuf.writeByte((byte) value);
			position += 1;
			return 1;
		}
		if (value >>> 14 == 0) {
			require(2);
			int intValue = (int) value;
			int newValue = (((intValue & 0x7F) | 0x80) << 8) | (intValue >>> 7);
			byteBuf.writeShort(newValue);
			position += 2;
			return 2;
		}
		if (value >>> 21 == 0) {
			require(3);
			int intValue = (int) value;
			int newValue = (((intValue & 0x7F) | 0x80) << 8) | (intValue >>> 7 & 0xFF | 0x80);
			byteBuf.writeShort(newValue);
			byteBuf.writeByte((byte) (intValue >>> 14));
			position += 3;
			return 3;
		}
		if (value >>> 28 == 0) {
			require(4);
			int intValue = (int) value;
			int newValue = (((intValue & 0x7F) | 0x80) << 24) //
					| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
					| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
					| (intValue >>> 21);

			byteBuf.writeInt(newValue);
			position += 4;
			return 4;
		}
		if (value >>> 35 == 0) {
			require(5);
			int intValue = (int) value;
			int newValue = (((intValue & 0x7F) | 0x80) << 24) //
					| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
					| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
					| (intValue >>> 21 & 0xFF | 0x80);

			byteBuf.writeInt(newValue);
			byteBuf.writeByte((byte) (value >>> 28));
			position += 5;
			return 5;
		}
		if (value >>> 42 == 0) {
			require(6);
			int intValue = (int) value;
			int first = (((intValue & 0x7F) | 0x80) << 24) //
					| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
					| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
					| (intValue >>> 21 & 0xFF | 0x80);

			byteBuf.writeInt(first);

			int second = (int) (((value >>> 28 & 0xFF | 0x80) << 8) //
					| (value >>> 35));

			byteBuf.writeShort(second);
			position += 6;
			return 6;
		}
		if (value >>> 49 == 0) {
			require(7);
			int intValue = (int) value;
			int first = (((intValue & 0x7F) | 0x80) << 24) //
					| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
					| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
					| (intValue >>> 21 & 0xFF | 0x80);

			byteBuf.writeInt(first);

			int second = (int) (((value >>> 28 & 0xFF | 0x80) << 8) //
					| (value >>> 35 & 0xFF | 0x80));

			byteBuf.writeShort(second);
			byteBuf.writeByte((byte) (value >>> 42));
			position += 7;
			return 7;
		}
		if (value >>> 56 == 0) {
			require(8);
			int intValue = (int) value;
			int first = (((intValue & 0x7F) | 0x80) << 24) //
					| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
					| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
					| (intValue >>> 21 & 0xFF | 0x80);

			byteBuf.writeInt(first);

			intValue = (int) (value >>> 28);
			int second = (((intValue & 0x7F) | 0x80) << 24) //
					| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
					| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
					| (intValue >>> 21);

			byteBuf.writeInt(second);
			position += 8;
			return 8;
		}
		require(9);
		int intValue = (int) value;
		int first = (((intValue & 0x7F) | 0x80) << 24) //
				| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
				| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
				| (intValue >>> 21 & 0xFF | 0x80);

		byteBuf.writeInt(first);

		intValue = (int) (value >>> 28);
		int second = (((intValue & 0x7F) | 0x80) << 24) //
				| ((intValue >>> 7 & 0xFF | 0x80) << 16) //
				| ((intValue >>> 14 & 0xFF | 0x80) << 8) //
				| (intValue >>> 21 & 0xFF | 0x80);

		byteBuf.writeInt(second);
		byteBuf.writeByte((byte) (value >>> 56));
		position += 9;
		return 9;
	}

	public int writeIntS(int value, boolean optimizePositive) throws KryoException {
		if (!optimizePositive)
			value = (value << 1) ^ (value >> 31);

		if (value >>> 7 == 0) {
			require(1);
			byteBuf.writeByte((byte) value);
			position += 1;
			return 1;
		}

		if (value >>> 14 == 0) {
			require(2);

			int newValue = (((value & 0x7F) | 0x80) << 8) | (value >>> 7);
			byteBuf.writeShort(newValue);

			position += 2;
			return 2;
		}

		if (value >>> 21 == 0) {
			require(3);

			int newValue = (((value & 0x7F) | 0x80) << 8) | (value >>> 7 & 0xFF | 0x80);
			byteBuf.writeShort(newValue);
			byteBuf.writeByte((byte) (value >>> 14));

			position += 3;
			return 3;
		}

		if (value >>> 28 == 0) {
			require(4);

			int newValue = (((value & 0x7F) | 0x80) << 24) //
					| ((value >>> 7 & 0xFF | 0x80) << 16) //
					| ((value >>> 14 & 0xFF | 0x80) << 8) //
					| (value >>> 21);

			byteBuf.writeInt(newValue);

			position += 4;
			return 4;
		}

		require(5);
		int newValue = (((value & 0x7F) | 0x80) << 24) //
				| ((value >>> 7 & 0xFF | 0x80) << 16) //
				| ((value >>> 14 & 0xFF | 0x80) << 8) //
				| (value >>> 21 & 0xFF | 0x80);

		byteBuf.writeInt(newValue);
		byteBuf.writeByte((byte) (value >>> 28));

		position += 5;
		return 5;
	}

	// boolean

	/** Writes a 1 byte boolean. */
	public void writeBoolean(boolean value) throws KryoException {
		require(1);
		byteBuf.writeByte((byte) (value ? 1 : 0));
		position++;
	}

	// char

	/** Writes a 2 byte char. */
	public void writeChar(char value) throws KryoException {
		require(2);
		byteBuf.writeChar(value);
		position += 2;
	}

	// double

	/** Writes an 8 byte double. */
	public void writeDouble(double value) throws KryoException {
		require(8);
		byteBuf.writeDouble(value);
		position += 8;
	}

	/**
	 * Writes a 1-9 byte double with reduced precision.
	 * 
	 * @param optimizePositive
	 *            If true, small positive numbers will be more efficient (1 byte)
	 *            and small negative numbers will be inefficient (9 bytes).
	 */
	public int writeDouble(double value, double precision, boolean optimizePositive) throws KryoException {
		return writeLong((long) (value * precision), optimizePositive);
	}

}
