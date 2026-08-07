package coopmod;

import java.io.IOException;

import script.SCRIPT;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

public final class CoopMod implements SCRIPT {

	@Override
	public CharSequence name() {
		return "Syx Together";
	}

	@Override
	public CharSequence desc() {
		return "Clean modular real-time co-op synchronisation with version checks, ping status, command sync, and multiplayer menu integration.";
	}

	@Override
	public boolean forceInit() {
		return true;
	}

	@Override
	public SCRIPT_INSTANCE createInstance() {
		CoopLog.installGlobalErrorHandler();
		return new Instance();
	}

	private static void crash(String context, RuntimeException e) {
		CoopLog.crash(context, e);
		throw e;
	}

	private static void crash(String context, Error e) {
		CoopLog.crash(context, e);
		throw e;
	}

	private static final class Instance implements SCRIPT_INSTANCE {

		@Override
		public void update(double ds) {
			try {
				CoopRuntime.update();
			} catch (RuntimeException e) {
				crash("Syx Together update failed.", e);
			} catch (Error e) {
				crash("Syx Together update crashed.", e);
			}
		}

		@Override
		public void render(Renderer r, float ds) {
			try {
				CoopRuntime.render(r);
			} catch (RuntimeException e) {
				crash("Syx Together render failed.", e);
			} catch (Error e) {
				crash("Syx Together render crashed.", e);
			}
		}

		@Override
		public void mouseClick(MButt button) {
			try {
				CoopRuntime.mouseClick(button);
			} catch (RuntimeException e) {
				crash("Syx Together mouse click failed.", e);
			} catch (Error e) {
				crash("Syx Together mouse click crashed.", e);
			}
		}

		@Override
		public void hover(COORDINATE mCoo, boolean mouseHasMoved) {
			try {
				CoopRuntime.hover(mCoo, mouseHasMoved);
			} catch (RuntimeException e) {
				crash("Syx Together hover failed.", e);
			} catch (Error e) {
				crash("Syx Together hover crashed.", e);
			}
		}

		@Override
		public void save(FilePutter file) {
		}

		@Override
		public void load(FileGetter file) throws IOException {
			CoopLog.installGlobalErrorHandler();
		}
	}
}
