package rpc.turbo.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class ReflectUtils {

	/**
	 * 获取所有依赖的类，包括字段、方法返回值
	 * 
	 * @param target
	 * @param filter
	 *            过滤器
	 * @return
	 */
	public static Collection<Class<?>> getAllDependClass(Class<?> target, Predicate<Class<?>> filter) {
		Objects.requireNonNull(target, "target is null");

		Set<Class<?>> set = null;

		while (target != Object.class) {
			Field[] fields = target.getDeclaredFields();
			for (int j = 0; j < fields.length; j++) {
				Field field = fields[j];
				Class<?> clazz = field.getType();

				if (filter != null && !filter.test(clazz)) {
					continue;
				}

				if (set == null) {
					set = new HashSet<>();
				}

				set.add(clazz);
			}

			Method[] methods = target.getDeclaredMethods();
			for (int j = 0; j < methods.length; j++) {
				Method method = methods[j];
				Class<?> clazz = method.getReturnType();

				if (filter != null && !filter.test(clazz)) {
					continue;
				}

				if (set == null) {
					set = new HashSet<>();
				}

				set.add(clazz);
			}

			target = target.getSuperclass();
		}

		return set == null ? Set.of() : set;
	}

	public static void main(String[] args) {
		getAllDependClass(ByteBuffer.class, null).forEach(System.out::println);
	}

}
