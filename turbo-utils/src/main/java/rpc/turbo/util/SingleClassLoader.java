package rpc.turbo.util;

public class SingleClassLoader extends ClassLoader {

	private final Class<?> clazz;

	public SingleClassLoader(ClassLoader parent, byte[] bytes) {
		super(parent);
		this.clazz = defineClass(null, bytes, 0, bytes.length, null);
	}

	@SuppressWarnings("unchecked")
	public <T> Class<T> getClazz() {
		return (Class<T>) clazz;
	}

	public static <T> Class<T> loadClass(byte[] bytes) {
		ClassLoader parent = SingleClassLoader.class.getClassLoader();
		return loadClass(parent, bytes);
	}

	public static <T> Class<T> loadClass(ClassLoader classLoader, byte[] bytes) {
		return new SingleClassLoader(classLoader, bytes).getClazz();
	}
}
