package featurecreep.api.crashdetector.ipc;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Publishes session, ticket and result records for one session. Records stamped
 * with another pid or nonce are rejected before anything is written.
 */
public final class IntercomWriter {

	private static final String CABECERA = "featurecreep crash intercom";

	private final IntercomLayout layout;
	private final IntercomSession sesion;

	public IntercomWriter(IntercomLayout layout, IntercomSession sesion) {
		this.layout = Objects.requireNonNull(layout, "layout");
		this.sesion = Objects.requireNonNull(sesion, "sesion");
	}

	public void escribirSesion() throws IOException {
		IntercomFiles.escribirAtomico(layout.sessionFile(), sesion.aPropiedades(), CABECERA + " session");
	}

	public void publicarTicket(Ticket ticket) throws IOException {
		comprobarSesion(ticket.pid(), ticket.nonce());
		IntercomFiles.escribirAtomico(layout.ticketFile(ticket.id()), ticket.aPropiedades(), CABECERA + " ticket");
	}

	public void publicarResultado(ResultRecord resultado) throws IOException {
		comprobarSesion(resultado.pid(), resultado.nonce());
		IntercomFiles.escribirAtomico(layout.resultFile(resultado.id()), resultado.aPropiedades(), CABECERA + " result");
	}

	private void comprobarSesion(long pid, String nonce) {
		if (pid != sesion.pid() || !sesion.nonce().equals(nonce)) {
			throw new IllegalArgumentException("registro de otra sesion");
		}
	}

	/**
	 * Removes only files named by the intercom protocol. Unknown files and
	 * non-empty directories are left in place.
	 */
	public static void limpiarSesionesAnteriores(IntercomLayout layout) throws IOException {
		Files.deleteIfExists(layout.sessionFile());
		borrarTickets(layout.registryDir());
		borrarResultados(layout.resultsDir());
	}

	private static void borrarTickets(Path carpeta) throws IOException {
		if (!Files.isDirectory(carpeta)) {
			return;
		}
		try (Stream<Path> entradas = Files.list(carpeta)) {
			for (Path archivo : entradas.toList()) {
				if (Files.isRegularFile(archivo)
						&& archivo.getFileName().toString().endsWith(IntercomLayout.SUFIJO_TICKET)) {
					Files.deleteIfExists(archivo);
				}
			}
		}
	}

	private static void borrarResultados(Path carpeta) throws IOException {
		if (!Files.isDirectory(carpeta)) {
			return;
		}
		try (Stream<Path> entradas = Files.list(carpeta)) {
			for (Path dir : entradas.toList()) {
				if (!Files.isDirectory(dir)) {
					continue;
				}
				Files.deleteIfExists(dir.resolve(IntercomLayout.NOMBRE_RESULTADO));
				borrarSiVacio(dir);
			}
		}
	}

	private static void borrarSiVacio(Path dir) throws IOException {
		try {
			Files.delete(dir);
		} catch (DirectoryNotEmptyException e) {
			// Holds files this protocol does not own, so it stays.
		}
	}
}
