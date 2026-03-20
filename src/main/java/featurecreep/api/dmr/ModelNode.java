package featurecreep.api.dmr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight "DMR-like" ModelNode with JSON support.
 *
 * Supports: - Object nodes (map) - List nodes (list) - String / Number /
 * Boolean / Null
 *
 * API shape intentionally similar to org.jboss.dmr.ModelNode for your usage:
 * node.get("a").get("b").set(1); node.get("list").add("x"); node.has("k"),
 * node.keys(), asInt/asString/asBoolean/asList ModelNode.fromJSONString(json),
 * ModelNode.fromJSONStream(in) node.toJSONString(false)
 */
public final class ModelNode {

	public enum Type {
		UNDEFINED, OBJECT, LIST, STRING, NUMBER, BOOLEAN, NULL
	}

	private Type type = Type.UNDEFINED;
	private Object value; // Map<String,ModelNode>, List<ModelNode>, String, Number, Boolean, null

	public ModelNode() {
	}

	public Type getType() {
		return type;
	}

	/*
	 * --------------------------- Object navigation / creation
	 * ---------------------------
	 */

	public ModelNode get(String key) {
		ensureObject();
		@SuppressWarnings("unchecked")
		Map<String, ModelNode> m = (Map<String, ModelNode>) value;
		return m.computeIfAbsent(key, k -> new ModelNode());
	}

	public boolean has(String key) {
		if (type != Type.OBJECT)
			return false;
		@SuppressWarnings("unchecked")
		Map<String, ModelNode> m = (Map<String, ModelNode>) value;
		return m.containsKey(key);
	}

	public Set<String> keys() {
		if (type != Type.OBJECT)
			return Collections.emptySet();
		@SuppressWarnings("unchecked")
		Map<String, ModelNode> m = (Map<String, ModelNode>) value;
		return Collections.unmodifiableSet(m.keySet());
	}

	/*
	 * --------------------------- List operations ---------------------------
	 */

	public ModelNode add() {
		ensureList();
		@SuppressWarnings("unchecked")
		List<ModelNode> list = (List<ModelNode>) value;
		ModelNode child = new ModelNode();
		list.add(child);
		return child;
	}

	public ModelNode add(String v) {
		ensureList();
		@SuppressWarnings("unchecked")
		List<ModelNode> list = (List<ModelNode>) value;
		ModelNode child = new ModelNode().set(v);
		list.add(child);
		return this;
	}

	public ModelNode add(int v) {
		ensureList();
		@SuppressWarnings("unchecked")
		List<ModelNode> list = (List<ModelNode>) value;
		ModelNode child = new ModelNode().set(v);
		list.add(child);
		return this;
	}

	public ModelNode add(boolean v) {
		ensureList();
		@SuppressWarnings("unchecked")
		List<ModelNode> list = (List<ModelNode>) value;
		ModelNode child = new ModelNode().set(v);
		list.add(child);
		return this;
	}

	public List<ModelNode> asList() {
		if (type != Type.LIST)
			return Collections.emptyList();
		@SuppressWarnings("unchecked")
		List<ModelNode> list = (List<ModelNode>) value;
		return Collections.unmodifiableList(list);
	}

	/*
	 * --------------------------- Setters ---------------------------
	 */

	public ModelNode set(String v) {
		if (v == null)
			return setNull();
		this.type = Type.STRING;
		this.value = v;
		return this;
	}

	public ModelNode set(int v) {
		this.type = Type.NUMBER;
		this.value = Integer.valueOf(v);
		return this;
	}

	public ModelNode set(long v) {
		this.type = Type.NUMBER;
		this.value = Long.valueOf(v);
		return this;
	}

	public ModelNode set(double v) {
		this.type = Type.NUMBER;
		this.value = Double.valueOf(v);
		return this;
	}

	public ModelNode set(boolean v) {
		this.type = Type.BOOLEAN;
		this.value = Boolean.valueOf(v);
		return this;
	}

	/**
	 * Convenience: set from a Java list (strings/nums/bools/maps/lists/ModelNodes).
	 */
	public ModelNode set(List<?> v) {
		if (v == null)
			return setNull();
		ensureList();
		@SuppressWarnings("unchecked")
		List<ModelNode> list = (List<ModelNode>) value;
		list.clear();
		for (Object o : v)
			list.add(wrap(o));
		return this;
	}

