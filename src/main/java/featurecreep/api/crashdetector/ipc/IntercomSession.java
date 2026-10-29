package featurecreep.api.crashdetector.ipc;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Identifies one game run. The reader only accepts tickets and results that carry
 * this pid and nonce, so files left by earlier runs are ignored.
 */
public record IntercomSession(long pid, long startedAtMillis, String nonce) {

	private static final Pattern PATRON_NONCE = Pattern.compile("[0-9a-f]{32}");
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String PID = "pid";
	private static final String INICIO = "startedAt";
	private static final String NONCE_CLAVE = "nonce";

	public IntercomSession {
		validarPid(pid);
		if (startedAtMillis < 0) {
			throw new IllegalArgumentException("instante invalido: " + startedAtMillis);
		}
		validarNonce(nonce);
	}

	public static IntercomSession crear(long pid) {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return new IntercomSession(pid, System.currentTimeMillis(), HexFormat.of().formatHex(bytes));
	}

	static void validarPid(long pid) {
		if (pid <= 0) {
			throw new IllegalArgumentException("pid invalido: " + pid);
		}
	}

	static void validarNonce(String nonce) {
		if (nonce == null || !PATRON_NONCE.matcher(nonce).matches()) {
			throw new IllegalArgumentException("nonce invalido");
		}
	}

	Properties aPropiedades() {
		Properties p = IntercomFiles.nuevasPropiedades();
		p.setProperty(PID, Long.toString(pid));
		p.setProperty(INICIO, Long.toString(startedAtMillis));
		p.setProperty(NONCE_CLAVE, nonce);
		return p;
	}

	static IntercomSession desdePropiedades(Properties p) {
		IntercomFiles.comprobarFormato(p);
		return new IntercomSession(
				IntercomFiles.requerirLong(p, PID),
				IntercomFiles.requerirLong(p, INICIO),
				IntercomFiles.requerir(p, NONCE_CLAVE));
	}
}
