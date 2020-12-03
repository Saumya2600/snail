package com.acgist.snail.format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.acgist.snail.utils.ArrayUtils;
import com.acgist.snail.utils.StringUtils;

/**
 * <p>JSON处理工具</p>
 * 
 * @author acgist
 */
public final class JSON {

//	private static final Logger LOGGER = LoggerFactory.getLogger(JSON.class);
	
	/**
	 * <p>JSON特殊字符</p>
	 * <p>Chrome浏览器控制台执行以下代码获取特殊字符：</p>
	 * <pre>
	 * var array = {};
	 * for (var i = 0, value = '', array = []; i < 0xFFFF; i++) {
	 *  	if(i >= 0xD800 && i <= 0xDFFF) {
	 *  		continue;
	 *  	}
	 * 		value = JSON.stringify(String.fromCharCode(i));
	 * 		value.indexOf("\\") > -1 && array.push(value);
	 * }
	 * console.log(array.join(", "));
	 * </pre>
	 * <p>其他特殊字符（不处理）：D800~DFFF</p>
	 */
	private static final char[] CHARS = new char[] {
		'\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005',
		'\u0006', '\u0007', '\b', '\t', '\n', '\u000b', '\f', '\r',
		'\u000e', '\u000f', '\u0010', '\u0011', '\u0012', '\u0013',
		'\u0014', '\u0015', '\u0016', '\u0017', '\u0018', '\u0019',
		'\u001a', '\u001b', '\u001c', '\u001d', '\u001e', '\u001f',
		'\"', '\\'
	};
	/**
	 * <p>特殊字符对应编码</p>
	 */
	private static final String[] CHARS_ENCODE = new String[] {
		"\\u0000", "\\u0001", "\\u0002", "\\u0003", "\\u0004", "\\u0005",
		"\\u0006", "\\u0007", "\\b", "\\t", "\\n", "\\u000b", "\\f", "\\r",
		"\\u000e", "\\u000f", "\\u0010", "\\u0011", "\\u0012", "\\u0013",
		"\\u0014", "\\u0015", "\\u0016", "\\u0017", "\\u0018", "\\u0019",
		"\\u001a", "\\u001b", "\\u001c", "\\u001d", "\\u001e", "\\u001f",
		"\\\"", "\\\\"
	};
	/**
	 * <p>{@link Map}前缀：{@value}</p>
	 */
	private static final char JSON_MAP_PREFIX = '{';
	/**
	 * <p>{@link Map}后缀：{@value}</p>
	 */
	private static final char JSON_MAP_SUFFIX = '}';
	/**
	 * <p>{@link List}前缀：{@value}</p>
	 */
	private static final char JSON_LIST_PREFIX = '[';
	/**
	 * <p>{@link List}后缀：{@value}</p>
	 */
	private static final char JSON_LIST_SUFFIX = ']';
	/**
	 * <p>键值分隔符：{@value}</p>
	 */
	private static final char JSON_KV_SEPARATOR = ':';
	/**
	 * <p>属性分隔符：{@value}</p>
	 */
	private static final char JSON_ATTR_SEPARATOR = ',';
	/**
	 * <p>字符串：{@value}</p>
	 */
	private static final char JSON_STRING = '"';
	/**
	 * <p>{@code null}：{@value}</p>
	 */
	private static final String JSON_NULL = "null";
	/**
	 * <p>boolean类型：{@value}</p>
	 */
	private static final String JSON_BOOLEAN_TRUE = "true";
	/**
	 * <p>boolean类型：{@value}</p>
	 */
	private static final String JSON_BOOLEAN_FALSE = "false";
	
	/**
	 * <p>JSON数据类型</p>
	 * 
	 * @author acgist
	 */
	public enum Type {

		/** map */
		MAP,
		/** list */
		LIST;
		
	}
	
	/**
	 * <p>类型</p>
	 */
	private Type type;
	/**
	 * <p>List</p>
	 */
	private List<Object> list;
	/**
	 * <p>Map</p>
	 */
	private Map<Object, Object> map;
	/**
	 * <p>是否使用懒加载</p>
	 * <p>懒加载：反序列化JSON时，懒加载不会立即解析所有的JSON对象。</p>
	 */
	private static boolean lazy = true;
	