	/**
	 * Convenience: set from a Java map (values can be
	 * primitives/maps/lists/ModelNodes).
	 */
	public ModelNode set(Map<String, ?> v) {
		if (v == null)
			return setNull();
		ensureObject();
		@SuppressWarnings("unchecked")
		Map<String, ModelNode> m = (Map<String, ModelNode>) value;
		m.clear();
		for (Map.Entry<String, ?> e : v.entrySet()) {
			m.put(e.getKey(), wrap(e.getValue()));
		}
		return this;
	}

	public ModelNode setNull() {
		this.type = Type.NULL;
		this.value = null;
		return this;
	}

	/*
	 * --------------------------- Conversions ---------------------------
	 */

	public int asInt() {
		if (type == Type.NUMBER) {
			Number n = (Number) value;
			return n.intValue();
		}
		if (type == Type.STRING) {
			try {
				return Integer.parseInt((String) value);
			} catch (NumberFormatException ignored) {
			}
		}
		if (type == Type.BOOLEAN)
			return ((Boolean) value) ? 1 : 0;
		return 0;
	}

	public long asLong() {
		if (type == Type.NUMBER) {
			Number n = (Number) value;
			return n.longValue();
		}
		if (type == Type.STRING) {
			try {
				return Long.parseLong((String) value);
			} catch (NumberFormatException ignored) {
			}
		}
		if (type == Type.BOOLEAN)
			return ((Boolean) value) ? 1L : 0L;
		return 0L;
	}

	public boolean asBoolean() {
		if (type == Type.BOOLEAN)
			return (Boolean) value;
		if (type == Type.NUMBER)
			return ((Number) value).doubleValue() != 0.0;
		if (type == Type.STRING)
			return Boolean.parseBoolean(((String) value));
		return false;
	}

	public String asString() {
		if (type == Type.STRING)
			return (String) value;
		if (type == Type.NUMBER)
			return String.valueOf(value);
		if (type == Type.BOOLEAN)
			return String.valueOf(value);
		if (type == Type.NULL)
			return "null";
		if (type == Type.OBJECT || type == Type.LIST)
			return toJSONString(false);
		return "";
	}

	/*
	 * --------------------------- JSON I/O ---------------------------
	 */

	public String toJSONString(boolean pretty) {
		StringBuilder sb = new StringBuilder();
		JsonWriter.write(this, sb, pretty ? 0 : -1);
		return sb.toString();
	}

	public static ModelNode fromJSONString(String json) {
		if (json == null)
			return new ModelNode();
		JsonParser p = new JsonParser(json);
		ModelNode n = p.parseValue();
		p.skipWs();
		return n == null ? new ModelNode() : n;
	}

	public static ModelNode fromJSONStream(InputStream in) throws IOException {
		if (in == null)
			return new ModelNode();
		String s = readAllUtf8(in);
		return fromJSONString(s);
	}

	// Convenience alias some codebases use
	public static ModelNode fromJSONStream(SupplierInputStream supplier) throws IOException {
		if (supplier == null)
			return new ModelNode();
		try (InputStream in = supplier.get()) {
			return fromJSONStream(in);
		}
	}

	/*
	 * --------------------------- Internals ---------------------------
	 */

	private void ensureObject() {
		if (type == Type.OBJECT)
			return;
		type = Type.OBJECT;
		value = new LinkedHashMap<String, ModelNode>();
	}

	private void ensureList() {
		if (type == Type.LIST)
			return;
		type = Type.LIST;
		value = new ArrayList<ModelNode>();
	}

	private static ModelNode wrap(Object o) {
		if (o == null)
			return new ModelNode().setNull();
		if (o instanceof ModelNode)
			return (ModelNode) o;

		ModelNode n = new ModelNode();
		if (o instanceof String)
			return n.set((String) o);
		if (o instanceof Integer)
			return n.set(((Integer) o).intValue());
		if (o instanceof Long)
			return n.set(((Long) o).longValue());
		if (o instanceof Short)
			return n.set(((Short) o).intValue());
		if (o instanceof Byte)
			return n.set(((Byte) o).intValue());
		if (o instanceof Double)
			return n.set(((Double) o).doubleValue());
		if (o instanceof Float)
			return n.set(((Float) o).doubleValue());
		if (o instanceof Number)
			return n.set(((Number) o).doubleValue());
		if (o instanceof Boolean)
			return n.set(((Boolean) o).booleanValue());

		if (o instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, ?> m = (Map<String, ?>) o;
			return n.set(m);
		}
		if (o instanceof List) {
			@SuppressWarnings("unchecked")
			List<?> l = (List<?>) o;
			return n.set(l);
		}

		// Fallback: stringify
		return n.set(String.valueOf(o));
	}

