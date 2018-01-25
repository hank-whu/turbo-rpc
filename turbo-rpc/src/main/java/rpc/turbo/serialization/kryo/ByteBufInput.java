package rpc.turbo.serialization.kryo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import io.netty.buffer.ByteBuf;
import rpc.turbo.serialization.SerializationConstants;

public class ByteBufInput extends Input {
	private static final int MAX_SAFE_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	protected ByteBuf byteBuf;

	/** Creates a new Input for reading from a ByteBuf. */
	public ByteBufInput(ByteBuf byteBuf) {
		setBuffer(byteBuf);
	}

	/**
	 * Sets a new byteBuf, discarding any previous byteBuf. The byte order,
	 * position, limit and capacity are set to match the specified byteBuf. The
	 * total is reset. The {@link #setInputStream(InputStream) InputStream} is set
	 * to null.
	 */
	public void setBuffer(ByteBuf byteBuf) {
		if (byteBuf == null) {
			return;
		}

		this.byteBuf = byteBuf;
		position = byteBuf.readerIndex();
		limit = byteBuf.capacity();
		capacity = byteBuf.capacity();
		total = 0;
		inputStream = null;
	}

	/**
	 * Releases a direct byteBuf. {@link #setBuffer(ByteBuf)} must be called before
	 * any write operations can be performed.
	 */
	public void release() {
		close();
		byteBuf.release();
		byteBuf = null;
	}

	public ByteBuf getByteBuf() {
		return byteBuf;
	}

	public void rewind() {
		super.rewind();
		byteBuf.readerIndex(0);
	}

