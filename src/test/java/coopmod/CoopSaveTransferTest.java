package coopmod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public final class CoopSaveTransferTest {

	private CoopSaveTransferTest() {
	}

	public static void main(String[] args) throws Exception {
		validChunkedTransfer();
		outOfOrderChunkIsRejected();
		wrongChecksumIsRejected();
		incompleteTransferIsRejected();
		oversizedTransferIsRejected();
		System.out.println("CoopSaveTransferTest: PASS");
	}

	private static void validChunkedTransfer() throws Exception {
		byte[] expected = "a synchronized city save".getBytes(StandardCharsets.UTF_8);
		CoopSaveTransfer.Incoming incoming = new CoopSaveTransfer.Incoming("valid", "City", expected.length,
				CoopSaveTransfer.sha256(expected));
		byte[] first = Arrays.copyOfRange(expected, 0, 8);
		byte[] second = Arrays.copyOfRange(expected, 8, expected.length);
		incoming.append(0, encode(first));
		incoming.append(first.length, encode(second));
		assertArrayEquals(expected, incoming.finish(), "valid transfer changed bytes");
	}

	private static void outOfOrderChunkIsRejected() throws Exception {
		CoopSaveTransfer.Incoming incoming = new CoopSaveTransfer.Incoming("order", "City", 4, "");
		expectIOException(() -> incoming.append(2, encode(new byte[] { 1, 2 })), "out-of-order chunk");
	}

	private static void wrongChecksumIsRejected() throws Exception {
		byte[] bytes = new byte[] { 1, 2, 3 };
		CoopSaveTransfer.Incoming incoming = new CoopSaveTransfer.Incoming("hash", "City", bytes.length,
				"0000000000000000000000000000000000000000000000000000000000000000");
		incoming.append(0, encode(bytes));
		expectIOException(incoming::finish, "wrong checksum");
	}

	private static void incompleteTransferIsRejected() throws Exception {
		CoopSaveTransfer.Incoming incoming = new CoopSaveTransfer.Incoming("short", "City", 5, "");
		incoming.append(0, encode(new byte[] { 1, 2 }));
		expectIOException(incoming::finish, "incomplete transfer");
	}

	private static void oversizedTransferIsRejected() {
		expectIOException(() -> new CoopSaveTransfer.Incoming("large", "City",
				CoopSaveTransfer.MAX_SAVE_BYTES + 1L, ""), "oversized transfer");
	}

	private static String encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static void expectIOException(ThrowingAction action, String label) {
		try {
			action.run();
			throw new AssertionError("Expected IOException for " + label + ".");
		} catch (IOException expected) {
			// Expected protocol rejection.
		} catch (Exception e) {
			throw new AssertionError("Unexpected exception for " + label + ".", e);
		}
	}

	private static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
		if (!Arrays.equals(expected, actual))
			throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface ThrowingAction {
		void run() throws Exception;
	}
}