	/**
	 * <p>禁止创建实例</p>
	 */
	private JSON() {
	}
	
	/**
	 * <p>使用Map生成JSON对象</p>
	 * 
	 * @param map {@link Map}
	 * 
	 * @return JSON对象
	 */
	public static final JSON ofMap(Map<Object, Object> map) {
		final JSON json = new JSON();
		json.map = map;
		json.type = Type.MAP;
		return json;
	}
	
	/**
	 * <p>使用List生成JSON对象</p>
	 * 
	 * @param list {@link List}
	 * 
	 * @return JSON对象
	 */
	public static final JSON ofList(List<Object> list) {
		final JSON json = new JSON();
		json.list = list;
		json.type = Type.LIST;
		return json;
	}
	
	/**
	 * <p>将字符串转为为JSON对象</p>
	 * 
	 * @param content 字符串
	 * 
	 * @return JSON对象
	 */
	public static final JSON ofString(String content) {
		if(StringUtils.isEmpty(content)) {
			throw new IllegalArgumentException("JSON格式错误：" + content);
		}
		content = content.trim();
		if(content.isEmpty()) {
			throw new IllegalArgumentException("JSON格式错误：" + content);
		}
		final JSON json = new JSON();
		final char prefix = content.charAt(0);
		final char suffix = content.charAt(content.length() - 1);
		if(prefix == JSON_MAP_PREFIX && suffix == JSON_MAP_SUFFIX) {
			json.type = Type.MAP;
		} else if(prefix == JSON_LIST_PREFIX && suffix == JSON_LIST_SUFFIX) {
			json.type = Type.LIST;
		} else {
			throw new IllegalArgumentException("JSON格式错误：" + content);
		}
		content = content.substring(1, content.length() - 1); // 去掉首位字符
		json.deserialize(content);
		return json;
	}
	
	/**
	 * <p>使用懒加载</p>
	 */
	public static final void lazy() {
		JSON.lazy = true;
	}
	
	/**
	 * <p>禁用懒加载</p>
	 */
	public static final void eager() {
		JSON.lazy = false;
	}
	
	/**
	 * <p>序列化JSON对象</p>
	 * 
	 * @return JSON字符串
	 */
	private String serialize() {
		final StringBuilder builder = new StringBuilder();
		if(this.type == Type.MAP) {
			serializeMap(this.map, builder);
		} else if(this.type == Type.LIST) {
			serializeList(this.list, builder);
		} else {
			throw new IllegalArgumentException("JSON类型错误：" + this.type);
		}
		return builder.toString();
	}

	/**
	 * <p>序列化Map</p>
	 * 
	 * @param map Map
	 * @param builder JSON字符串Builder
	 */
	private static final void serializeMap(Map<?, ?> map, StringBuilder builder) {
		Objects.requireNonNull(map, "JSON序列化错误（Map为空）");
		builder.append(JSON_MAP_PREFIX);
		if(!map.isEmpty()) {
			map.entrySet().forEach(entry -> {
				serializeValue(entry.getKey(), builder);
				builder.append(JSON_KV_SEPARATOR);
				serializeValue(entry.getValue(), builder);
				builder.append(JSON_ATTR_SEPARATOR);
			});
			builder.setLength(builder.length() - 1);
		}
		builder.append(JSON_MAP_SUFFIX);
	}
	
	/**
	 * <p>序列化List</p>
	 * 
	 * @param list List
	 * @param builder JSON字符串Builder
	 */
	private static final void serializeList(List<?> list, StringBuilder builder) {
		Objects.requireNonNull(list, "JSON序列化错误（List为空）");
		builder.append(JSON_LIST_PREFIX);
		if(!list.isEmpty()) {
			list.forEach(value -> {
				serializeValue(value, builder);
				builder.append(JSON_ATTR_SEPARATOR);
			});
			builder.setLength(builder.length() - 1);
		}
		builder.append(JSON_LIST_SUFFIX);
	}
	
