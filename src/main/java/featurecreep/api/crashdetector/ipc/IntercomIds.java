package featurecreep.api.crashdetector.ipc;

import java.util.regex.Pattern;

/**
 * Validates ids that become file and directory names inside the intercom.
 */
public final class IntercomIds {

	private static final Pattern PATRON = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	private IntercomIds() {
	}

	/**
	 * @return the id unchanged when it is safe to use as a file name
	 * @throws IllegalArgumentException if the id is null, too long, contains other
	 *                                  characters, or is "." or ".."
	 */
	public static String validar(String id) {
		if (id == null || !PATRON.matcher(id).matches() || id.equals(".") || id.equals("..")) {
			throw new IllegalArgumentException("id de intercom invalido: " + id);
		}
		return id;
	}

	public static boolean esValido(String id) {
		try {
			validar(id);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
