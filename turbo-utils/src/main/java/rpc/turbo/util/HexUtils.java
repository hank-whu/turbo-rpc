package rpc.turbo.util;

import static rpc.turbo.util.UnsafeUtils.unsafe;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * 高性能，空间换时间
 * 
 * @author Hank
 *
 */
public class HexUtils {

	private static final short[] HEX_TABLE = new short[256];
	private static final short[] HEX_TABLE_LE = new short[256];
	private static final short[] UPPER_HEX_TABLE = new short[256];
	private static final short[] UPPER_HEX_TABLE_LE = new short[256];

	private static final byte[] BYTE_TABLE;
	private static final byte[] BYTE_TABLE_LE;

	static {
		final char[] DIGITS = "0123456789abcdef".toCharArray();
		final char[] UPPER_DIGITS = "0123456789ABCDEF".toCharArray();

		for (int i = 0; i < 256; i++) {
			int high = DIGITS[(0xF0 & i) >>> 4];
			int low = DIGITS[0x0F & i];

			HEX_TABLE[i] = (short) (high << 8 | low);
			HEX_TABLE_LE[i] = (short) (low << 8 | high);

			high = UPPER_DIGITS[(0xF0 & i) >>> 4];
			low = UPPER_DIGITS[0x0F & i];

			UPPER_HEX_TABLE[i] = (short) (high << 8 | low);
			UPPER_HEX_TABLE_LE[i] = (short) (low << 8 | high);
		}

		BYTE_TABLE = new byte[HEX_TABLE[255] + 1];
		for (int i = 0; i < 256; i++) {
			BYTE_TABLE[HEX_TABLE[i]] = (byte) i;
			BYTE_TABLE[UPPER_HEX_TABLE[i]] = (byte) i;
		}

		BYTE_TABLE_LE = new byte[HEX_TABLE_LE[255] + 1];
		for (int i = 0; i < 256; i++) {
			BYTE_TABLE_LE[HEX_TABLE_LE[i]] = (byte) i;
			BYTE_TABLE_LE[UPPER_HEX_TABLE_LE[i]] = (byte) i;
		}
	}

	public static short byteToHex(byte b) {
		return HEX_TABLE[b & 0xFF];
	}

	public static short byteToHex(int b) {
		return HEX_TABLE[b & 0xFF];
	}

	public static short byteToHex(long b) {
		return HEX_TABLE[(int) (b & 0xFF)];
	}

	public static short byteToHexLE(byte b) {
		return HEX_TABLE_LE[b & 0xFF];
	}

	public static short byteToHexLE(int b) {
		return HEX_TABLE_LE[b & 0xFF];
	}

	public static short byteToHexLE(long b) {
		return HEX_TABLE_LE[((int) b) & 0xFF];
	}

	public static byte hexToByte(short hex) {
		return BYTE_TABLE[hex];
	}

	public static byte hexToByteLE(short hex) {
		return BYTE_TABLE_LE[hex];
	}

	/**
	 * high performance
	 * 
	 * @param bytes
	 * @return
	 */
	public static String toHex(byte[] bytes) {
		Objects.requireNonNull(bytes, "bytes is null");

		int lentgh = bytes.length;
		if (lentgh == 0) {
			return "";
		}

		byte[] hexBytes = new byte[lentgh << 1];

		for (int i = 0; i < lentgh; i++) {
			byte b = bytes[i];
			unsafe().putShort(hexBytes, ARRAY_BYTE_BASE_OFFSET + i * 2, byteToHexLE(b));
		}

		return UnsafeStringUtils.toLatin1String(hexBytes);
	}

	public static String toHex(int value) {
		byte[] bytes = new byte[8];

		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET, byteToHexLE(value >> 24));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 2, byteToHexLE(value >> 16));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 4, byteToHexLE(value >> 8));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 6, byteToHexLE(value));

		String hex = UnsafeStringUtils.toLatin1String(bytes);

		return hex;
	}

	public static String toHex(long value) {
		byte[] bytes = new byte[16];

		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET, byteToHexLE(value >> 52));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 2, byteToHexLE(value >> 48));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 4, byteToHexLE(value >> 40));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 6, byteToHexLE(value >> 32));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 8, byteToHexLE(value >> 24));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 10, byteToHexLE(value >> 16));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 12, byteToHexLE(value >> 8));
		unsafe().putShort(bytes, ARRAY_BYTE_BASE_OFFSET + 14, byteToHexLE(value));

		String hex = UnsafeStringUtils.toLatin1String(bytes);

		return hex;
	}

	/**
	 * high performance
	 * 
	 * @param byteBuffer
	 * @param offset
	 * @param length
	 * @return
	 */
	public static String toHex(ByteBuffer byteBuffer, int offset, int length) {
		Objects.requireNonNull(byteBuffer, "byteBuffer is null");

		if (byteBuffer.remaining() + byteBuffer.position() - offset < length) {
			throw new IllegalArgumentException("no enough bytes");
		}

		byte[] hexBytes = new byte[length << 1];

		for (int i = 0; i < offset; i++) {
			byte b = byteBuffer.get(offset + i);
			unsafe().putShort(hexBytes, ARRAY_BYTE_BASE_OFFSET + i * 2, byteToHexLE(b));
		}

		return UnsafeStringUtils.toLatin1String(hexBytes);
	}

	public static byte[] fromHex(String hex) {
		int length = hex.length();
		int halfLength = length >> 1;

		if (halfLength << 1 != length) {
			throw new IllegalArgumentException("not hex String");
		}

		byte[] hexBytes = UnsafeStringUtils.getLatin1Bytes(hex);
		byte[] bytes = new byte[length >> 1];

		for (int i = 0; i < bytes.length; i++) {
			short index = unsafe().getShort(hexBytes, ARRAY_BYTE_BASE_OFFSET + i * 2);
			bytes[i] = hexToByteLE(index);
		}

		return bytes;
	}

	public static void main(String[] args) throws UnsupportedEncodingException {

		for (int i = 0; i < 256; i++) {
			short hex = byteToHex((byte) i);
			byte b = hexToByte(hex);

			System.out.print(i);
			System.out.print(" ");
			System.out.print(hex);
			System.out.print(" ");
			System.out.print(b & 0xFF);
			System.out.println();
		}

		String str = "aZ Hello, World, 你好，世界！   +++ ---- %%%% @@@";
		byte[] bytes = str.getBytes("UTF-8");
		String hex = toHex(bytes);

		System.out.println(hex);
		System.out.println(Arrays.toString(bytes));
		System.out.println(Arrays.toString(fromHex(hex)));
	}

}
