package coopmod;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class CoopSaveTransfer {

	static final long MAX_SAVE_BYTES = 512L * 1024L * 1024L;
	private static final int MAX_CHUNK_BYTES = 64 * 1024;

	private CoopSaveTransfer() {
	}

	static byte[] readSaveBytes(Path path) throws IOException {
		long size = Files.size(path);
		if (size < 0 || size > MAX_SAVE_BYTES)
			throw new IOException("Save exceeds the maximum supported size: " + size + " bytes.");
		byte[] bytes = Files.readAllBytes(path);
		if (bytes.length != size)
			throw new IOException("Save changed while it was being read.");
		return bytes;
	}

	static Snapshot readSnapshot(BufferedReader in, String firstLine) throws IOException {
		if (firstLine == null)
			throw new IOException("Host closed the connection before sending the save.");
		if (firstLine.startsWith("ERR\t"))
			throw new IOException(firstLine.substring(4));
		if (firstLine.startsWith("SAVE\t"))
			return readLegacySnapshot(firstLine);
		if (!firstLine.startsWith("SAVE_META\t"))
			throw new IOException("Unexpected save response.");
		String[] meta = firstLine.split("\t", -1);
		if (meta.length < 5)
			throw new IOException("Invalid save metadata.");
		long replayAfter = parseNonNegativeLong(meta[4], "replay cursor");
		String hash = meta.length >= 6 ? meta[5] : "";
		Incoming incoming = new Incoming(meta[1], CoopProtocol.dec(meta[2]), parseNonNegativeLong(meta[3], "save size"), hash);
		long maxLines = incoming.size / 1_000L + 1024L;
		for (long lines = 0; lines < maxLines; lines++) {
			String line = CoopProtocol.readNetworkLine(in);
			if (line == null)
				throw new IOException("Host closed the connection during the save transfer.");
			String[] parts = line.split("\t", -1);
			if (parts.length >= 4 && "SAVE_CHUNK".equals(parts[0]) && incoming.id.equals(parts[1])) {
				incoming.append(parseNonNegativeLong(parts[2], "chunk offset"), parts[3]);
				continue;
			}
			if (parts.length >= 2 && "SAVE_DONE".equals(parts[0]) && incoming.id.equals(parts[1]))
				return new Snapshot(incoming.name, replayAfter, incoming.finish());
			if (line.startsWith("ERR\t"))
				throw new IOException(line.substring(4));
			throw new IOException("Unexpected packet during save transfer: " + CoopProtocol.trim(line));
		}
		throw new IOException("Save transfer contained too many packets.");
	}

	private static Snapshot readLegacySnapshot(String line) throws IOException {
		String[] parts = line.split("\t", 4);
		if (parts.length < 3)
			throw new IOException("Invalid legacy save response.");
		long replayAfter = parts.length >= 4 ? parseNonNegativeLong(parts[2], "replay cursor") : 0L;
		String encoded = parts.length >= 4 ? parts[3] : parts[2];
		long maxEncoded = ((MAX_SAVE_BYTES + 2L) / 3L) * 4L;
		if (encoded.length() > maxEncoded)
			throw new IOException("Legacy save exceeds the maximum supported size.");
		try {
			byte[] bytes = Base64.getDecoder().decode(encoded);
			if (bytes.length > MAX_SAVE_BYTES)
				throw new IOException("Legacy save exceeds the maximum supported size.");
			return new Snapshot("", replayAfter, bytes);
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid legacy save encoding.", e);
		}
	}

	private static long parseNonNegativeLong(String value, String label) throws IOException {
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 0)
				throw new IOException("Invalid " + label + ": " + value + ".");
			return parsed;
		} catch (NumberFormatException e) {
			throw new IOException("Invalid " + label + ": " + value + ".", e);
		}
	}

	static final class Snapshot {
		final String name;
		final long replayAfter;
		final byte[] bytes;

		Snapshot(String name, long replayAfter, byte[] bytes) {
			this.name = name;
			this.replayAfter = replayAfter;
			this.bytes = bytes;
		}
	}

	static String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			char[] hex = new char[digest.length * 2];
			final char[] digits = "0123456789abcdef".toCharArray();
			for (int i = 0; i < digest.length; i++) {
				int value = digest[i] & 0xff;
				hex[i * 2] = digits[value >>> 4];
				hex[i * 2 + 1] = digits[value & 0x0f];
			}
			return new String(hex);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	static final class Incoming {
		final String id;
		final String name;
		final long size;
		private final String expectedHash;
		private final ByteArrayOutputStream bytes;
		private long received;

		Incoming(String id, String name, long size, String expectedHash) throws IOException {
			if (id == null || id.length() == 0)
				throw new IOException("Save transfer id is missing.");
			if (size < 0 || size > MAX_SAVE_BYTES)
				throw new IOException("Invalid save size: " + size + ".");
			this.id = id;
			this.name = name == null ? "" : name;
			this.size = size;
			this.expectedHash = expectedHash == null ? "" : expectedHash.trim();
			this.bytes = new ByteArrayOutputStream((int) Math.min(size, 1024L * 1024L));
		}

		void append(long offset, String encodedChunk) throws IOException {
			if (offset != received)
				throw new IOException("Unexpected save chunk offset: expected " + received + " but received " + offset + ".");
			byte[] chunk;
			try {
				chunk = Base64.getDecoder().decode(encodedChunk);
			} catch (IllegalArgumentException e) {
				throw new IOException("Invalid Base64 save chunk.", e);
			}
			if (chunk.length == 0 || chunk.length > MAX_CHUNK_BYTES)
				throw new IOException("Invalid save chunk size: " + chunk.length + ".");
			if (received + chunk.length > size)
				throw new IOException("Save transfer exceeds its declared size.");
			bytes.write(chunk, 0, chunk.length);
			received += chunk.length;
		}

		int progress() {
			return size <= 0 ? 0 : (int) Math.min(99, (received * 100L) / size);
		}

		byte[] finish() throws IOException {
			if (received != size)
				throw new IOException("Incomplete save transfer: expected " + size + " bytes but received " + received + ".");
			byte[] result = bytes.toByteArray();
			if (expectedHash.length() > 0 && !expectedHash.equalsIgnoreCase(sha256(result)))
				throw new IOException("Save checksum mismatch.");
			return result;
		}
	}
}
