package coopmod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public final class CoopLog {

	private static volatile boolean globalHandlerInstalled;

	private CoopLog() {
	}

	public static synchronized void installGlobalErrorHandler() {
		if (globalHandlerInstalled)
			return;
		globalHandlerInstalled = true;
		final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable throwable) {
				crash("Uncaught exception in thread " + (thread == null ? "unknown" : thread.getName()) + ".", throwable);
				if (previous != null)
					previous.uncaughtException(thread, throwable);
			}
		});
	}

	public static void warn(String message) {
		write("WARN", message, null);
	}

	public static void error(String message, Throwable throwable) {
		write("ERROR", message, throwable);
		writeErrorOnly("ERROR", message, throwable);
	}

	public static void crash(String message, Throwable throwable) {
		write("CRASH", message, throwable);
		writeErrorOnly("CRASH", message, throwable);
	}

	private static synchronized void write(String level, String message, Throwable throwable) {
		try {
			Path path = logPath();
			Path parent = path.getParent();
			if (parent != null)
				Files.createDirectories(parent);
			Files.writeString(path, format(level, message, throwable), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {
		}
	}

	private static synchronized void writeErrorOnly(String level, String message, Throwable throwable) {
		try {
			Path path = errorLogPath();
			Path parent = path.getParent();
			if (parent != null)
				Files.createDirectories(parent);
			Files.writeString(path, format(level, message, throwable), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {
		}
	}

	public static Path logPath() {
		try {
			URI uri = CoopLog.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			Path jar = Paths.get(uri);
			Path script = jar.getParent();
			Path v71 = script == null ? null : script.getParent();
			Path root = v71 == null ? null : v71.getParent();
			if (root != null)
				return root.resolve("coop-log.txt");
		} catch (Exception ignored) {
		}
		return Paths.get("coop-log.txt");
	}

	public static Path errorLogPath() {
		try {
			URI uri = CoopLog.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			Path jar = Paths.get(uri);
			Path script = jar.getParent();
			Path v71 = script == null ? null : script.getParent();
			Path root = v71 == null ? null : v71.getParent();
			if (root != null)
				return root.resolve("syx-together-errors.txt");
		} catch (Exception ignored) {
		}
		return Paths.get("syx-together-errors.txt");
	}

	private static String format(String level, String message, Throwable throwable) {
		StringBuilder sb = new StringBuilder(512);
		sb.append('[').append(LocalDateTime.now()).append("] ");
		sb.append(level).append(" ");
		sb.append(message == null ? "" : message).append(System.lineSeparator());
		if (throwable != null) {
			StringWriter sw = new StringWriter();
			throwable.printStackTrace(new PrintWriter(sw));
			sb.append(sw);
			if (!sb.toString().endsWith(System.lineSeparator()))
				sb.append(System.lineSeparator());
		}
		return sb.toString();
	}
}
