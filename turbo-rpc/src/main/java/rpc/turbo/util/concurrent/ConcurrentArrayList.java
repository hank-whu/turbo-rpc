package rpc.turbo.util.concurrent;

import static rpc.turbo.util.UnsafeUtils.unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * only support add get set, not support remove
 * 
 * @author Hank
 *
 * @param <T>
 */
public class ConcurrentArrayList<T> implements RandomAccess {
	public static final int MAXIMUM_CAPACITY = 1 << 30;

	private static final long SIZE_OFFSET;

	private volatile Object[] values;
	private volatile int size;// unsafe atomic operate

	public ConcurrentArrayList() {
		this(16);
	}

	public ConcurrentArrayList(int initialCapacity) {
		if (initialCapacity < 2) {
			throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
		}

		if (initialCapacity > MAXIMUM_CAPACITY) {
			throw new IndexOutOfBoundsException("Illegal initial capacity: " + initialCapacity);
		}

		ensureCapacity(initialCapacity);
	}

	public void add(T value) {
		int index = insertIndex();
		values[index] = value;
	}

	public void addAll(Collection<T> collection) {
		if (collection == null) {
			return;
		}

		ensureCapacity(size + collection.size());

		for (T t : collection) {
			add(t);
		}
	}

	@SuppressWarnings("unchecked")
	public T get(int index) {
		Objects.checkIndex(index, size);
		return (T) values[index];
	}

	public void set(int index, T value) {
		Objects.checkIndex(index, size);
		values[index] = value;
	}

	public int size() {
		return size;
	}

	public void clear() {
		size = 0;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<T> toArrayList() {
		int finalSize = size;
		Object[] finalValues = values;

		ArrayList<T> list = new ArrayList<>(finalSize);

		for (int i = 0; i < finalSize; i++) {
			list.add((T) finalValues[i]);
		}

		return list;
	}

	private int insertIndex() {
		int index = unsafe().getAndAddInt(this, SIZE_OFFSET, 1);
		ensureCapacity(index + 1);

		return index;
	}

	private void ensureCapacity(int capacity) {
		Object[] theArray = values;
		if (theArray != null && theArray.length >= capacity) {
			return;
		}

		synchronized (this) {
			Object[] finalArray = values;
			if (finalArray != null && finalArray.length >= capacity) {
				return;
			}

			int newCapacity = tableSizeFor(capacity);

			if (newCapacity > MAXIMUM_CAPACITY) {
				throw new IndexOutOfBoundsException(newCapacity);
			}

			Object[] objs = new Object[newCapacity];

			if (finalArray != null) {
				System.arraycopy(finalArray, 0, objs, 0, finalArray.length);
			}

			values = objs;
		}
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
			Field field = ConcurrentArrayList.class.getDeclaredField("size");
			SIZE_OFFSET = unsafe().objectFieldOffset(field);
		} catch (Throwable e) {
			throw new Error(e);
		}
	}

	public static void main(String[] args) {
		ConcurrentArrayList<Integer> list = new ConcurrentArrayList<>();

		for (int i = 0; i < 1024; i++) {
			list.add(i);
		}

		for (int i = 0; i < 1024; i++) {
			System.out.println(list.get(i));
		}

	}
}
