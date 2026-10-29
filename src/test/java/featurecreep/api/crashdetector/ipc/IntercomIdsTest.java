package featurecreep.api.crashdetector.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntercomIdsTest {

	@Test
	void acceptsPlainFileSafeIds() {
		assertEquals("OutOfMemory", IntercomIds.validar("OutOfMemory"));
		assertEquals("a.b-c_1", IntercomIds.validar("a.b-c_1"));
		assertTrue(IntercomIds.esValido("x".repeat(128)));
	}

	@Test
	void rejectsTraversalAndSeparators() {
		for (String id : new String[] { "..", ".", "../x", "a/b", "a\\b", "/abs", "x/../y" }) {
			assertFalse(IntercomIds.esValido(id), id);
			assertThrows(IllegalArgumentException.class, () -> IntercomIds.validar(id), id);
		}
	}

	@Test
	void rejectsEmptyNullTooLongAndOtherCharacters() {
		assertFalse(IntercomIds.esValido(null));
		assertFalse(IntercomIds.esValido(""));
		assertFalse(IntercomIds.esValido("x".repeat(129)));
		assertFalse(IntercomIds.esValido("con espacio"));
		assertFalse(IntercomIds.esValido("nul\u0000"));
	}
}