	private static String readAllUtf8(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r;
		while ((r = in.read(buf)) != -1)
			baos.write(buf, 0, r);
		return baos.toString(StandardCharsets.UTF_8);
	}

	/*
	 * --------------------------- Minimal JSON parser/writer
	 * ---------------------------
	 */

	private static final class JsonParser {
		private final String s;
		private int i = 0;

		JsonParser(String s) {
			this.s = s;
		}

		void skipWs() {
			while (i < s.length()) {
				char c = s.charAt(i);
				if (c == ' ' || c == '\n' || c == '\r' || c == '\t')
					i++;
				else
					break;
			}
		}

		ModelNode parseValue() {
			skipWs();
			if (i >= s.length())
				return new ModelNode();

			char c = s.charAt(i);
			if (c == '{')
				return parseObject();
			if (c == '[')
				return parseArray();
			if (c == '"')
				return new ModelNode().set(parseString());
			if (c == 't' || c == 'f')
				return new ModelNode().set(parseBoolean());
			if (c == 'n') {
				parseNull();
				return new ModelNode().setNull();
			}
			return parseNumber();
		}

		ModelNode parseObject() {
			expect('{');
			ModelNode obj = new ModelNode();
			obj.type = Type.OBJECT;
			obj.value = new LinkedHashMap<String, ModelNode>();

			skipWs();
			if (peek('}')) {
				i++;
				return obj;
			}

			while (true) {
				skipWs();
				String key = parseString();
				skipWs();
				expect(':');
				ModelNode val = parseValue();

				@SuppressWarnings("unchecked")
				Map<String, ModelNode> m = (Map<String, ModelNode>) obj.value;
				m.put(key, val);

				skipWs();
				if (peek('}')) {
					i++;
					break;
				}
				expect(',');
			}

			return obj;
		}

		ModelNode parseArray() {
			expect('[');
			ModelNode arr = new ModelNode();
			arr.type = Type.LIST;
			arr.value = new ArrayList<ModelNode>();

			skipWs();
			if (peek(']')) {
				i++;
				return arr;
			}

			while (true) {
				ModelNode val = parseValue();
				@SuppressWarnings("unchecked")
				List<ModelNode> list = (List<ModelNode>) arr.value;
				list.add(val);

				skipWs();
				if (peek(']')) {
					i++;
					break;
				}
				expect(',');
			}

			return arr;
		}

		String parseString() {
			expect('"');
			StringBuilder sb = new StringBuilder();
			while (i < s.length()) {
				char c = s.charAt(i++);
				if (c == '"')
					break;
				if (c == '\\') {
					if (i >= s.length())
						break;
					char e = s.charAt(i++);
					switch (e) {
					case '"':
						sb.append('"');
						break;
					case '\\':
						sb.append('\\');
						break;
					case '/':
						sb.append('/');
						break;
					case 'b':
						sb.append('\b');
						break;
					case 'f':
						sb.append('\f');
						break;
					case 'n':
						sb.append('\n');
						break;
					case 'r':
						sb.append('\r');
						break;
					case 't':
						sb.append('\t');
						break;
					case 'u':
						if (i + 4 <= s.length()) {
							String hex = s.substring(i, i + 4);
							i += 4;
							try {
								sb.append((char) Integer.parseInt(hex, 16));
							} catch (NumberFormatException ignored) {
							}
						}
						break;
					default:
						sb.append(e);
					}
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
		}

		boolean parseBoolean() {
			if (s.startsWith("true", i)) {
				i += 4;
				return true;
			}
			if (s.startsWith("false", i)) {
				i += 5;
				return false;
			}
			throw new IllegalArgumentException("Invalid boolean at " + i);
		}

		void parseNull() {
			if (s.startsWith("null", i)) {
				i += 4;
				return;
			}
			throw new IllegalArgumentException("Invalid null at " + i);
		}

		ModelNode parseNumber() {
			int start = i;
			if (peek('-'))
				i++;
			while (i < s.length() && Character.isDigit(s.charAt(i)))
				i++;
			boolean isFloat = false;
			if (peek('.')) {
				isFloat = true;
				i++;
				while (i < s.length() && Character.isDigit(s.charAt(i)))
					i++;
			}
			if (peek('e') || peek('E')) {
				isFloat = true;
				i++;
				if (peek('+') || peek('-'))
					i++;
				while (i < s.length() && Character.isDigit(s.charAt(i)))
					i++;
			}

			String num = s.substring(start, i);
			ModelNode n = new ModelNode();
			n.type = Type.NUMBER;

			try {
				if (!isFloat) {
					// prefer int/long
					long lv = Long.parseLong(num);
					if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE)
						n.value = (int) lv;
					else
						n.value = lv;
				} else {
					n.value = Double.parseDouble(num);
				}
			} catch (NumberFormatException e) {
				// fallback
				n.type = Type.STRING;
				n.value = num;
			}
			return n;
		}

		boolean peek(char c) {
			return i < s.length() && s.charAt(i) == c;
		}

		void expect(char c) {
			skipWs();
			if (i >= s.length() || s.charAt(i) != c) {
				throw new IllegalArgumentException("Expected '" + c + "' at " + i);
			}
			i++;
		}
	}

