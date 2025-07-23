package featurecreep.api;

import javassist.ClassPool;

public class ClassPoolNewer1st {

	public static boolean setClassPoolToNewer1st(ClassPool pool, boolean result) {
		pool.childFirstLookup = result;
		return result;

	}

}