	/**
	 * <p>序列化Java对象</p>
	 * 
	 * @param object Java对象
	 * @param builder JSON字符串Builder
	 */
	private static final void serializeValue(Object object, StringBuilder builder) {
		if(object == null) {
			builder.append(JSON_NULL);
		} else if(object instanceof String) {
			builder
				.append(JSON_STRING)
				.append(encodeValue((String) object))
				.append(JSON_STRING);
		} else if(object instanceof Boolean) {
			builder.append(object.toString());
		} else if(object instanceof Number) {
			builder.append(object.toString());
		} else if(object instanceof JSON) {
			builder.append(object.toString());
		} else if(object instanceof Map) {
			serializeMap((Map<?, ?>) object, builder);
		} else if(object instanceof List) {
			serializeList((List<?>) object, builder);
		} else {
			builder
				.append(JSON_STRING)
				.append(encodeValue(object.toString()))
				.append(JSON_STRING);
		}
	}
	
	/**
	 * <p>转义JSON字符串</p>
	 * 
	 * @param content 待转义字符串
	 * 
	 * @return 转义后字符串
	 */
	private static final String encodeValue(String content) {
		int index = -1;
		final char[] chars = content.toCharArray();
		final StringBuilder builder = new StringBuilder();
		for (char value : chars) {
			index = ArrayUtils.indexOf(CHARS, value);
			if(index == ArrayUtils.NONE_INDEX) {
				builder.append(value);
			} else {
				builder.append(CHARS_ENCODE[index]);
			}
		}
		return builder.toString();
	}
	
	/**
	 * <p>反序列化JSON字符串</p>
	 * 
	 * @param content JSON字符串
	 */
	private void deserialize(String content) {
		if(this.type == Type.MAP) {
			this.map = new LinkedHashMap<>();
			deserializeMap(content, this.map);
		} else if(this.type == Type.LIST) {
			this.list = new ArrayList<>();
			deserializeList(content, this.list);
		} else {
			throw new IllegalArgumentException("JSON类型错误：" + this.type);
		}
	}
	
	/**
	 * <p>反序列化Map</p>
	 * 
	 * @param content JSON字符串
	 * @param map Map
	 */
	private static final void deserializeMap(String content, Map<Object, Object> map) {
		final AtomicInteger index = new AtomicInteger(0);
		while(index.get() < content.length()) {
			map.put(
				deserializeValue(index, content),
				deserializeValue(index, content)
			);
		}
	}
	
	/**
	 * <p>反序列化List</p>
	 * 
	 * @param content JSON字符串
	 * @param list List
	 */
	private static final void deserializeList(String content, List<Object> list) {
		final AtomicInteger index = new AtomicInteger(0);
		while(index.get() < content.length()) {
			list.add(
				deserializeValue(index, content)
			);
		}
	}
	
	/**
	 * <p>反序列化JSON字符串</p>
	 * 
	 * @param index 字符索引
	 * @param content JSON字符串
	 * 
	 * @return Java对象
	 */
	private static final Object deserializeValue(AtomicInteger index, String content) {
		char value;
		String hexValue;
		int jsonIndex = 0; // JSON层级
		int stringIndex = 0; // 字符串层级
		final StringBuilder builder = new StringBuilder();
		do {
			value = content.charAt(index.get());
			if(value == JSON_STRING) {
				if(stringIndex == 0) {
					stringIndex++; // 层级增加
				} else {
					stringIndex--; // 层级减少
				}
			} else if(value == JSON_MAP_PREFIX || value == JSON_LIST_PREFIX) {
				jsonIndex++;
			} else if(value == JSON_MAP_SUFFIX || value == JSON_LIST_SUFFIX) {
				jsonIndex--;
			}
			// 不属于JSON对象和字符串对象出现分隔符：结束循环
			if(stringIndex == 0 && jsonIndex == 0 && (value == JSON_KV_SEPARATOR || value == JSON_ATTR_SEPARATOR)) {
				index.incrementAndGet();
				break;
			}
			// 转义参考：#CHARS
			if (value == '\\') {
				value = content.charAt(index.incrementAndGet());
				switch (value) {
				case 'b':
					builder.append('\b');
					break;
				case 't':
					builder.append('\t');
					break;
				case 'n':
					builder.append('\n');
					break;
				case 'f':
					builder.append('\f');
					break;
				case 'r':
					builder.append('\r');
					break;
				case '"':
					// 如果存在JSON对象里面保留转义字符
					if(jsonIndex != 0) {
						builder.append('\\');
					}
					builder.append(value);
					break;
				case '\\':
					// 如果存在JSON对象里面保留转义字符
					if(jsonIndex != 0) {
						builder.append('\\');
					}
					builder.append(value);
					break;
				case 'u': // Unicode
					hexValue = content.substring(index.get() + 1, index.get() + 5);
					builder.append((char) Integer.parseInt(hexValue, 16));
					index.addAndGet(4);
					break;
				default:
					// 未知转义类型保留转义字符
					builder.append('\\');
					builder.append(value);
					break;
				}
			} else {
				builder.append(value);
			}
		} while (index.incrementAndGet() < content.length());
		return decodeValue(builder.toString());
	}
	
