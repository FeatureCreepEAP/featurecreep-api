package featurecreep.api.crashdetector;

import java.io.File;

import com.asbestosstar.crashdetector.Extencion;
import com.asbestosstar.crashdetector.MonitorDePID;

import featurecreep.api.FCLoaderObtainer;
import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.listeners.IncompatibleEventListenerMethodReference;

public class CDExtention implements Extencion {

	@Override
	public void procesoDeLaMonitorizacionDePID() {
		// TODO Auto-generated method stub
		CrashDetectorError.inCDProcess = true;

		
		//reimport registered crash reasons from CrashDetectorError
		
	}

	@Override
	public void procesoDelApp() {
		// TODO Auto-generated method stub

		File carpta_de_intercom = MonitorDePID.carpeta.resolve("featurecreep-intercom").toFile();
		if (carpta_de_intercom.exists() && carpta_de_intercom.isDirectory()) {
			deleteDirectoryContents(carpta_de_intercom);
		}
		carpta_de_intercom.mkdirs();

		for (EventListener listener : FCLoaderObtainer.getFCLoaderBasic(this.getClass()).getEventViewer()
				.getListener(AppError.EVENT.getEvent_name())) {
			try {
				listener.invoke();
			} catch (IncompatibleEventListenerMethodReference e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private void deleteDirectoryContents(File directory) {
		File[] files = directory.listFiles();

		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					// Recursively delete the contents of the directory
					deleteDirectoryContents(file);
				}
				// Delete the file or empty directory
				file.delete();
			}
		}
	}

}
