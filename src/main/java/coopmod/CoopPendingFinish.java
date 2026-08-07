package coopmod;

final class CoopPendingFinish {

	static final int ROOM_FINISH = 1;
	static final int ROOM_JOB = 2;
	static final int BUILD_JOB = 3;

	final int type;
	final int tx;
	final int ty;
	final int resourceIndex;
	final int amount;
	final String jobKey;
	final long createdMillis;
	int attempts;
	long nextMillis;

	private CoopPendingFinish(int type, int tx, int ty, int resourceIndex, int amount, String jobKey) {
		this.type = type;
		this.tx = tx;
		this.ty = ty;
		this.resourceIndex = resourceIndex;
		this.amount = amount;
		this.jobKey = jobKey == null ? "" : jobKey;
		this.createdMillis = System.currentTimeMillis();
	}

	static CoopPendingFinish roomFinish(int tx, int ty) {
		return new CoopPendingFinish(ROOM_FINISH, tx, ty, -1, 0, "");
	}

	static CoopPendingFinish roomJob(int tx, int ty, int resourceIndex, int amount) {
		return new CoopPendingFinish(ROOM_JOB, tx, ty, resourceIndex, amount, "");
	}

	static CoopPendingFinish buildJob(String jobKey, int tx, int ty) {
		return new CoopPendingFinish(BUILD_JOB, tx, ty, -1, 0, jobKey);
	}

	String key() {
		return type + ":" + tx + ":" + ty + ":" + resourceIndex + ":" + amount + ":" + jobKey;
	}

	String describe() {
		return "type=" + type + " tx=" + tx + " ty=" + ty + " res=" + resourceIndex + " amount=" + amount
				+ " key=" + jobKey + " attempts=" + attempts;
	}
}