	/**
	 * <p>类型转换</p>
	 * 
	 * @param content JSON字符串
	 * 
	 * @return Java对象
	 */
	private static final Object decodeValue(String content) {
		final String value = content.trim();
		final int length = value.length();
		if(JSON_NULL.equals(value)) {
			// null
			return null;
		} else if(StringUtils.isDecimal(value)) {
			// 数字
			return Integer.valueOf(value);
		} else if(
			JSON_BOOLEAN_TRUE.equals(value) ||
			JSON_BOOLEAN_FALSE.equals(value)
		) {
			// Boolean
			return Boolean.valueOf(value);
		} else if(
			length > 1 &&
			value.charAt(0) == JSON_STRING &&
			value.charAt(value.length() - 1) == JSON_STRING
		) {
			// 字符串
			return value.substring(1, length - 1); // 去掉引号
		} else if(
			length > 1 &&
			value.charAt(0) == JSON_MAP_PREFIX &&
			value.charAt(length - 1) == JSON_MAP_SUFFIX
		) {
			if(JSON.lazy) {
				return value;
			} else {
				return JSON.ofString(value);
			}
		} else if(
			length > 1 &&
			value.charAt(0) == JSON_LIST_PREFIX &&
			value.charAt(length - 1) == JSON_LIST_SUFFIX
		) {
			if(JSON.lazy) {
				return value;
			} else {
				return JSON.ofString(value);
			}
		} else {
			throw new IllegalArgumentException("JSON格式错误：" + value);
		}
	}
	
	/**
	 * <p>获取Map</p>
	 * 
	 * @return Map
	 */
	public Map<Object, Object> getMap() {
		return this.map;
	}
	
	/**
	 * <p>获取List</p>
	 * 
	 * @return List
	 */
	public List<Object> getList() {
		return this.list;
	}
	
	/**
	 * <p>获取JSON对象</p>
	 * <p>如果对象是JSON对象直接返回，如果是字符串转为JSON对象。</p>
	 * 
	 * @param key 属性名称
	 * 
	 * @return JSON对象
	 */
	public JSON getJSON(Object key) {
		final Object value = this.get(key);
		if(value == null) {
			return null;
		} else if(value instanceof JSON) {
			return (JSON) value;
		} else if(value instanceof String) {
			return JSON.ofString((String) value);
		} else if(value instanceof Map) {
			final Map<Object, Object> map = ((Map<?, ?>) value).entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
			return JSON.ofMap(map);
		} else if(value instanceof List) {
			final List<Object> list = ((List<?>) value).stream()
				.collect(Collectors.toList());
			return JSON.ofList(list);
		} else {
			throw new IllegalArgumentException("JSON转换错误：" + value);
		}
	}
	
	/**
	 * <p>获取Integer属性</p>
	 * 
	 * @param key 属性名称
	 * 
	 * @return Integer
	 */
	public Integer getInteger(Object key) {
		return (Integer) this.get(key);
	}

	/**
	 * <p>获取Boolean属性</p>
	 * 
	 * @param key 属性名称
	 * 
	 * @return Boolean
	 */
	public Boolean getBoolean(Object key) {
		return (Boolean) this.get(key);
	}
	
	/**
	 * <p>获取String属性</p>
	 * 
	 * @param key 属性名称
	 * 
	 * @return String
	 */
	public String getString(Object key) {
		return (String) this.get(key);
	}
	
	/**
	 * <p>获取属性</p>
	 * 
	 * @param key 属性名称
	 * 
	 * @return 属性对象
	 */
	public Object get(Object key) {
		return this.map.get(key);
	}
	
	/**
	 * @return JSON字符串
	 */
	public String toJSON() {
		return this.serialize();
	}
	
	@Override
	public String toString() {
		return this.serialize();
	}
	
}
