package coopmod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import game.event.engine.EContext;
import game.faction.diplomacy.deal.DealSave;
import view.ui.message.Message;

final class CoopProtocol {

	static final String MOD_VERSION = "0.8.0";
	static final int PROTOCOL_VERSION = 3;
	static final int MAX_NETWORK_LINE_CHARS = 8 * 1024 * 1024;
	private static final int MAX_SERIALIZED_BYTES = 4 * 1024 * 1024;
	private static final long MAX_OBJECT_DEPTH = 48;
	private static final long MAX_OBJECT_REFERENCES = 100_000;
	private static final long MAX_ARRAY_LENGTH = 1_000_000;

	private CoopProtocol() {
	}

	static String trim(String line) {
		if (line == null)
			return "";
		if (line.length() <= 220)
			return line;
		return line.substring(0, 220) + "...";
	}

	static String readNetworkLine(BufferedReader in) throws IOException {
		StringBuilder line = new StringBuilder(256);
		for (;;) {
			int value = in.read();
			if (value < 0)
				return line.length() == 0 ? null : line.toString();
			if (value == '\n') {
				int length = line.length();
				if (length > 0 && line.charAt(length - 1) == '\r')
					line.setLength(length - 1);
				return line.toString();
			}
			if (line.length() >= MAX_NETWORK_LINE_CHARS)
				throw new IOException("Multiplayer packet exceeds the maximum line length.");
			line.append((char) value);
		}
	}

	static String enc(String s) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	static String dec(String s) {
		return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
	}

	static String bool(boolean value) {
		return value ? "1" : "0";
	}

	static String serializeMessage(Message message) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(message);
		}
		return encodeSerialized(bytes);
	}

	static Message deserializeMessage(String serialized) throws IOException, ClassNotFoundException {
		return deserialize(serialized, Message.class);
	}

	static String serializeDealSave(DealSave save) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(2048);
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(save);
		}
		return encodeSerialized(bytes);
	}

	static DealSave deserializeDealSave(String serialized) throws IOException, ClassNotFoundException {
		return deserialize(serialized, DealSave.class);
	}

	static String serializeContext(EContext context) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(2048);
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(context);
		}
		return encodeSerialized(bytes);
	}

	static EContext deserializeContext(String serialized) throws IOException, ClassNotFoundException {
		return deserialize(serialized, EContext.class);
	}

	private static String encodeSerialized(ByteArrayOutputStream bytes) throws IOException {
		if (bytes.size() > MAX_SERIALIZED_BYTES)
			throw new IOException("Serialized multiplayer payload is too large: " + bytes.size() + " bytes.");
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private static <T> T deserialize(String serialized, Class<T> expected) throws IOException, ClassNotFoundException {
		if (serialized == null)
			throw new IOException("Serialized multiplayer payload is missing.");
		long maxEncoded = ((MAX_SERIALIZED_BYTES + 2L) / 3L) * 4L;
		if (serialized.length() > maxEncoded)
			throw new IOException("Serialized multiplayer payload exceeds the size limit.");
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(serialized);
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid serialized multiplayer payload encoding.", e);
		}
		if (bytes.length > MAX_SERIALIZED_BYTES)
			throw new IOException("Serialized multiplayer payload exceeds the size limit.");
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
			in.setObjectInputFilter(CoopProtocol::filterSerializedClass);
			Object value = in.readObject();
			if (!expected.isInstance(value))
				throw new IOException("Unexpected serialized multiplayer type: "
						+ (value == null ? "null" : value.getClass().getName()) + ".");
			if (in.read() != -1)
				throw new IOException("Serialized multiplayer payload contains trailing data.");
			return expected.cast(value);
		}
	}

	private static ObjectInputFilter.Status filterSerializedClass(ObjectInputFilter.FilterInfo info) {
		if (info.depth() > MAX_OBJECT_DEPTH || info.references() > MAX_OBJECT_REFERENCES
				|| info.streamBytes() > MAX_SERIALIZED_BYTES || info.arrayLength() > MAX_ARRAY_LENGTH)
			return ObjectInputFilter.Status.REJECTED;
		Class<?> type = info.serialClass();
		if (type == null)
			return ObjectInputFilter.Status.UNDECIDED;
		while (type.isArray())
			type = type.getComponentType();
		if (type.isPrimitive() || type.isEnum())
			return ObjectInputFilter.Status.ALLOWED;
		String name = type.getName();
		if (name.startsWith("java.lang.") || name.startsWith("java.util."))
			return ObjectInputFilter.Status.ALLOWED;
		if (name.startsWith("game.") || name.startsWith("view.ui.message.") || name.startsWith("settlement.")
				|| name.startsWith("world.") || name.startsWith("init.") || name.startsWith("snake2d.util."))
			return ObjectInputFilter.Status.ALLOWED;
		return ObjectInputFilter.Status.REJECTED;
	}
}
