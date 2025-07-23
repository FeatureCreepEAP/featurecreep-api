package featurecreep.api.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class BasicIO {

	// https://www.javatpoint.com/new-line-in-java
	public static String getNewLineSeperator() {
		return System.getProperty("line.separator");
	}

	// https://howtodoinjava.com/java/io/java-read-file-to-string-examples/#2-using-fileslines-java-8
	public static String getFileContents(File file) {

		URI filePath = file.toURI();
		StringBuilder contentBuilder = new StringBuilder();

		try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
			// Read the content with Stream
			stream.forEach(s -> contentBuilder.append(s).append(getNewLineSeperator()));
		} catch (IOException e) {
			e.printStackTrace();
		}

		String fileContent = contentBuilder.toString();

		return fileContent;
	}

	// https://howtodoinjava.com/java/io/java-read-file-to-string-examples/#2-using-fileslines-java-8
	public static String getFileContentsOneLine(File file) {

		URI filePath = file.toURI();
		StringBuilder contentBuilder = new StringBuilder();

		try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
			// Read the content with Stream
			stream.forEach(s -> contentBuilder.append(s));
		} catch (IOException e) {
			e.printStackTrace();
		}

		String fileContent = contentBuilder.toString();

		return fileContent;
	}

	public static Set setFromArray(Object[] objects) {
		return new java.util.HashSet<>(java.util.Arrays.asList(objects));

	}
	
	public static Set deImmutaliseSet(Set set) {
	Set ret = new LinkedHashSet();
	ret.addAll(set);
	return ret;	
	}
	
    /**
     * Converts a byte array to a string using UTF-8 encoding.
     *
     * @param bytes The byte array to convert.
     * @return The converted string.
     */
    public static String byteArrayToString(byte[] bytes) {
        String string = null;
		try {
			string = new String(bytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        return string;
    }
    
    /**
     * Converts a String to a byte-array using UTF-8 encoding.
     *
     * @param bytes The String to convert.
     * @return The converted byte[].
     */
    public static byte[] stringToByteArray(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
   
    public static String inputstreamToString(InputStream stream) {
        StringBuilder textBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                textBuilder.append(line).append(System.lineSeparator()); // Add line break if needed
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return textBuilder.toString();
    }
    
    
    /**
     * Converts an input stream to byte array
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            
            return byteArrayOutputStream.toByteArray();
        }
    }
    
    
	public static Supplier<InputStream> inputStreamSupplierFromBytes(byte[] bytes) {
		return () -> new ByteArrayInputStream(bytes);
	}
    
    
    
}
