package featurecreep.api.crashdetector.ipc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads intercom records. Never throws for bad content: unreadable, corrupt or
 * foreign records are skipped and described in the returned problem list.
 */
public final class IntercomReader {

	public record Resultado<T>(Optional<T> valor, List<String> problemas) {
	}

	public record Lectura<T>(List<T> validos, List<String> problemas) {
	}

	private final IntercomLayout layout;

	public IntercomReader(IntercomLayout layout) {
		this.layout = Objects.requireNonNull(layout, "layout");
	}

	public Resultado<IntercomSession> leerSesion(long pidEsperado) {
		Path archivo = layout.sessionFile();
		if (!Files.isRegularFile(archivo)) {
			return new Resultado<>(Optional.empty(), List.of("no hay sesion de intercom en " + archivo));
		}
		try {
			IntercomSession sesion = IntercomSession.desdePropiedades(IntercomFiles.leer(archivo));
			if (sesion.pid() != pidEsperado) {
				return new Resultado<>(Optional.empty(),
						List.of("la sesion es del pid " + sesion.pid() + ", no del " + pidEsperado));
			}
			return new Resultado<>(Optional.of(sesion), List.of());
		} catch (IOException | IllegalArgumentException e) {
			return new Resultado<>(Optional.empty(), List.of("sesion de intercom corrupta: " + e.getMessage()));
		}
	}

	public Lectura<Ticket> leerTickets(IntercomSession sesion) {
		Path carpeta = layout.registryDir();
		if (!Files.isDirectory(carpeta)) {
			return new Lectura<>(List.of(), List.of());
		}
		List<Path> archivos;
		try (Stream<Path> entradas = Files.list(carpeta)) {
			archivos = entradas
					.filter(p -> p.getFileName().toString().endsWith(IntercomLayout.SUFIJO_TICKET))
					.sorted()
					.toList();
		} catch (IOException e) {
			return new Lectura<>(List.of(), List.of("no se puede listar " + carpeta + ": " + e.getMessage()));
		}

		List<Ticket> validos = new ArrayList<>();
		List<String> problemas = new ArrayList<>();
		for (Path archivo : archivos) {
			String nombre = archivo.getFileName().toString();
			String id = nombre.substring(0, nombre.length() - IntercomLayout.SUFIJO_TICKET.length());
			try {
				IntercomIds.validar(id);
				Ticket ticket = Ticket.desdePropiedades(IntercomFiles.leer(archivo));
				comprobarRegistro(ticket.id(), id, ticket.pid(), ticket.nonce(), sesion);
				validos.add(ticket);
			} catch (IOException | IllegalArgumentException e) {
				problemas.add("ticket descartado " + nombre + ": " + e.getMessage());
			}
		}
		return new Lectura<>(List.copyOf(validos), List.copyOf(problemas));
	}

	public Resultado<ResultRecord> leerResultado(String id, IntercomSession sesion) {
		if (!IntercomIds.esValido(id)) {
			return new Resultado<>(Optional.empty(), List.of("id de resultado invalido: " + id));
		}
		Path archivo = layout.resultFile(id);
		if (!Files.exists(archivo)) {
			return new Resultado<>(Optional.empty(), List.of());
		}
		try {
			ResultRecord resultado = ResultRecord.desdePropiedades(IntercomFiles.leer(archivo));
			comprobarRegistro(resultado.id(), id, resultado.pid(), resultado.nonce(), sesion);
			return new Resultado<>(Optional.of(resultado), List.of());
		} catch (IOException | IllegalArgumentException e) {
			return new Resultado<>(Optional.empty(), List.of("resultado descartado " + id + ": " + e.getMessage()));
		}
	}

	private static void comprobarRegistro(String idRegistro, String idEsperado, long pid, String nonce,
			IntercomSession sesion) {
		if (!idRegistro.equals(idEsperado)) {
			throw new IllegalArgumentException("el id " + idRegistro + " no coincide con su archivo");
		}
		if (pid != sesion.pid() || !sesion.nonce().equals(nonce)) {
			throw new IllegalArgumentException("registro de otra sesion");
		}
	}
}
