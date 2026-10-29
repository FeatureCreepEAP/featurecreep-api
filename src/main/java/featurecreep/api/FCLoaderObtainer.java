package featurecreep.api;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

import featurecreep.api.annotations.Nullable;
import featurecreep.loader.FCLoaderBasic;
import featurecreep.loader.FCLoaderBasicR9;
import featurecreep.loader.finder.FCFileSystemClassPathFinder;

/**
 * This tries to get the current FCLoaderBasic
 */
public class FCLoaderObtainer {

	/**
	 * Get the current FCLoaderBasic for a class. Falls back to the most recently
	 * created loader when the class is not owned by a FCLoaderBasic module, so
	 * callers in a running FeatureCreep session get a loader. It is null only
	 * when no loader has been created.
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

		FCLoaderBasic appLoader = FCFileSystemClassPathFinder.appClassPathLoader(clazz);
		return appLoader != null ? appLoader : FCLoaderBasicR9.active();
	}// TODO move to FCLoaderBasic class

}
