package featurecreep.api.crashdetector.ipc;

import java.util.Objects;
import java.util.Properties;

/**
 * Outcome of analysing one error class, published by the CrashDetector side.
 */
public record ResultRecord(String id, boolean triggered, String message, String level,
		double priority, long pid, String nonce) {

	private static final String ID = "id";
	private static final String ACTIVADO = "triggered";
	private static final String MENSAJE = "message";
	private static final String NIVEL = "level";
	private static final String PRIORIDAD = "priority";
	private static final String PID = "pid";
	private static final String NONCE = "nonce";

	public ResultRecord {
		IntercomIds.validar(id);
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(level, "level");
		if (!Double.isFinite(priority)) {
			throw new IllegalArgumentException("prioridad no finita en el resultado " + id);
		}
		IntercomSession.validarPid(pid);
		IntercomSession.validarNonce(nonce);
	}

	Properties aPropiedades() {
		Properties p = IntercomFiles.nuevasPropiedades();
		p.setProperty(ID, id);
		p.setProperty(ACTIVADO, Boolean.toString(triggered));
		p.setProperty(MENSAJE, message);
		p.setProperty(NIVEL, level);
		p.setProperty(PRIORIDAD, Double.toString(priority));
		p.setProperty(PID, Long.toString(pid));
		p.setProperty(NONCE, nonce);
		return p;
	}

	static ResultRecord desdePropiedades(Properties p) {
		IntercomFiles.comprobarFormato(p);
		return new ResultRecord(
				IntercomFiles.requerir(p, ID),
				IntercomFiles.requerirBoolean(p, ACTIVADO),
				IntercomFiles.requerir(p, MENSAJE),
				IntercomFiles.requerir(p, NIVEL),
				IntercomFiles.requerirDouble(p, PRIORIDAD),
				IntercomFiles.requerirLong(p, PID),
				IntercomFiles.requerir(p, NONCE));
	}
}
