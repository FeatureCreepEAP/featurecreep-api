package featurecreep.api;

import java.lang.instrument.Instrumentation;

import featurecreep.loader.eventviewer.EventViewer;

public class CommonEntryPoint {
	static boolean agentran=false;
	
    // Premain method, invoked before the main method
    public static void premain(String agentArgs, Instrumentation inst) {
        agentStuff(agentArgs,inst);
        agentran=true;
    }

    // Agentmain method, invoked when the agent is attached to a running JVM
    public static void agentmain(String agentArgs, Instrumentation inst) {
    	if(!agentran) {    
    	agentStuff(agentArgs,inst);
    	agentran=true;
    	}
    }
    
    public static void agentStuff(String args,Instrumentation inst) {
    	EventViewer vwr = FCLoaderObtainer.getFCLoaderBasic(CommonEntryPoint.class).getEventViewer();
    	vwr.addEvent(featurecreep.api.crashdetector.AppError.EVENT);
    }
    
    
    
}

