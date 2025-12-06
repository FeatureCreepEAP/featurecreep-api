package featurecreep.api.crashdetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.asbestosstar.crashdetector.Extencion;
import com.asbestosstar.crashdetector.MonitorDePID;
import com.asbestosstar.crashdetector.analizador.Analizador;

import featurecreep.api.FCLoaderObtainer;
import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.listeners.IncompatibleEventListenerMethodReference;

/**
 * Extensión que: - En proceso de APP: limpia e inicializa la carpeta de
 * intercom y dispara listeners de registro. - En proceso de CrashDetector:
 * re-importa los tickets (.reg.properties) desde intercom/registry y los
 * inserta en el Analizador (vía CrashDetectorError.register).
 */
public class CDExtention implements Extencion {

	@Override
	public void procesoDeLaMonitorizacionDePID() {
		// Estamos en el proceso de CrashDetector
		CrashDetectorError.inCDProcess = true;

		// Re-importar tickets previamente generados por el proceso del juego
		reimportarRegistrosDesdeIntercom();
	}

	@Override
	public void procesoDelApp() {
		// Proceso del juego: limpiar/crear carpeta de intercom y disparar listeners
		File carpetaDeIntercom = MonitorDePID.carpeta.resolve("featurecreep-intercom").toFile();
		if (carpetaDeIntercom.exists() && carpetaDeIntercom.isDirectory()) {
			deleteDirectoryContents(carpetaDeIntercom);
		}
		carpetaDeIntercom.mkdirs();

		for (EventListener listener : FCLoaderObtainer.getFCLoaderBasic(this.getClass()).getEventViewer()
				.getListener(AppError.EVENT.getEvent_name())) {
			try {
				listener.invoke();
			} catch (IncompatibleEventListenerMethodReference e) {
				// No dejamos que un error aquí tumbe la inicialización
				e.printStackTrace();
			}
		}
	}

	// -----------------------------------------------------------
	// Re-importación de tickets (sólo en proceso CrashDetector)
	// -----------------------------------------------------------

	private void reimportarRegistrosDesdeIntercom() {
		File baseIntercom = MonitorDePID.carpeta.resolve("featurecreep-intercom").toFile();
		File carpetaRegistry = new File(baseIntercom, "registry");
		if (!carpetaRegistry.exists() || !carpetaRegistry.isDirectory()) {
			return; // Nada que importar
		}

		File[] tickets = carpetaRegistry.listFiles((dir, name) -> name.endsWith(".reg.properties"));
		if (tickets == null || tickets.length == 0) {
			return;
		}

		for (File ticket : tickets) {
			try {
				Properties p = new Properties();
				try (FileInputStream fis = new FileInputStream(ticket)) {
					p.load(fis);
				}

				final String id = trimOrEmpty(p.getProperty("id"));
				final String name = trimOrEmpty(p.getProperty("name"));
				final String clazz = trimOrEmpty(p.getProperty("class"));
				final String levelStr = trimOrEmpty(p.getProperty("level"));
				final String prioStr = trimOrEmpty(p.getProperty("priority"));
				final boolean dynamic = Boolean.parseBoolean(trimOrEmpty(p.getProperty("dynamic")));

				// Intento de instanciación directa si no es dinámico
				AppError appError = null;
				if (!dynamic && !clazz.isEmpty()) {
					appError = instanciarAppError(clazz);
				}
				// Si falló o es dinámico, usamos proxy
				if (appError == null) {
					appError = crearProxyDesdeTicket(baseIntercom, id, name, levelStr, prioStr);
				}

				// Registrar en Analizador (vía CrashDetectorError.register, que dentro de CD
				// añade a Analizador.verificaciones)
				CrashDetectorError envoltura = new CrashDetectorError(appError);
				// CrashDetectorError.register(envoltura, dynamic);
				MonitorDePID.analizador.verificaciones.add(envoltura);
			} catch (Throwable t) {
				// Tolerante a fallos: un ticket corrupto no debe detener la importación
				// (intencionalmente silencioso o logueable si se dispone de logger)
			}
		}
	}

	/**
	 * Intenta cargar e instanciar una clase AppError por reflexión. Soporta
	 * constructores sin args.
	 */
	private AppError instanciarAppError(String fqn) {
		try {
			Class<?> c = Class.forName(fqn);
			if (!AppError.class.isAssignableFrom(c)) {
				return null;
			}
			@SuppressWarnings("unchecked")
			Class<? extends AppError> ce = (Class<? extends AppError>) c;
			Constructor<? extends AppError> ctor = ce.getDeclaredConstructor();
			ctor.setAccessible(true);
			return ctor.newInstance();
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * Crea un AppError proxy basado en los metadatos del ticket y ficheros
	 * opcionales de resultados en intercom/results/<id>/: - message.txt ->
	 * contenido del mensaje - triggered.flag -> si existe, se considera true
	 */
	private AppError crearProxyDesdeTicket(File baseIntercom, String id, String name, String levelStr, String prioStr) {
		final File resultsDir = new File(new File(baseIntercom, "results"), idSafe(id));
		final File msgFile = new File(resultsDir, "message.txt");
		final File trigFile = new File(resultsDir, "triggered.flag");

		final String msg = leerArchivoComoString(msgFile);
		final boolean triggered = trigFile.isFile();

		final AppError.ErrorLevel level = parseLevel(levelStr);
		final double prio = parseDoubleOrDefault(prioStr, 0.0);

		return new AppError() {
			@Override
			public void parse(String logname, String log) {
				/* no-op en proxy */ }

			@Override
			public String name() {
				return name.isEmpty() ? id : name;
			}

			@Override
			public String id() {
				return id;
			}

			@Override
			public boolean triggered() {
				return triggered;
			}

			@Override
			public double priority() {
				return prio;
			}

			@Override
			public String message() {
				return (msg == null || msg.isEmpty()) ? name() : msg;
			}

			@Override
			public boolean matchesTrace(String trace) {
				return false;
			} // sin matching en proxy

			@Override
			public ErrorLevel errorLevel() {
				return level;
			}

			@Override
			public AppError newInstance() {
				return this;
			} // proxy es inmutable/simple
		};
	}

	private String leerArchivoComoString(File f) {
		if (f == null || !f.isFile())
			return null;
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line).append('\n');
			}
		} catch (IOException ignored) {
		}
		return sb.toString().trim();
	}

	private AppError.ErrorLevel parseLevel(String s) {
		if (s == null)
			return AppError.ErrorLevel.ERROR;
		try {
			return AppError.ErrorLevel.valueOf(s.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return AppError.ErrorLevel.ERROR;
		}
	}

	private double parseDoubleOrDefault(String s, double dflt) {
		try {
			return Double.parseDouble(s.trim());
		} catch (Exception e) {
			return dflt;
		}
	}

	private String trimOrEmpty(String s) {
		return s == null ? "" : s.trim();
	}

	private String idSafe(String s) {
		if (s == null || s.isEmpty())
			return "sin_id";
		return s.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	// -----------------------------------------------------------
	// Utilidades
	// -----------------------------------------------------------

	private void deleteDirectoryContents(File directory) {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					deleteDirectoryContents(file);
				}
				file.delete();
			}
		}
	}
}
