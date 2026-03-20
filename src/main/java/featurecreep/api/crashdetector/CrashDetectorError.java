package featurecreep.api.crashdetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;

import com.asbestosstar.crashdetector.Consola;
import com.asbestosstar.crashdetector.MonitorDePID;
import com.asbestosstar.crashdetector.analizador.QuickFix;
import com.asbestosstar.crashdetector.analizador.VerificacionDeStackTrace.TraceInfo;
import com.asbestosstar.crashdetector.analizador.Verificaciones;
import com.asbestosstar.crashdetector.analizador.Analizador;

/**
 * Envoltura para adaptar AppError a Verificaciones de CrashDetector. NOTA:
 * CrashDetector puede correr en un proceso separado. Por ello, el registro de
 * errores contempla dos rutas:
 *
 * 1) Proceso CrashDetector (inCDProcess=true): - Se puede registrar
 * directamente el Verificaciones en Analizador.verificaciones.
 *
 * 2) Proceso del juego (inCDProcess=false): - No se puede acceder al Analizador
 * directamente. Se deja un "ticket" en la carpeta de intercom
 * (featurecreep-intercom/registry) con metadatos (id, clase, dynamic, etc.)
 * para que CrashDetector los re-importe durante su arranque (p. ej. en
 * CDExtention.procesoDelApp()).
 *
 * Para errores "dynamic" se recomienda hacer la mayor parte del parse en el
 * proceso del juego y escribir resultados en archivos propios de intercom.
 */
public class CrashDetectorError implements Verificaciones {

	public static boolean inCDProcess = false;

	AppError error;

	/**
	 * Constructor interno que envuelve un AppError.
	 */
	public CrashDetectorError(AppError error) {
		this.error = error;
	}

	@Override
	public boolean activado() {
		return error.triggered();
	}

	@Override
	public String id() {
		return error.id();
	}

	@Override
	public String mensaje() {
		return error.message();
	}

	@Override
	public String nombre() {
		return error.name();
	}

	@Override
	public Verificaciones nueva() {
		return new CrashDetectorError(error.newInstance());
	}

	@Override
	public boolean ocupaTrazo(TraceInfo info) {
		return error.matchesTrace(info.trace);
	}

	@Override
	public float prioridad() {
		return (float) error.priority();
	}

	@Override
	public QuickFix solucion() {
		return new QuickFix.Builder(nombre()).agregarEtiqueta(MonitorDePID.idioma.noHaySolucionDisponible())
				.construir();
	}

	@Override
	public void verificar(Consola consola) {
		// Delegamos el parseo del log al AppError envuelto.
		error.parse(consola.archivo.toString(), consola.contenido_verificar);
	}

	/**
	 * Registra el error para CrashDetector, considerando si estamos en el proceso
	 * de CrashDetector (registro directo) o en el proceso del juego (ticket en
	 * intercom).
	 *
	 * @param envoltura instancia ya envuelta (CrashDetectorError)
	 * @param dynamic   true si el AppError depende de clases/recursos del juego o
	 *                  se genera dinámicamente (nested jars, generación en
	 *                  runtime); false si es estático y el .class es accesible para
	 *                  CrashDetector.
	 */
	public static void register(CrashDetectorError envoltura, boolean dynamic) {
		if (envoltura == null || envoltura.error == null) {
			return;
		}

		if (inCDProcess) {
			// --- Ruta 1: estamos dentro del proceso de CrashDetector ---
			// Podemos registrar directamente la verificación para que el Analizador la
			// ejecute.
			try {
				Analizador.verificaciones.add(envoltura);
			} catch (Throwable t) {
				// Evitar que un fallo aquí tumbe el arranque del analizador.
				// (intencionalmente sin re-lanzar)
			}
			return;
		}

		// --- Ruta 2: estamos en el proceso del juego ---
		// Escribimos un ticket/entrada de registro en la carpeta de intercom para que
		// CrashDetector pueda re-importarlo. No asumimos que CrashDetector pueda
		// cargar clases del juego si dynamic=true.

		try {
			// Carpeta base de intercom (creada/limpiada en CDExtention.procesoDelApp()).
			final File baseIntercom = MonitorDePID.carpeta.resolve("featurecreep-intercom").toFile();
			final File carpetaRegistry = new File(baseIntercom, "registry");
			if (!carpetaRegistry.exists()) {
				carpetaRegistry.mkdirs();
			}

			// Usamos el id como nombre de archivo, saneado a caracteres seguros.
			final String idSeguro = sanitizarNombreArchivo(envoltura.id());
			final File archivoTicket = new File(carpetaRegistry, idSeguro + ".reg.properties");

			// Guardamos propiedades mínimas para la re-importación.
			final Properties props = new Properties();
			props.setProperty("id", safe(envoltura.id()));
			props.setProperty("name", safe(envoltura.nombre()));
			props.setProperty("class", safe(envoltura.error.getClass().getName()));
			props.setProperty("dynamic", Boolean.toString(dynamic));
			// Metadatos opcionales útiles para ordenado/visual:
			props.setProperty("priority", Double.toString(envoltura.error.priority()));
			props.setProperty("level", envoltura.error.errorLevel().name());

			// NOTA sobre dynamic:
			// - dynamic=true: CrashDetector NO debe intentar instanciar la clase del juego.
			// En su lugar, un importador del lado CrashDetector puede crear un proxy
			// (p. ej. otra CrashDetectorError vacía) que sólo lee resultados pre-procesados
			// desde featurecreep-intercom (p. ej. /results/<id>.json) escritos por el
			// juego.
			// - dynamic=false: CrashDetector puede intentar cargar/reflejar la clase
			// indicada
			// si el classpath compartido lo permite. Si no, al menos tendrá el id/nombre
			// para mostrar algo o pedir al usuario los resultados generados.

			try (OutputStream os = new FileOutputStream(archivoTicket)) {
				props.store(os, "Registro de AppError para CrashDetector (dynamic=" + dynamic + ")");
			}
		} catch (Throwable t) {
			// Silencioso: el registro no debe romper el juego si la carpeta no existe, etc.
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	/**
	 * Sanea un nombre para archivo simple (ASCII seguro).
	 */
	private static String sanitizarNombreArchivo(String nombre) {
		if (nombre == null || nombre.isEmpty())
			return "sin_id";
		// Reemplazar todo lo que no sea [A-Za-z0-9._-] por '_'
		return nombre.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
