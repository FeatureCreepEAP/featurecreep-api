package featurecreep.api.crashdetector;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.asbestosstar.crashdetector.Consola;
import com.asbestosstar.crashdetector.Statics;
import com.asbestosstar.crashdetector.analizador.Analizador;
import com.asbestosstar.crashdetector.analizador.Criticalidad;
import com.asbestosstar.crashdetector.analizador.QuickFix;
import com.asbestosstar.crashdetector.analizador.Verificaciones;
import com.asbestosstar.crashdetector.analizador.rapido.EstadoAnalisisArchivo;
import com.asbestosstar.crashdetector.analizador.rapido.EventoDeCoincidencia;
import com.asbestosstar.crashdetector.gui.tipos.docs.Documento;

import featurecreep.api.crashdetector.ipc.IntercomLayout;
import featurecreep.api.crashdetector.ipc.IntercomSession;
import featurecreep.api.crashdetector.ipc.IntercomWriter;
import featurecreep.api.crashdetector.ipc.ResultRecord;
import featurecreep.api.crashdetector.ipc.Ticket;

/**
 * Adapts an AppError to CrashDetector's Verificaciones.
 *
 * CrashDetector may run in another process. Registration has two paths:
 *
 * 1) CrashDetector process (inCDProcess=true): the wrapper is added to
 * Analizador.verificaciones, the only registration path.
 *
 * 2) Game process: a Ticket is published in the intercom so CrashDetector can
 * re-create the error in CDExtention. Results are published with
 * publicarResultado, and CrashDetector reads them lazily.
 *
 * A wrapper is triggered only when a log analysis triggered it.
 */
public class CrashDetectorError implements Verificaciones {

	public static volatile boolean inCDProcess = false;

	private static volatile Juego juego;

	private final AppError error;
	private final List<AppError> disparos = new CopyOnWriteArrayList<>();

	public CrashDetectorError(AppError error) {
		this.error = Objects.requireNonNull(error, "error");
	}

	/**
	 * Game process: starts a new intercom session for this run and removes the
	 * records of earlier runs.
	 */
	public static synchronized void escritorDelJuego() {
		try {
			IntercomLayout layout = carpetaDeIntercom();
			IntercomSession sesion = IntercomSession.crear(ProcessHandle.current().pid());
			IntercomWriter.limpiarSesionesAnteriores(layout);
			IntercomWriter escritor = new IntercomWriter(layout, sesion);
			escritor.escribirSesion();
			juego = new Juego(sesion, escritor);
		} catch (IOException | RuntimeException e) {
			juego = null;
			aviso("no se pudo iniciar el intercom de CrashDetector", e);
		}
	}

	/**
	 * Registers the error for CrashDetector, either directly in the CrashDetector
	 * process or as a ticket in the game process.
	 *
	 * @param envoltura wrapper to register
	 * @param dynamic   true if the error depends on game classes or is generated at
	 *                  runtime, so CrashDetector must not instantiate it
	 */
	public static void register(CrashDetectorError envoltura, boolean dynamic) {
		if (inCDProcess) {
			registrarEnAnalizador(envoltura);
		} else {
			publicarTicket(envoltura.error, dynamic);
		}
	}

	/**
	 * Game process: publishes the current result of an error for CrashDetector.
	 */
	public static void publicarResultado(AppError error) {
		Juego actual = juego;
		if (actual == null) {
			aviso("resultado sin intercom iniciado: " + error.id(), null);
			return;
		}
		try {
			actual.escritor().publicarResultado(new ResultRecord(
					error.id(),
					error.triggered(),
					Objects.toString(error.message(), ""),
					error.errorLevel().name(),
					error.priority(),
					actual.sesion().pid(),
					actual.sesion().nonce()));
		} catch (IOException | RuntimeException e) {
			aviso("no se pudo publicar el resultado de " + error.id(), e);
		}
	}

	static IntercomLayout carpetaDeIntercom() {
		return new IntercomLayout(Statics.carpeta.resolve(IntercomLayout.CARPETA));
	}

	static void aviso(String mensaje, Throwable causa) {
		System.err.println("[featurecreep-crashdetector] " + mensaje);
		if (causa != null) {
			causa.printStackTrace();
		}
	}

	private static void registrarEnAnalizador(CrashDetectorError envoltura) {
		synchronized (Analizador.verificaciones) {
			boolean repetida = Analizador.verificaciones.stream()
					.anyMatch(v -> v.id().equals(envoltura.id()));
			if (repetida) {
				aviso("verificacion repetida ignorada: " + envoltura.id(), null);
				return;
			}
			Analizador.verificaciones.add(envoltura);
		}
	}

	private static void publicarTicket(AppError error, boolean dynamic) {
		Juego actual = juego;
		if (actual == null) {
			aviso("registro sin intercom iniciado: " + error.id(), null);
			return;
		}
		try {
			actual.escritor().publicarTicket(new Ticket(
					error.id(),
					error.name(),
					error.getClass().getName(),
					dynamic,
					error.priority(),
					error.errorLevel().name(),
					actual.sesion().pid(),
					actual.sesion().nonce()));
		} catch (IOException | RuntimeException e) {
			aviso("no se pudo publicar el ticket de " + error.id(), e);
		}
	}

	@Override
	public Verificaciones nueva() {
		return new CrashDetectorError(error.newInstance());
	}

	@Override
	public boolean activado() {
		return !disparos.isEmpty();
	}

	@Override
	public String mensaje() {
		return disparos.isEmpty() ? "" : Objects.toString(disparos.get(0).message(), "");
	}

	@Override
	public String id() {
		return error.id();
	}

	@Override
	public String nombre() {
		return error.name();
	}

	@Override
	public float prioridad() {
		return (float) error.priority();
	}

	@Override
	public QuickFix solucion() {
		return QuickFix.NINGUN;
	}

	@Override
	public Documento docs() {
		return Documento.NINGUN;
	}

	@Override
	public Criticalidad nivel_de_criticalidad() {
		switch (error.errorLevel()) {
		case FATAL:
			return Criticalidad.FATAL;
		case WARNING:
			return Criticalidad.ADVERTENCIA;
		default:
			return Criticalidad.ERROR;
		}
	}

	@Override
	public String[] patronesRapidos() {
		return new String[0];
	}

	@Override
	public void verificarCoincidencia(EventoDeCoincidencia evento) {
		// El analisis se hace por archivo en finalizarArchivo, no por coincidencias rapidas.
	}

	@Override
	public void finalizarArchivo(Consola consola, EstadoAnalisisArchivo estado) {
		AppError analisis = error.newInstance();
		analisis.parse(String.valueOf(consola.archivo), Objects.toString(consola.contenido_verificar, ""));
		if (analisis.triggered()) {
			disparos.add(analisis);
		}
	}

	private record Juego(IntercomSession sesion, IntercomWriter escritor) {
	}
}
