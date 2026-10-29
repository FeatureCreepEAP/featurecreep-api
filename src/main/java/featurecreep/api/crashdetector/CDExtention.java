package featurecreep.api.crashdetector;

import java.lang.reflect.Constructor;

import com.asbestosstar.crashdetector.Extencion;
import com.asbestosstar.crashdetector.MonitorDePID;

import featurecreep.api.FCLoaderObtainer;
import featurecreep.api.crashdetector.ipc.IntercomReader;
import featurecreep.api.crashdetector.ipc.IntercomSession;
import featurecreep.api.crashdetector.ipc.ResultRecord;
import featurecreep.api.crashdetector.ipc.Ticket;
import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.listeners.IncompatibleEventListenerMethodReference;

/**
 * Game process: starts the intercom session and fires the AppError listeners.
 * CrashDetector process: re-imports the tickets of the game's current session.
 */
public class CDExtention implements Extencion {

	@Override
	public void procesoDeLaMonitorizacionDePID() {
		CrashDetectorError.inCDProcess = true;
		reimportarRegistrosDesdeIntercom();
	}

	@Override
	public void procesoDelApp() {
		CrashDetectorError.escritorDelJuego();

		for (EventListener listener : FCLoaderObtainer.getFCLoaderBasic(this.getClass()).getEventViewer()
				.getListener(AppError.EVENT.getEvent_name())) {
			try {
				listener.invoke();
			} catch (IncompatibleEventListenerMethodReference e) {
				CrashDetectorError.aviso("listener de AppError incompatible", e);
			}
		}
	}

	private static void reimportarRegistrosDesdeIntercom() {
		IntercomReader lector = new IntercomReader(CrashDetectorError.carpetaDeIntercom());
		IntercomReader.Resultado<IntercomSession> sesion = lector.leerSesion(MonitorDePID.pid);
		if (sesion.valor().isEmpty()) {
			sesion.problemas().forEach(p -> CrashDetectorError.aviso(p, null));
			return;
		}

		IntercomSession actual = sesion.valor().get();
		IntercomReader.Lectura<Ticket> tickets = lector.leerTickets(actual);
		tickets.problemas().forEach(p -> CrashDetectorError.aviso(p, null));

		for (Ticket ticket : tickets.validos()) {
			AppError app = ticket.dynamic() ? null : instanciarAppError(ticket);
			if (app == null) {
				app = new ProxyDesdeIntercom(ticket, lector, actual);
			}
			CrashDetectorError.register(new CrashDetectorError(app), ticket.dynamic());
		}
	}

	private static AppError instanciarAppError(Ticket ticket) {
		try {
			Class<?> clase = Class.forName(ticket.className());
			if (!AppError.class.isAssignableFrom(clase)) {
				CrashDetectorError.aviso(ticket.className() + " no implementa AppError; se usa proxy", null);
				return null;
			}
			Constructor<? extends AppError> ctor = clase.asSubclass(AppError.class).getDeclaredConstructor();
			ctor.setAccessible(true);
			return ctor.newInstance();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			CrashDetectorError.aviso("no se pudo instanciar " + ticket.className() + "; se usa proxy", e);
			return null;
		}
	}

	/**
	 * Stands in for an error CrashDetector cannot instantiate. Its triggered state
	 * and message come only from the result the game published.
	 */
	private static final class ProxyDesdeIntercom implements AppError {

		private final Ticket ticket;
		private final IntercomReader lector;
		private final IntercomSession sesion;
		private final AppError.ErrorLevel nivel;

		ProxyDesdeIntercom(Ticket ticket, IntercomReader lector, IntercomSession sesion) {
			this.ticket = ticket;
			this.lector = lector;
			this.sesion = sesion;
			this.nivel = parseNivel(ticket);
		}

		@Override
		public void parse(String logname, String log) {
			// Los resultados los publica el proceso del juego, no el analisis de logs.
		}

		@Override
		public String name() {
			return ticket.name().isEmpty() ? ticket.id() : ticket.name();
		}

		@Override
		public String id() {
			return ticket.id();
		}

		@Override
		public boolean triggered() {
			IntercomReader.Resultado<ResultRecord> r = leer();
			r.problemas().forEach(p -> CrashDetectorError.aviso(p, null));
			return r.valor().map(ResultRecord::triggered).orElse(false);
		}

		@Override
		public double priority() {
			return ticket.priority();
		}

		@Override
		public String message() {
			return leer().valor().map(ResultRecord::message).orElse("");
		}

		@Override
		public boolean matchesTrace(String trace) {
			return false;
		}

		@Override
		public ErrorLevel errorLevel() {
			return nivel;
		}

		@Override
		public AppError newInstance() {
			return this;
		}

		private IntercomReader.Resultado<ResultRecord> leer() {
			return lector.leerResultado(ticket.id(), sesion);
		}

		private static AppError.ErrorLevel parseNivel(Ticket ticket) {
			try {
				return AppError.ErrorLevel.valueOf(ticket.level());
			} catch (IllegalArgumentException e) {
				CrashDetectorError.aviso("nivel desconocido '" + ticket.level() + "' en " + ticket.id()
						+ "; se usa ERROR", e);
				return AppError.ErrorLevel.ERROR;
			}
		}
	}
}
