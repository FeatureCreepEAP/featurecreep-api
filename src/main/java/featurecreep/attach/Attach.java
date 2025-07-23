package featurecreep.attach;

import java.io.IOException;
import java.io.InputStream;

import io.smallrye.common.os.OS;

/**
 * This is a reimplementation of Suns Attach mechanism which does not require a
 * JDK. It mostly uses JNA to avoid native code
 */
public class Attach {

	public static void attach(String agent, String args) {
		//String str = agent + "=" + args;
		//String str = agent;

		try {
			if (OS.current().equals(OS.LINUX)) {

				AttachLinux.attach();
				System.out.println("loading agent");
				AttachLinux.loadAgent(agent, "testargs");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("done loading agent");

	}

}
