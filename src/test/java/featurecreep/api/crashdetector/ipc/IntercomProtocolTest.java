package featurecreep.api.crashdetector.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntercomProtocolTest {

	private static final long PID = 4242L;

	@TempDir
	Path tmp;

	private IntercomLayout layout() {
		return new IntercomLayout(tmp.resolve("intercom"));
	}

	private static Ticket ticket(String id, String name, IntercomSession s) {
		return new Ticket(id, name, "com.example." + name, true, 1.5, "ERROR", s.pid(), s.nonce());
	}

	private static ResultRecord result(String id, boolean triggered, String message, IntercomSession s) {
		return new ResultRecord(id, triggered, message, "FATAL", 2.0, s.pid(), s.nonce());
	}

	@Test
	void roundTripReturnsTheSameRecords() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		IntercomWriter w = new IntercomWriter(layout, s);
		Ticket t = ticket("OutOfMemory", "OutOfMemory", s);
		ResultRecord r = result("OutOfMemory", true, "linea 1\nlinea 2 = ok: \\", s);

		w.escribirSesion();
		w.publicarTicket(t);
		w.publicarResultado(r);

		IntercomReader reader = new IntercomReader(layout);
		IntercomReader.Resultado<IntercomSession> leida = reader.leerSesion(PID);
		assertEquals(s, leida.valor().orElseThrow());
		assertTrue(leida.problemas().isEmpty());

		IntercomReader.Lectura<Ticket> tickets = reader.leerTickets(s);
		assertEquals(List.of(t), tickets.validos());
		assertTrue(tickets.problemas().isEmpty());

		IntercomReader.Resultado<ResultRecord> res = reader.leerResultado("OutOfMemory", s);
		assertEquals(r, res.valor().orElseThrow());
		assertTrue(res.problemas().isEmpty());
	}

	@Test
	void unpublishedResultIsEmptyWithoutProblem() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		new IntercomWriter(layout, s).escribirSesion();

		IntercomReader.Resultado<ResultRecord> res = new IntercomReader(layout).leerResultado("Missing", s);
		assertFalse(res.valor().isPresent());
		assertTrue(res.problemas().isEmpty());
	}

	@Test
	void overwriteReplacesTheRecordAndLeavesNoTemporaryFiles() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		IntercomWriter w = new IntercomWriter(layout, s);
		w.escribirSesion();
		w.publicarTicket(ticket("Dup", "first", s));
		w.publicarTicket(ticket("Dup", "second", s));

		IntercomReader.Lectura<Ticket> lectura = new IntercomReader(layout).leerTickets(s);
		assertEquals(1, lectura.validos().size());
		assertEquals("second", lectura.validos().get(0).name());
		assertTrue(lectura.problemas().isEmpty());
		try (Stream<Path> files = Files.list(layout.registryDir())) {
			assertEquals(List.of(layout.ticketFile("Dup").getFileName()),
					files.map(Path::getFileName).toList());
		}
	}

	@Test
	void corruptFilesAreReportedNotThrown() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		IntercomWriter w = new IntercomWriter(layout, s);
		w.escribirSesion();
		w.publicarTicket(ticket("Good", "good", s));

		Files.createDirectories(layout.registryDir());
		Files.writeString(layout.registryDir().resolve("Bad" + IntercomLayout.SUFIJO_TICKET),
				"format=featurecreep-intercom/1\nid=Bad\n", StandardCharsets.UTF_8);
		Files.writeString(layout.registryDir().resolve("Junk" + IntercomLayout.SUFIJO_TICKET),
				"\u0000\u0001 basura sin formato", StandardCharsets.ISO_8859_1);

		IntercomReader reader = new IntercomReader(layout);
		IntercomReader.Lectura<Ticket> lectura = reader.leerTickets(s);
		assertEquals(1, lectura.validos().size());
		assertEquals("Good", lectura.validos().get(0).id());
		assertEquals(2, lectura.problemas().size());
		assertTrue(lectura.problemas().stream().anyMatch(p -> p.contains("Bad" + IntercomLayout.SUFIJO_TICKET)));

		Files.createDirectories(layout.resultDir("Trunc"));
		Files.writeString(layout.resultFile("Trunc"), "format=featurecreep-intercom/1\ntriggered=yes\n",
				StandardCharsets.UTF_8);
		IntercomReader.Resultado<ResultRecord> res = reader.leerResultado("Trunc", s);
		assertFalse(res.valor().isPresent());
		assertEquals(1, res.problemas().size());

		Files.writeString(layout.sessionFile(), "not a session", StandardCharsets.UTF_8);
		IntercomReader.Resultado<IntercomSession> sesion = reader.leerSesion(PID);
		assertFalse(sesion.valor().isPresent());
		assertFalse(sesion.problemas().isEmpty());
	}

	@Test
	void traversalIdsAreRejected() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		new IntercomWriter(layout, s).escribirSesion();
		Files.writeString(tmp.resolve("intercom").resolve("outside.properties"), "x", StandardCharsets.UTF_8);

		assertThrows(IllegalArgumentException.class, () -> layout.ticketFile("../outside"));
		assertThrows(IllegalArgumentException.class, () -> layout.resultFile(".."));
		assertThrows(IllegalArgumentException.class, () -> new Ticket("../x", "n", "c", false, 0, "ERROR",
				s.pid(), s.nonce()));

		IntercomReader.Resultado<ResultRecord> res = new IntercomReader(layout).leerResultado("../outside", s);
		assertFalse(res.valor().isPresent());
		assertEquals(1, res.problemas().size());
	}

	@Test
	void foreignSessionRecordsAreRejected() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession current = IntercomSession.crear(PID);
		IntercomSession previous = IntercomSession.crear(PID);
		new IntercomWriter(layout, current).escribirSesion();

		IntercomWriter oldRun = new IntercomWriter(layout, previous);
		oldRun.publicarTicket(ticket("Stale", "stale", previous));
		oldRun.publicarResultado(result("Stale", true, "old", previous));

		IntercomReader reader = new IntercomReader(layout);
		IntercomReader.Lectura<Ticket> lectura = reader.leerTickets(current);
		assertTrue(lectura.validos().isEmpty());
		assertEquals(1, lectura.problemas().size());

		IntercomReader.Resultado<ResultRecord> res = reader.leerResultado("Stale", current);
		assertFalse(res.valor().isPresent());
		assertEquals(1, res.problemas().size());
	}

	@Test
	void pidMismatchIsRejected() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		IntercomWriter w = new IntercomWriter(layout, s);
		w.escribirSesion();

		IntercomReader.Resultado<IntercomSession> sesion = new IntercomReader(layout).leerSesion(PID + 1);
		assertFalse(sesion.valor().isPresent());
		assertEquals(1, sesion.problemas().size());

		IntercomSession other = new IntercomSession(PID + 1, s.startedAtMillis(), s.nonce());
		assertThrows(IllegalArgumentException.class,
				() -> w.publicarTicket(ticket("Wrong", "wrong", other)));
		assertThrows(IllegalArgumentException.class,
				() -> w.publicarResultado(result("Wrong", true, "x", other)));
		assertFalse(Files.exists(layout.ticketFile("Wrong")));
	}

	@Test
	void cleanupRemovesOnlyProtocolFiles() throws IOException {
		IntercomLayout layout = layout();
		IntercomSession old = IntercomSession.crear(PID);
		IntercomWriter w = new IntercomWriter(layout, old);
		w.escribirSesion();
		w.publicarTicket(ticket("A", "a", old));
		w.publicarResultado(result("A", true, "m", old));
		w.publicarResultado(result("B", false, "m", old));

		Path unrelated = layout.base().resolve("notas.txt");
		Path registryNote = layout.registryDir().resolve("readme.txt");
		Path resultNote = layout.resultDir("A").resolve("otro.txt");
		Files.createDirectories(layout.resultDir("Ajeno"));
		Path ajeno = layout.resultsDir().resolve("Ajeno").resolve("otro.txt");
		Files.writeString(unrelated, "x", StandardCharsets.UTF_8);
		Files.writeString(registryNote, "x", StandardCharsets.UTF_8);
		Files.writeString(ajeno, "x", StandardCharsets.UTF_8);
		Files.writeString(resultNote, "x", StandardCharsets.UTF_8);

		IntercomWriter.limpiarSesionesAnteriores(layout);

		assertFalse(Files.exists(layout.sessionFile()));
		assertFalse(Files.exists(layout.ticketFile("A")));
		assertFalse(Files.exists(layout.resultFile("A")));
		assertFalse(Files.exists(layout.resultFile("B")));
		assertFalse(Files.exists(layout.resultDir("B")), "directorio vacio deberia borrarse");
		assertTrue(Files.exists(unrelated));
		assertTrue(Files.exists(registryNote));
		assertTrue(Files.exists(ajeno));
		assertTrue(Files.exists(resultNote));
	}

	@Test
	void concurrentPublishesOfTheSameIdLeaveOneCompleteRecord() throws Exception {
		IntercomLayout layout = layout();
		IntercomSession s = IntercomSession.crear(PID);
		IntercomWriter w = new IntercomWriter(layout, s);
		w.escribirSesion();

		int writers = 8;
		ExecutorService pool = Executors.newFixedThreadPool(writers);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		List<Ticket> candidatos = new ArrayList<>();
		try {
			for (int i = 0; i < writers; i++) {
				Ticket t = ticket("Race", "candidate" + i, s);
				candidatos.add(t);
				futures.add(pool.submit(() -> {
					start.await();
					w.publicarTicket(t);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> f : futures) {
				f.get();
			}
		} finally {
			pool.shutdownNow();
		}

		IntercomReader.Lectura<Ticket> lectura = new IntercomReader(layout).leerTickets(s);
		assertTrue(lectura.problemas().isEmpty(), () -> "problemas: " + lectura.problemas());
		assertEquals(1, lectura.validos().size());
		assertTrue(candidatos.contains(lectura.validos().get(0)));
		try (Stream<Path> files = Files.list(layout.registryDir())) {
			assertEquals(1, files.count());
		}
	}
}
