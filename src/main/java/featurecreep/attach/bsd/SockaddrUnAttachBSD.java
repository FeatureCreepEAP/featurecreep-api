package featurecreep.attach.bsd;

import com.sun.jna.Structure;

public class SockaddrUnAttachBSD extends Structure {
	public short sun_family;
	public byte[] sun_path = new byte[108]; // UNIX_PATH_MAX = 108

	@Override
	public java.util.List<String> getFieldOrder() {
		return java.util.Arrays.asList("sun_family", "sun_path");
	}
}