	/**
	 * Fills the byteBuf with more bytes. Can be overridden to fill the bytes from a
	 * source other than the InputStream.
	 */
	protected int fill(ByteBuf byteBuf, int offset, int count) throws KryoException {
		return -1;
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

	/**
	 * @param required
	 *            Must be &gt; 0. The byteBuf is filled until it has at least this many
	 *            bytes.
	 * @return the number of bytes remaining.
	 * @throws KryoException
	 *             if EOS is reached before required bytes are read (byteBuf
	 *             underflow).
	 */
	final protected int require(int required) throws KryoException {
		int remaining = limit - position;

		if (required > capacity) {
			throw new KryoException("Buffer too small: capacity: " + capacity + ", required: " + required);
		}

		return remaining;
	}

	/**
	 * @param optional
	 *            Try to fill the byteBuf with this many bytes.
	 * @return the number of bytes remaining, but not more than optional, or -1 if
	 *         the EOS was reached and the byteBuf is empty.
	 */
	private int optional(int optional) throws KryoException {
		int remaining = limit - position;

		if (remaining >= optional)
			return optional;

		optional = Math.min(optional, capacity);

		return remaining == 0 ? -1 : Math.min(remaining, optional);
	}

	// InputStream

	/**
	 * Reads a single byte as an int from 0 to 255, or -1 if there are no more bytes
	 * are available.
	 */
	public int read() throws KryoException {
		if (optional(1) <= 0)
			return -1;
		byteBuf.readerIndex(position);
		position++;
		return byteBuf.readByte() & 0xFF;
	}

	/**
	 * Reads bytes.length bytes or less and writes them to the specified byte[],
	 * starting at 0, and returns the number of bytes read.
	 */
	public int read(byte[] bytes) throws KryoException {
		byteBuf.readerIndex(position);
		return read(bytes, 0, bytes.length);
	}

	/**
	 * Reads count bytes or less and writes them to the specified byte[], starting
	 * at offset, and returns the number of bytes read or -1 if no more bytes are
	 * available.
	 */
	public int read(byte[] bytes, int offset, int count) throws KryoException {
		byteBuf.readerIndex(position);
		if (bytes == null)
			throw new IllegalArgumentException("bytes cannot be null.");
		int startingCount = count;
		int copyCount = Math.min(limit - position, count);
		while (true) {
			byteBuf.readBytes(bytes, offset, copyCount);
			position += copyCount;
			count -= copyCount;
			if (count == 0)
				break;
			offset += copyCount;
			copyCount = optional(count);
			if (copyCount == -1) {
				// End of data.
				if (startingCount == count)
					return -1;
				break;
			}
			if (position == limit)
				break;
		}
		return startingCount - count;
	}

	public void setPosition(int position) {
		this.position = position;
		this.byteBuf.readerIndex(position);
	}

	/** Sets the limit in the byteBuf. */
	public void setLimit(int limit) {
		this.limit = limit;
	}

	public void skip(int count) throws KryoException {
		super.skip(count);
		byteBuf.readerIndex(this.position());
	}

	/** Discards the specified number of bytes. */
	public long skip(long count) throws KryoException {
		long remaining = count;
		while (remaining > 0) {
			int skip = (int) Math.min(MAX_SAFE_ARRAY_SIZE, remaining);
			skip(skip);
			remaining -= skip;
		}
		return count;
	}

	/** Closes the underlying InputStream, if any. */
	public void close() throws KryoException {
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException ignored) {
			}
		}
	}

	/** Closes the underlying InputStream, if any. */
	public void clear() throws KryoException {
		byteBuf = null;
	}

	// byte

	/** Reads a single byte. */
	public byte readByte() throws KryoException {
		byteBuf.readerIndex(position);
		// require(1);
		position++;
		return byteBuf.readByte();
	}

	/** Reads a byte as an int from 0 to 255. */
	public int readByteUnsigned() throws KryoException {
		// byteBuf.readerIndex(position);
		// require(1);
		position++;
		return byteBuf.readByte() & 0xFF;
	}

	/** Reads the specified number of bytes into a new byte[]. */
	public byte[] readBytes(int length) throws KryoException {
		byte[] bytes = new byte[length];
		readBytes(bytes, 0, length);
		return bytes;
	}

	/**
	 * Reads bytes.length bytes and writes them to the specified byte[], starting at
	 * index 0.
	 */
	public void readBytes(byte[] bytes) throws KryoException {
		readBytes(bytes, 0, bytes.length);
	}

	/**
	 * Reads count bytes and writes them to the specified byte[], starting at
	 * offset.
	 */
	public void readBytes(byte[] bytes, int offset, int count) throws KryoException {
		if (bytes == null)
			throw new IllegalArgumentException("bytes cannot be null.");
		int copyCount = Math.min(limit - position, count);
		while (true) {
			byteBuf.readBytes(bytes, offset, copyCount);
			position += copyCount;
			count -= copyCount;
			if (count == 0)
				break;
			offset += copyCount;
			copyCount = Math.min(count, capacity);
			// require(copyCount);
		}
	}

	public int readInt() throws KryoException {
		// require(4);
		position += 4;
		return byteBuf.readInt();
	}

	public int readInt(boolean optimizePositive) throws KryoException {
		return readVarInt(optimizePositive);
	}

	public int readVarInt(boolean optimizePositive) throws KryoException {
		byteBuf.readerIndex(position);

		position++;
		int b = byteBuf.readByte();
		int result = b & 0x7F;
		if ((b & 0x80) != 0) {
			position++;
			b = byteBuf.readByte();
			result |= (b & 0x7F) << 7;
			if ((b & 0x80) != 0) {
				position++;
				b = byteBuf.readByte();
				result |= (b & 0x7F) << 14;
				if ((b & 0x80) != 0) {
					position++;
					b = byteBuf.readByte();
					result |= (b & 0x7F) << 21;
					if ((b & 0x80) != 0) {
						position++;
						b = byteBuf.readByte();
						result |= (b & 0x7F) << 28;
					}
				}
			}
		}
		return optimizePositive ? result : ((result >>> 1) ^ -(result & 1));
	}

	/**
	 * Returns true if enough bytes are available to read an int with
	 * {@link #readInt(boolean)}.
	 */
	public boolean canReadInt() throws KryoException {
		if (limit - position >= 5)
			return true;
		if (optional(5) <= 0)
			return false;
		int p = position;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		return true;
	}

	/**
	 * Returns true if enough bytes are available to read a long with
	 * {@link #readLong(boolean)}.
	 */
	public boolean canReadLong() throws KryoException {
		if (limit - position >= 9)
			return true;
		if (optional(5) <= 0)
			return false;
		int p = position;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		if ((byteBuf.getByte(p++) & 0x80) == 0)
			return true;
		if (p == limit)
			return false;
		return true;
	}

	public String readStringFast() {
		// byteBuf.readerIndex(position);

		byte typ = readByte();

		switch (typ) {
		case SerializationConstants.STRING_NULL:
			return null;

		case SerializationConstants.STRING_EMPTY:
			return "";

		case SerializationConstants.STRING_LATIN1: {
			int count = readVarInt(true);

			ensureSize(count);
			byte[] bytes = this.buffer;
			readBytes(bytes, 0, count);

			return new String(bytes, 0, count, StandardCharsets.ISO_8859_1);
		}

		case SerializationConstants.STRING_UTF8: {
			int count = readVarInt(true);

			ensureSize(count);
			byte[] bytes = this.buffer;
			readBytes(bytes, 0, count);

			return new String(bytes, 0, count, StandardCharsets.UTF_8);
		}

		default:
			break;
		}

		return null;
	}

	/**
	 * Reads the length and string of UTF8 characters, or null. This can read
	 * strings written by {@link Output#writeString(String)} ,
	 * {@link Output#writeString(CharSequence)}, and
	 * {@link Output#writeAscii(String)}.
	 * 
	 * @return May be null.
	 */
	public String readString() {
		if (SerializationConstants.INCOMPATIBLE_FAST_STRING_FORMAT) {
			return readStringFast();
		}

		byteBuf.readerIndex(position);
		int available = require(1);
		position++;
		int b = byteBuf.readByte();
		if ((b & 0x80) == 0)
			return readAscii(); // ASCII.
		// Null, empty, or UTF8.
		int charCount = available >= 5 ? readUtf8Length(b) : readUtf8Length_slow(b);
		switch (charCount) {
		case 0:
			return null;
		case 1:
			return "";
		}
		charCount--;
		if (chars.length < charCount)
			chars = new char[charCount];
		readUtf8(charCount);
		return new String(chars, 0, charCount);
	}

	private int readUtf8Length(int b) {
		int result = b & 0x3F; // Mask all but first 6 bits.
		if ((b & 0x40) != 0) { // Bit 7 means another byte, bit 8 means UTF8.
			position++;
			b = byteBuf.readByte();
			result |= (b & 0x7F) << 6;
			if ((b & 0x80) != 0) {
				position++;
				b = byteBuf.readByte();
				result |= (b & 0x7F) << 13;
				if ((b & 0x80) != 0) {
					position++;
					b = byteBuf.readByte();
					result |= (b & 0x7F) << 20;
					if ((b & 0x80) != 0) {
						position++;
						b = byteBuf.readByte();
						result |= (b & 0x7F) << 27;
					}
				}
			}
		}
		return result;
	}

	private int readUtf8Length_slow(int b) {
		int result = b & 0x3F; // Mask all but first 6 bits.
		if ((b & 0x40) != 0) { // Bit 7 means another byte, bit 8 means UTF8.
			// require(1);
			position++;
			b = byteBuf.readByte();
			result |= (b & 0x7F) << 6;
			if ((b & 0x80) != 0) {
				// require(1);
				position++;
				b = byteBuf.readByte();
				result |= (b & 0x7F) << 13;
				if ((b & 0x80) != 0) {
					// require(1);
					position++;
					b = byteBuf.readByte();
					result |= (b & 0x7F) << 20;
					if ((b & 0x80) != 0) {
						// require(1);
						position++;
						b = byteBuf.readByte();
						result |= (b & 0x7F) << 27;
					}
				}
			}
		}
		return result;
	}

	private void readUtf8(int charCount) {
		char[] chars = this.chars;
		// Try to read 7 bit ASCII chars.
		int charIndex = 0;
		int count = Math.min(require(1), charCount);
		int position = this.position;
		int b;
		while (charIndex < count) {
			position++;
			b = byteBuf.readByte();
			if (b < 0) {
				position--;
				break;
			}
			chars[charIndex++] = (char) b;
		}
		this.position = position;
		// If byteBuf didn't hold all chars or any were not ASCII, use slow path for
		// remainder.
		if (charIndex < charCount) {
			byteBuf.readerIndex(position);
			readUtf8_slow(charCount, charIndex);
		}
	}

	private void readUtf8_slow(int charCount, int charIndex) {
		char[] chars = this.chars;
		while (charIndex < charCount) {
			if (position == limit)
				require(1);
			position++;
			int b = byteBuf.readByte() & 0xFF;
			switch (b >> 4) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				chars[charIndex] = (char) b;
				break;
			case 12:
			case 13:
				if (position == limit)
					require(1);
				position++;
				chars[charIndex] = (char) ((b & 0x1F) << 6 | byteBuf.readByte() & 0x3F);
				break;
			case 14:
				// require(2);
				position += 2;
				int b2 = byteBuf.readByte();
				int b3 = byteBuf.readByte();
				chars[charIndex] = (char) ((b & 0x0F) << 12 | (b2 & 0x3F) << 6 | b3 & 0x3F);
				break;
			}
			charIndex++;
		}
	}

	private String readAscii() {
		int end = position;
		int start = end - 1;
		int limit = this.limit;
		int b;

		do {
			if (end == limit)
				return readAscii_slow();
			end++;
			b = byteBuf.readByte();
		} while ((b & 0x80) == 0);

		byteBuf.setByte(end - 1, (byte) (byteBuf.getByte(end - 1) & 0x7F)); // Mask end of ascii bit.

		ensureSize(end - start);
		byte[] tmp = this.buffer;// new byte[end - start];
		byteBuf.readerIndex(start);
		byteBuf.readBytes(tmp, 0, end - start);
		@SuppressWarnings("deprecation")
		String value = new String(tmp, 0, 0, end - start);

		byteBuf.setByte(end - 1, (byte) (byteBuf.getByte(end - 1) | 0x80));
		position = end;
		byteBuf.readerIndex(position);
		return value;
	}

	private String readAscii_slow() {
		position--; // Re-read the first byte.
		// Copy chars currently in byteBuf.
		int charCount = limit - position;
		if (charCount > chars.length)
			chars = new char[charCount * 2];
		char[] chars = this.chars;
		for (int i = position, ii = 0, n = limit; i < n; i++, ii++)
			chars[ii] = (char) byteBuf.getByte(i);
		position = limit;
		// Copy additional chars one by one.
		while (true) {
			// require(1);
			position++;
			int b = byteBuf.readByte();
			if (charCount == chars.length) {
				char[] newChars = new char[charCount * 2];
				System.arraycopy(chars, 0, newChars, 0, charCount);
				chars = newChars;
				this.chars = newChars;
			}
			if ((b & 0x80) == 0x80) {
				chars[charCount++] = (char) (b & 0x7F);
				break;
			}
			chars[charCount++] = (char) b;
		}
		return new String(chars, 0, charCount);
	}

	/**
	 * Reads the length and string of UTF8 characters, or null. This can read
	 * strings written by {@link Output#writeString(String)} ,
	 * {@link Output#writeString(CharSequence)}, and
	 * {@link Output#writeAscii(String)}.
	 * 
	 * @return May be null.
	 */
	public StringBuilder readStringBuilder() {
		byteBuf.readerIndex(position);
		int available = require(1);
		position++;
		int b = byteBuf.readByte();
		if ((b & 0x80) == 0)
			return new StringBuilder(readAscii()); // ASCII.
		// Null, empty, or UTF8.
		int charCount = available >= 5 ? readUtf8Length(b) : readUtf8Length_slow(b);
		switch (charCount) {
		case 0:
			return null;
		case 1:
			return new StringBuilder("");
		}
		charCount--;
		if (chars.length < charCount)
			chars = new char[charCount];
		readUtf8(charCount);
		StringBuilder builder = new StringBuilder(charCount);
		builder.append(chars, 0, charCount);
		return builder;
	}

	/** Reads a 4 byte float. */
	public float readFloat() throws KryoException {
		// require(4);
		position += 4;
		return byteBuf.readFloat();
	}

	/** Reads a 1-5 byte float with reduced precision. */
	public float readFloat(float precision, boolean optimizePositive) throws KryoException {
		return readInt(optimizePositive) / (float) precision;
	}

	/** Reads a 2 byte short. */
	public short readShort() throws KryoException {
		// require(2);
		position += 2;
		return byteBuf.readShort();
	}

	/** Reads a 2 byte short as an int from 0 to 65535. */
	public int readShortUnsigned() throws KryoException {
		// require(2);
		position += 2;
		return byteBuf.readShort();
	}

	/** Reads an 8 byte long. */
	public long readLong() throws KryoException {
		// require(8);
		position += 8;
		return byteBuf.readLong();
	}

	/** {@inheritDoc} */
	public long readLong(boolean optimizePositive) throws KryoException {
		return readVarLong(optimizePositive);
	}

	/** {@inheritDoc} */
	public long readVarLong(boolean optimizePositive) throws KryoException {
		byteBuf.readerIndex(position);
		if (require(1) < 9)
			return readLong_slow(optimizePositive);
		position++;
		int b = byteBuf.readByte();
		long result = b & 0x7F;
		if ((b & 0x80) != 0) {
			position++;
			b = byteBuf.readByte();
			result |= (b & 0x7F) << 7;
			if ((b & 0x80) != 0) {
				position++;
				b = byteBuf.readByte();
				result |= (b & 0x7F) << 14;
				if ((b & 0x80) != 0) {
					position++;
					b = byteBuf.readByte();
					result |= (b & 0x7F) << 21;
					if ((b & 0x80) != 0) {
						position++;
						b = byteBuf.readByte();
						result |= (long) (b & 0x7F) << 28;
						if ((b & 0x80) != 0) {
							position++;
							b = byteBuf.readByte();
							result |= (long) (b & 0x7F) << 35;
							if ((b & 0x80) != 0) {
								position++;
								b = byteBuf.readByte();
								result |= (long) (b & 0x7F) << 42;
								if ((b & 0x80) != 0) {
									position++;
									b = byteBuf.readByte();
									result |= (long) (b & 0x7F) << 49;
									if ((b & 0x80) != 0) {
										position++;
										b = byteBuf.readByte();
										result |= (long) b << 56;
									}
								}
							}
						}
					}
				}
			}
		}
		if (!optimizePositive)
			result = (result >>> 1) ^ -(result & 1);
		return result;
	}

	private long readLong_slow(boolean optimizePositive) {
		// The byteBuf is guaranteed to have at least 1 byte.
		position++;
		int b = byteBuf.readByte();
		long result = b & 0x7F;
		if ((b & 0x80) != 0) {
			// require(1);
			position++;
			b = byteBuf.readByte();
			result |= (b & 0x7F) << 7;
			if ((b & 0x80) != 0) {
				// require(1);
				position++;
				b = byteBuf.readByte();
				result |= (b & 0x7F) << 14;
				if ((b & 0x80) != 0) {
					// require(1);
					position++;
					b = byteBuf.readByte();
					result |= (b & 0x7F) << 21;
					if ((b & 0x80) != 0) {
						// require(1);
						position++;
						b = byteBuf.readByte();
						result |= (long) (b & 0x7F) << 28;
						if ((b & 0x80) != 0) {
							// require(1);
							position++;
							b = byteBuf.readByte();
							result |= (long) (b & 0x7F) << 35;
							if ((b & 0x80) != 0) {
								// require(1);
								position++;
								b = byteBuf.readByte();
								result |= (long) (b & 0x7F) << 42;
								if ((b & 0x80) != 0) {
									// require(1);
									position++;
									b = byteBuf.readByte();
									result |= (long) (b & 0x7F) << 49;
									if ((b & 0x80) != 0) {
										// require(1);
										position++;
										b = byteBuf.readByte();
										result |= (long) b << 56;
									}
								}
							}
						}
					}
				}
			}
		}
		if (!optimizePositive)
			result = (result >>> 1) ^ -(result & 1);
		return result;
	}

	/** Reads a 1 byte boolean. */
	public boolean readBoolean() throws KryoException {
		// require(1);
		position++;
		return byteBuf.readByte() == 1 ? true : false;
	}

	/** Reads a 2 byte char. */
	public char readChar() throws KryoException {
		// require(2);
		position += 2;
		return byteBuf.readChar();
	}

	/** Reads an 8 bytes double. */
	public double readDouble() throws KryoException {
		// require(8);
		position += 8;
		return byteBuf.readDouble();
	}

	/** Reads a 1-9 byte double with reduced precision. */
	public double readDouble(double precision, boolean optimizePositive) throws KryoException {
		return readLong(optimizePositive) / (double) precision;
	}
}
