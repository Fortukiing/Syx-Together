package coopmod;

final class CoopHandshake {

	final int protocol;
	final String modVersion;
	final String gameVersion;
	final String modHash;

	CoopHandshake(int protocol, String modVersion, String gameVersion, String modHash) {
		this.protocol = protocol;
		this.modVersion = modVersion;
		this.gameVersion = gameVersion;
		this.modHash = modHash;
	}

	String summary() {
		return "protocol=" + protocol + " coop=" + modVersion + " game=" + gameVersion + " mods=" + modHash;
	}
}
