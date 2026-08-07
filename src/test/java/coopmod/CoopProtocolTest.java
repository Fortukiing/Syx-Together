package coopmod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;

public final class CoopProtocolTest {

	private CoopProtocolTest() {
	}

	public static void main(String[] args) throws Exception {
		disallowedClassIsRejected();
		oversizedSerializedPayloadIsRejectedBeforeDecode();
		invalidBase64IsRejected();
		System.out.println("CoopProtocolTest: PASS");
	}

	private static void disallowedClassIsRejected() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(new UnsafePayload());
		}
		expectIOException(() -> CoopProtocol.deserializeMessage(Base64.getEncoder().encodeToString(bytes.toByteArray())),
				"disallowed serialized class");
	}

	private static void oversizedSerializedPayloadIsRejectedBeforeDecode() {
		String oversized = "A".repeat(5_592_410);
		expectIOException(() -> CoopProtocol.deserializeMessage(oversized), "oversized serialized payload");
	}

	private static void invalidBase64IsRejected() {
		expectIOException(() -> CoopProtocol.deserializeMessage("not-base64!"), "invalid Base64");
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

	private static final class UnsafePayload implements Serializable {
		private static final long serialVersionUID = 1L;
	}

	@FunctionalInterface
	private interface ThrowingAction {
		void run() throws Exception;
	}
}
