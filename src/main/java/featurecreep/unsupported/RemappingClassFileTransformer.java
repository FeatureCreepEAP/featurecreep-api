package featurecreep.unsupported;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.asbestosstar.assistremapper.Mappings;
import com.asbestosstar.assistremapper.remapper.ClassRemapper;

import featurecreep.bytecode.ClassFileUtils;
import featurecreep.loader.FCLoaderBasic;
import featurecreep.loader.FCTransformer;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;

public class RemappingClassFileTransformer extends FCTransformer {

	
	public static Map<String, Mappings> fcied_classes = new HashMap<String, Mappings>();
	
	
	public static Map<String, Mappings> to_be_fcied_classes = new HashMap<String, Mappings>();
	
	
	/**
	 * 
	 */
	public static ArrayList<String> completa = new ArrayList<String>();

	public static ArrayList<ClassFile> clazzes = new ArrayList<ClassFile>();

	public ClassPool pool;
	
	/**
	 * A basic transformer to remap classes
	 * @param loader the modloader to look through
	 * @param pool the classpool you want to use, it is not used for all that much, just dependency resolution
	 */
	public RemappingClassFileTransformer(FCLoaderBasic loader,ClassPool pool) {
		super(loader);
		this.pool=pool;
		// TODO Auto-generated constructor stub
	}

	@Override
	public ByteBuffer transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
			ByteBuffer classBytes) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		if (completa.contains(className)) {
			return classBytes;
		} else {
			ByteBuffer fcied = classBytes;
			if (to_be_fcied_classes.containsKey(className)) {
				fcied = remapClass(classBytes, to_be_fcied_classes.get(className));
			}

			ByteBuffer transformed = super.transform(loader, className, protectionDomain, fcied);
			if (fcied_classes.containsKey(className)) {
				completa.add(className);
				if (transformed != null) {
					return remapClass(transformed, fcied_classes.get(className));
				} else {
					return remapClass(classBytes, fcied_classes.get(className));
				}

			}

			if (transformed != null) {
				return transformed;
			} else {
				completa.add(className);
				return classBytes;
			}
		}
	}

	public ByteBuffer remapClass(ByteBuffer buffer, Mappings maps) {
		try {
			ClassRemapper remapper = new ClassRemapper(maps, new ClassPool(pool),
					ClassFileUtils.classFileFromByteBuffer(buffer), null, 1);
			remapper.setUseResourceInputStream(true);
			remapper.setLoaderForResourceInputStream(RemappingClassFileTransformer.class.getClassLoader());
			remapper.setLoadFromClassPool(false); // No quieremos cargador los clases ahora
			remapper.dont_export = true;
			return ClassFileUtils.classFileToByteBuffer(remapper.file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return buffer;
		}
	}

}
