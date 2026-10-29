package featurecreep.api.crashdetector.ipc;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Atomic writes and typed property access shared by the intercom records.
 */
final class IntercomFiles {

	static final String FORMATO = "featurecreep-intercom/1";
	static final String CLAVE_FORMATO = "format";

	private IntercomFiles() {
	}

	static Properties nuevasPropiedades() {
		Properties p = new Properties();
		p.setProperty(CLAVE_FORMATO, FORMATO);
		return p;
	}

	/**
	 * Writes to a temporary file in the same directory and then moves it over the
	 * destination, so readers see either the old or the new complete file.
	 */
	static void escribirAtomico(Path destino, Properties propiedades, String comentario) throws IOException {
		Path carpeta = destino.toAbsolutePath().getParent();
		Files.createDirectories(carpeta);
		Path temporal = Files.createTempFile(carpeta, "." + destino.getFileName() + ".", ".tmp");
		try {
			try (Writer w = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
				propiedades.store(w, comentario);
			}
			moverReemplazando(temporal, destino);
		} finally {
			Files.deleteIfExists(temporal);
		}
	}

	private static void moverReemplazando(Path origen, Path destino) throws IOException {
		try {
			Files.move(origen, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	static Properties leer(Path origen) throws IOException {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(origen, StandardCharsets.UTF_8)) {
			p.load(r);
		}
		return p;
	}

	static void comprobarFormato(Properties p) {
		String formato = p.getProperty(CLAVE_FORMATO);
		if (!FORMATO.equals(formato)) {
			throw new IllegalArgumentException("formato de intercom desconocido: " + formato);
		}
	}

	static String requerir(Properties p, String clave) {
		String valor = p.getProperty(clave);
		if (valor == null) {
			throw new IllegalArgumentException("falta la clave " + clave);
		}
		return valor;
	}

	static long requerirLong(Properties p, String clave) {
		try {
			return Long.parseLong(requerir(p, clave));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("valor no numerico en " + clave, e);
		}
	}

	static double requerirDouble(Properties p, String clave) {
		double valor;
		try {
			valor = Double.parseDouble(requerir(p, clave));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("valor no numerico en " + clave, e);
		}
		if (!Double.isFinite(valor)) {
			throw new IllegalArgumentException("valor no finito en " + clave);
		}
		return valor;
	}

	static boolean requerirBoolean(Properties p, String clave) {
		String valor = requerir(p, clave);
		if (!"true".equals(valor) && !"false".equals(valor)) {
			throw new IllegalArgumentException("valor booleano invalido en " + clave);
		}
		return Boolean.parseBoolean(valor);
	}
}
