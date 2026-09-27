package featurecreep.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PKZipUtils {

	public static void zipDirectory(File directory, String output) throws IOException {
		File zipFile = new File(output);
		File parent = zipFile.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		try (FileOutputStream fos = new FileOutputStream(zipFile);
				ZipOutputStream zos = new ZipOutputStream(fos)) {
			zipDirectory(directory, zos, "");
		}
	}

	public static void zipDirectory(File directory, ZipOutputStream zos, String parentFolder) throws IOException {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					// If it's a directory, recursively call this method
					zipDirectory(file, zos, parentFolder + file.getName() + "/");
				} else {
					// If it's a file, add it to the ZIP output stream
					addToZip(zos, file, parentFolder + file.getName());
				}
			}
		}
	}

	public static void addToZip(ZipOutputStream zos, File file, String fileName) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)) {
// Create a new ZIP entry with the file's name  
			ZipEntry zipEntry = new ZipEntry(fileName);
			zos.putNextEntry(zipEntry);

// Copy the file's content to the ZIP output stream  
			byte[] buffer = new byte[1024];
			int length;
			while ((length = fis.read(buffer)) > 0) {
				zos.write(buffer, 0, length);
			}

// Close the current ZIP entry  
			zos.closeEntry();
		}
	}

}
