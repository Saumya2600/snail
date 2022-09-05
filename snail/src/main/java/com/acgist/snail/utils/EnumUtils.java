package com.acgist.snail.utils;

import java.lang.reflect.Array;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 枚举工具
 * 
 * @author acgist
 */
public final class EnumUtils {

	private EnumUtils() {
	}
	
	/**
	 * 创建枚举索引数组
	 * 注意：只有频繁使用枚举转义同时枚举索引和枚举数量差距不大时使用
	 * 
	 * @param <T> 枚举泛型
	 * 
	 * @param clazz 枚举类型
	 * @param mapper 索引函数
	 * 
	 * @return 索引数组
	 * 
	 * TODO：类型安全
	 */
	@SuppressWarnings("unchecked")
	public static final <T> T[] index(Class<T> clazz, Function<T, Byte> mapper) {
		if(clazz == null || !clazz.isEnum()) {
			throw new IllegalArgumentException("必须输入枚举类型");
		}
		final T[] array = clazz.getEnumConstants();
		final int length = Stream.of(array).map(mapper::apply).max(Byte::compare).get() + 1;
		final T[] index = (T[]) Array.newInstance(clazz, length);
		for (final T value : array) {
			index[mapper.apply(value)] = value;
		}
		return index;
	}
	
}
