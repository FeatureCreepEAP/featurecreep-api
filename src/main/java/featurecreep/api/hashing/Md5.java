package featurecreep.api.hashing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5 implements Hash {

    public static String getHashFromFileAsString(File file) throws NoSuchAlgorithmException, IOException {
        return bytesToHex(calculateFileHash(file));
    }

    public static String getHashFromInputStreamAsString(InputStream stream) throws NoSuchAlgorithmException, IOException {
        return bytesToHex(calculateStreamHash(stream));
    }

    public static String getHashFromBytesAsString(byte[] bytes) throws NoSuchAlgorithmException, IOException {
        return bytesToHex(calculateBytesHash(bytes));
    }

    public static byte[] calculateStreamHash(InputStream stream) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");

        byte[] buffer = new byte[1024];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }

        return md.digest();
    }

    public static byte[] calculateBytesHash(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(bytes);
        return md.digest();
    }

    public static byte[] calculateFileHash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        return md.digest();
    }

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