	private static final class JsonWriter {

		static void write(ModelNode n, StringBuilder sb, int indent) {
			switch (n.type) {
			case UNDEFINED:
			case NULL:
				sb.append("null");
				return;
			case BOOLEAN:
				sb.append(((Boolean) n.value) ? "true" : "false");
				return;
			case NUMBER:
				sb.append(String.valueOf(n.value));
				return;
			case STRING:
				sb.append('"').append(escape((String) n.value)).append('"');
				return;
			case LIST:
				writeList(n, sb, indent);
				return;
			case OBJECT:
				writeObject(n, sb, indent);
				return;
			default:
				sb.append("null");
			}
		}

		private static void writeList(ModelNode n, StringBuilder sb, int indent) {
			@SuppressWarnings("unchecked")
			List<ModelNode> list = (List<ModelNode>) n.value;

			sb.append('[');
			if (list.isEmpty()) {
				sb.append(']');
				return;
			}

			boolean pretty = indent >= 0;
			int childIndent = pretty ? indent + 2 : -1;

			for (int idx = 0; idx < list.size(); idx++) {
				if (idx > 0)
					sb.append(',');
				if (pretty)
					newlineAndIndent(sb, childIndent);
				write(list.get(idx), sb, childIndent);
			}

			if (pretty)
				newlineAndIndent(sb, indent);
			sb.append(']');
		}

		private static void writeObject(ModelNode n, StringBuilder sb, int indent) {
			@SuppressWarnings("unchecked")
			Map<String, ModelNode> m = (Map<String, ModelNode>) n.value;

			sb.append('{');
			if (m.isEmpty()) {
				sb.append('}');
				return;
			}

			boolean pretty = indent >= 0;
			int childIndent = pretty ? indent + 2 : -1;

			int idx = 0;
			for (Map.Entry<String, ModelNode> e : m.entrySet()) {
				if (idx++ > 0)
					sb.append(',');
				if (pretty)
					newlineAndIndent(sb, childIndent);

				sb.append('"').append(escape(e.getKey())).append('"').append(':');
				if (pretty)
					sb.append(' ');
				write(e.getValue(), sb, childIndent);
			}

			if (pretty)
				newlineAndIndent(sb, indent);
			sb.append('}');
		}

		private static void newlineAndIndent(StringBuilder sb, int indent) {
			sb.append('\n');
			for (int i = 0; i < indent; i++)
				sb.append(' ');
		}

		private static String escape(String s) {
			StringBuilder out = new StringBuilder(s.length() + 16);
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				switch (c) {
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\b':
					out.append("\\b");
					break;
				case '\f':
					out.append("\\f");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
			return out.toString();
		}
	}

	/**
	 * Optional tiny adapter interface if you want a close drop-in to code that
	 * passes suppliers.
	 */
	@FunctionalInterface
	public interface SupplierInputStream {
		InputStream get() throws IOException;
	}
}