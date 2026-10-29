package featurecreep.api.crashdetector.ipc;

import java.nio.file.Path;
import java.util.Objects;

/**
 * File layout of an intercom directory. Takes an explicit base directory so it
 * can be used without CrashDetector classes on the classpath.
 */
public final class IntercomLayout {

	public static final String CARPETA = "featurecreep-intercom";
	public static final String SUFIJO_TICKET = ".reg.properties";
	public static final String NOMBRE_SESION = "session.properties";
	public static final String NOMBRE_RESULTADO = "result.properties";

	private static final String REGISTRO = "registry";
	private static final String RESULTADOS = "results";

	private final Path base;

	public IntercomLayout(Path base) {
		this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
	}

	public Path base() {
		return base;
	}

	public Path sessionFile() {
		return base.resolve(NOMBRE_SESION);
	}

	public Path registryDir() {
		return base.resolve(REGISTRO);
	}

	public Path resultsDir() {
		return base.resolve(RESULTADOS);
	}

	public Path ticketFile(String id) {
		return registryDir().resolve(IntercomIds.validar(id) + SUFIJO_TICKET);
	}

	public Path resultDir(String id) {
		return resultsDir().resolve(IntercomIds.validar(id));
	}

	public Path resultFile(String id) {
		return resultDir(id).resolve(NOMBRE_RESULTADO);
	}
}
