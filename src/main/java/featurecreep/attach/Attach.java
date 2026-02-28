package featurecreep.attach;

import featurecreep.api.lowlevel.OS;

/**
 * This is a reimplementation of Suns Attach mechanism which does not require a
 * JDK. It mostly uses JNA to avoid native code. Still in Alpha, I wrote all
 * these different times so formattings a bit different and not stable as i plan
 * to change it a lot. AIX is untested, Linux Aand Solaris Have traces sometimes
 * and Linux sometimes has C lib crashes outside of VMs, Windows can only attach
 * to self at this time, some BSDs and Illuminos are untested, Many SYSVs are
 * unsupported as are most other NONUnix 0Ss, are some of the issues.
 */
public class Attach {

	public static void attach(String agent, String args) {
		// String str = agent + "=" + args;
		// String str = agent;

		try {
			if (OS.current().equals(OS.LINUX)) {

				AttachLinux.attach();
				System.out.println("loading agent");
				AttachLinux.loadAgent(agent, "testargs");
			} else if (OS.current().equals(OS.AIX)) {

				AttachAix.attach();
				System.out.println("loading agent");
				AttachAix.loadAgent(agent, "testargs");
			} else if (OS.current().equals(OS.SOLARIS)) {

				AttachSolaris.attach();
				System.out.println("loading agent");
				AttachSolaris.loadAgent(agent, "testargs");
			} else if (OS.current().equals(OS.MAC) || OS.current().name().toLowerCase().contains("bsd")) {

				BSDAttach bsd = new BSDAttach(String.valueOf(ProcessHandle.current().pid()));
				System.out.println("loading agent");
				bsd.loadAgent(agent, "testargs");
			}else if (OS.current().equals(OS.WINDOWS)) {//Possibly OS/2 or Arca, though that could be a UNIX?
	            AttachWindows vm = new AttachWindows((int)ProcessHandle.current().pid());
				System.out.println("loading agent");
				 vm.loadAgent(agent, null);
			}
			
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("done loading agent");

	}

}
