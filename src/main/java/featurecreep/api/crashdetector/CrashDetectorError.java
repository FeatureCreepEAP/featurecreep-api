package featurecreep.api.crashdetector;

import com.asbestosstar.crashdetector.Consola;
import com.asbestosstar.crashdetector.MonitorDePID;
import com.asbestosstar.crashdetector.analizador.QuickFix;
import com.asbestosstar.crashdetector.analizador.VerificacionDeStackTrace.TraceInfo;
import com.asbestosstar.crashdetector.analizador.Verificaciones;

/**
 * This is a wrapper for adding Verificaciones to CrashDetector. For Crash Detector you are more limited as
 * the errors are transmitted to another process where you will not have access
 * to the same vars, CrashDetector will instead get a serialised version of the
 * error to display, so for CrashDetector you should do most of the processing
 * here.
 */
public class CrashDetectorError implements Verificaciones{

	public static boolean inCDProcess= false;
	AppError error;
	
	/**
	 * Internal constructor for registering these to crash detector
	 * @param error
	 */
	public CrashDetectorError(AppError error) {
		this.error=error;
	}

	@Override
	public boolean activado() {
		// TODO Auto-generated method stub
		return error.triggered();
	}

	@Override
	public String id() {
		// TODO Auto-generated method stub
		return error.id();
	}

	@Override
	public String mensaje() {
		// TODO Auto-generated method stub
		return error.message();
	}

	@Override
	public String nombre() {
		// TODO Auto-generated method stub
		return error.name();
	}

	@Override
	public Verificaciones nueva() {
		// TODO Auto-generated method stub
		return new CrashDetectorError(error.newInstance());
	}

	@Override
	public boolean ocupaTrazo(TraceInfo arg0) {
		// TODO Auto-generated method stub
		return error.matchesTrace(arg0.trace);
	}

	@Override
	public float prioridad() {
		// TODO Auto-generated method stub
		return (float) error.priority();
	}

	@Override
	public QuickFix solucion() {
		// TODO Auto-generated method stub
		return new QuickFix.Builder(nombre()).agregarEtiqueta(MonitorDePID.idioma.noHaySolucionDisponible())
				.construir();
	}

	@Override
	public void verificar(Consola arg0) {
		// TODO Auto-generated method stub
		error.parse(arg0.archivo.toString(), arg0.contenido_verificar);
	}
	
	
	public static void register(CrashDetectorError error,boolean dynamic) {
		
	}
	
	
}
