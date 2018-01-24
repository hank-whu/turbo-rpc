package rpc.turbo.util.concurrent;

import static rpc.turbo.util.UnsafeUtils.unsafe;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 高性能，仅适用于key值比较少的情况，不能超过512k个
 * 
 * @author Hank
 *
 */
public class ConcurrentIntToObjectArrayMap<T> {
	private static final Object NOT_FOUND = new Object();
	private static final int MAXIMUM_CAPACITY = 1024 * 512;

	private static final int ABASE;
	private static final int ASHIFT;

	private volatile Object[] array;

	public ConcurrentIntToObjectArrayMap() {
		this(16);
	}

	public ConcurrentIntToObjectArrayMap(int initialCapacity) {
		if (initialCapacity < 2) {
			throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
		}

		if (initialCapacity > MAXIMUM_CAPACITY) {
			throw new IndexOutOfBoundsException("Illegal initial capacity: " + initialCapacity);
		}

		ensureCapacity(initialCapacity);
	}

	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean contains(int key) {
		if (key < 0) {
			return false;
		}

		Object[] finalArray = array;
		if (key >= finalArray.length) {
			return false;
		}

		Object value = unsafe().getObjectVolatile(finalArray, offset(key));

		if (value == NOT_FOUND) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 
	 * @param key
	 * @return null为找不到
	 */
	@SuppressWarnings("unchecked")
	public T get(int key) {
		if (key < 0) {
			throw new IllegalArgumentException("Illegal key: " + key);
		}

		Object[] finalArray = array;
		if (key >= finalArray.length) {
			return null;
		}

		Object value = unsafe().getObjectVolatile(finalArray, offset(key));

		if (value != NOT_FOUND) {
			return (T) value;
		} else {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public T getOrUpdate(int key, Supplier<T> producer) {
		if (key < 0) {
			throw new IllegalArgumentException("Illegal key: " + key);
		}

		Object value;
		final Object[] finalArray = array;

		if (key >= finalArray.length) {
			value = NOT_FOUND;
		} else {
			value = unsafe().getObjectVolatile(finalArray, offset(key));
		}

		if (value != NOT_FOUND) {
			return (T) value;
		}

		synchronized (this) {
			final Object[] theArray = array;
			if (key < theArray.length) {
				value = unsafe().getObjectVolatile(theArray, offset(key));
			}

			if (value != NOT_FOUND) {
				return (T) value;
			}

			value = producer.get();
			put(key, (T) value);
		}

		return (T) value;
	}

	/**
	 * 
	 * @param key
	 *            大于零，小于256k
	 * 
	 * @param value
	 *            不为null
	 */
	public void put(int key, T value) {
		if (key < 0) {
			throw new IllegalArgumentException("Illegal key: " + key);
		}

		if (key >= MAXIMUM_CAPACITY) {
			throw new IndexOutOfBoundsException("Illegal key: " + key);
		}

		if (value == null) {
			throw new IllegalArgumentException("Illegal value: " + value);
		}

		ensureCapacity(key + 1);

		unsafe().putOrderedObject(array, offset(key), value);
	}

	public void clear() {
		if (array == null) {
			return;
		}

		Object[] objs = new Object[16];
		Arrays.fill(objs, NOT_FOUND);

		array = objs;
	}

	private void ensureCapacity(int capacity) {
		Object[] theArray = array;
		if (theArray != null && theArray.length >= capacity) {
			return;
		}

		synchronized (this) {
			Object[] finalArray = array;
			if (finalArray != null && finalArray.length >= capacity) {
				return;
			}

			int newCapacity = tableSizeFor(capacity);

			if (newCapacity > MAXIMUM_CAPACITY) {
				throw new IndexOutOfBoundsException(newCapacity);
			}

			Object[] objs = new Object[newCapacity];
			Arrays.fill(objs, NOT_FOUND);

			if (finalArray != null) {
				System.arraycopy(finalArray, 0, objs, 0, finalArray.length);
			}

			array = objs;
		}
	}

	private static final long offset(int key) {
		return ((long) key << ASHIFT) + ABASE;
	}

	private static final int tableSizeFor(int cap) {
		int n = cap - 1;
		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;
		return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
	}

	static {
		try {
			ABASE = unsafe().arrayBaseOffset(Object[].class);

			int scale = unsafe().arrayIndexScale(Object[].class);
			if ((scale & (scale - 1)) != 0) {
				throw new Error("array index scale not a power of two");
			}

			ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	public static void main(String[] args) {
		ConcurrentIntToObjectArrayMap<Integer> map = new ConcurrentIntToObjectArrayMap<>();
		map.put(16, 16);

		for (int i = 0; i < 1024; i++) {
			map.put(i, i);
		}

		for (int i = 0; i < 1024; i++) {
			System.out.println(i + ":" + map.get(i));
		}
	}

}
