package featurecreep.api.crashdetector.ipc;

import java.util.Objects;
import java.util.Properties;

/**
 * Registration of one error class, published by the game side for the CrashDetector side.
 */
public record Ticket(String id, String name, String className, boolean dynamic, double priority,
		String level, long pid, String nonce) {

	private static final String ID = "id";
	private static final String NOMBRE = "name";
	private static final String CLASE = "className";
	private static final String DINAMICO = "dynamic";
	private static final String PRIORIDAD = "priority";
	private static final String NIVEL = "level";
	private static final String PID = "pid";
	private static final String NONCE = "nonce";

	public Ticket {
		IntercomIds.validar(id);
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(className, "className");
		Objects.requireNonNull(level, "level");
		if (!Double.isFinite(priority)) {
			throw new IllegalArgumentException("prioridad no finita en el ticket " + id);
		}
		IntercomSession.validarPid(pid);
		IntercomSession.validarNonce(nonce);
	}

	Properties aPropiedades() {
		Properties p = IntercomFiles.nuevasPropiedades();
		p.setProperty(ID, id);
		p.setProperty(NOMBRE, name);
		p.setProperty(CLASE, className);
		p.setProperty(DINAMICO, Boolean.toString(dynamic));
		p.setProperty(PRIORIDAD, Double.toString(priority));
		p.setProperty(NIVEL, level);
		p.setProperty(PID, Long.toString(pid));
		p.setProperty(NONCE, nonce);
		return p;
	}

	static Ticket desdePropiedades(Properties p) {
		IntercomFiles.comprobarFormato(p);
		return new Ticket(
				IntercomFiles.requerir(p, ID),
				IntercomFiles.requerir(p, NOMBRE),
				IntercomFiles.requerir(p, CLASE),
				IntercomFiles.requerirBoolean(p, DINAMICO),
				IntercomFiles.requerirDouble(p, PRIORIDAD),
				IntercomFiles.requerir(p, NIVEL),
				IntercomFiles.requerirLong(p, PID),
				IntercomFiles.requerir(p, NONCE));
	}
}
