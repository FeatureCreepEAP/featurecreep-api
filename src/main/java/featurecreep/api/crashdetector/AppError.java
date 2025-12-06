package featurecreep.api.crashdetector;

import java.util.ArrayList;
import java.util.List;

import featurecreep.loader.eventviewer.EventViewerEvent;
import featurecreep.loader.eventviewer.events.BasicEvent;

/**
 * Errors for Crash Detection mods. For Crash Detector you are more limited as
 * the errors are transmitted to another process where you will not have access
 * to the same var instances or game classes, CrashDetector will instead get a serialised version of the
 * error to display, so for CrashDetector you should do most of the processing
 * here. For NotEnoughCrashes this limitation does not exist, however
 * NotEnoughCrashes does not come up on hard crashes, only soft crashes that
 * have a crash report and can maybe be deferred. The sooner you register the better, we recommend using the EventViewer event in this class to register a listener to register most of your reasons which in turn should have its listeners registered in premain or agent-main entrypoints.
 */
public interface AppError {

	
	EventViewerEvent EVENT = new BasicEvent("crashdetector");
	
	
	/**
	 * USE registerError(Error) method instead. This is where the errors are registered
	 */
	static List<AppError> errors = new ArrayList<AppError>();

	/**
	 * Register your errors here.
	 * @param error
	 * @param dynamic This option is for Crash Detector if your class is made dynamically, or is in a nested jar, or is relying on info in the game classes and needs to be serialised (true) or if it is just a static log analysis with a physical class file not in a nested jar¡.
	 */
	public static void registerError(AppError error, boolean dynamic) {
		errors.add(error);
		if (AppError.classExists("com.asbestosstar.crashdetector.analizador.Verificaciones")){
			CrashDetectorError cd = new CrashDetectorError(error);
			CrashDetectorError.register(cd, dynamic);
		}
		
		
	}
	
	
	
	/**
	 * This is where you parse the log to see if the error is triggered and to get
	 * information about the message. on CrashDetector this is often called on a seperate process so in the game process you should store info in a file if you need to intercommunicate 
	 * 
	 * @param logname
	 * @param log
	 */
	public void parse(String logname, String log);

	/**
	 * Public display name for the error
	 */
	public String name();

	/**
	 * An ID for the error, should be all ASCII with no special chars
	 * 
	 * @return
	 */
	public String id();

	/**
	 * If the error has been triggered in parse
	 * 
	 * @return
	 */
	public boolean triggered();

	/**
	 * Priority. CrashDetector has an unenforced high end of about 1000 with some
	 * going higher or lower
	 * 
	 * @return
	 */
	public double priority();

	/**
	 * The message to write in the results
	 * 
	 * @return
	 */
	public String message();

	/**
	 * For crash detection mods with ocupied stacktrace registries like
	 * CrashDetector, you can write if a stacktrace matches with one from the
	 * Stacktrace analyser
	 * 
	 * @param trace
	 * @return
	 */
	public boolean matchesTrace(String trace);

	/**
	 * The level of error, Fatal, Error, or Warning
	 * 
	 * @return
	 */
	public ErrorLevel errorLevel();
	
	/**
	 * A new instance of the current error
	 * @return
	 */
	public AppError newInstance();

	/**
	 * The criticality of the error
	 */
	enum ErrorLevel {
		FATAL, ERROR, WARNING
	}
	
	/**
	 * Checks if a class exists
	 * @param name
	 * @return
	 */
	public static boolean classExists(String name) {
		try {
			Class.forName(name);
			return true;
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return false;
		}
	}

}
