package featurecreep.bytecode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javassist.bytecode.ClassFile;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;

public class ClassFileUtils {

	public static DataInputStream dataInputStreamFromBytes(byte[] bytes) {
        // 创建一个ByteArrayInputStream来包装byte数组  
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);  
        // 创建一个DataInputStream来从ByteArrayInputStream中读取数据  
        return new DataInputStream(byteArrayInputStream);  
	}
	
	public static ClassFile classFileFromBytes(byte[] bytes) throws IOException { 
        return new ClassFile(dataInputStreamFromBytes(bytes));
	}
	
	public static ClassFile classFileFromByteBuffer(ByteBuffer bytes) throws IOException { 
        return new ClassFile(dataInputStreamFromBytes(bytes.array()));
	}
	
	public static MethodInfo getMethodInfoWithDescriptor(ClassFile file, String name, String descriptor) {
		for(MethodInfo def:file.getMethods()) {
			if(def.getName().equals(name)&&def.getDescriptor().equals(descriptor)) {
				return def;
			}
			
		}
		return null;
	}
	
	public static FieldInfo getFieldInfoWithDescriptor(ClassFile file, String name, String descriptor) {
		for(FieldInfo var:file.getFields()) {
			if(var.getName().equals(name)&&var.getDescriptor().equals(descriptor)) {
				return var;
			}
			
		}
		return null;
	}
	
	public static byte[] classFileToBytes(ClassFile file) throws IOException
	{	
		 // 创建一个ByteArrayOutputStream，它将用作DataOutputStream的底层流  
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();  
  
        // 创建一个DataOutputStream，它将数据写入ByteArrayOutputStream  
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);  
		
        file.write(dataOutputStream);
        
        return byteArrayOutputStream.toByteArray();
	}	
	
	public static ByteBuffer classFileToByteBuffer(ClassFile file) throws IOException
	{	
		return ByteBuffer.wrap(classFileToBytes(file));
	}
	
	public static void exportClassFile(File location, ClassFile file) throws IOException {
		location.getParentFile().mkdirs();
		 DataOutputStream out = new DataOutputStream(new FileOutputStream(location));
		file.write(out);
	}
	
}

