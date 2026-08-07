package coopmod;

final class CoopFatal {

	private CoopFatal() {
	}

	static void rethrow(Throwable t) {
		if (t == null)
			return;
		if (t instanceof Error)
			throw (Error) t;
	}
}
