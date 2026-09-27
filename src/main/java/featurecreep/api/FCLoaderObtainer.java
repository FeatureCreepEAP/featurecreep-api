package featurecreep.api;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

import featurecreep.api.annotations.Nullable;
import featurecreep.loader.FCLoaderBasic;

/**
 * This tries to get the current FCLoaderBasic
 */
public class FCLoaderObtainer {

	/**
	 * Get the current FCLoaderBasic for a class. If there is no FCLoaderBasic or
	 * the class is not part of a Module it is null.
	 * 
	 * @param clazz
	 * @return
	 */
	public static @Nullable FCLoaderBasic getFCLoaderBasic(Class<?> clazz) {
		Module mod = Module.forClass(clazz);
		if (mod != null) {
			ModuleLoader loader = mod.getModuleLoader();
			if (loader != null && loader instanceof FCLoaderBasic) {
				return (FCLoaderBasic) loader;
			}

		}

		return null;
	}// TODO move to FCLoaderBasic class

}
