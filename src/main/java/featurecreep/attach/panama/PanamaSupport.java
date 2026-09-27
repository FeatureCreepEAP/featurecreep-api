package featurecreep.attach.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * Small Java 25 FFM helper used by FeatureCreep's native attach implementation.
 *
 * <p>This class deliberately does not use {@code jdk.attach}. It only provides
 * typed downcalls to operating-system APIs needed by the HotSpot attach protocol.</p>
 */
public final class PanamaSupport {
    public static final Linker LINKER = Linker.nativeLinker();
    public static final SymbolLookup DEFAULT_LOOKUP = LINKER.defaultLookup();
    public static final ValueLayout C_SIZE_T = (ValueLayout) LINKER.canonicalLayouts().get("size_t");
    public static final ValueLayout C_LONG = (ValueLayout) LINKER.canonicalLayouts().get("long");

    private static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();
    private static final VarHandle ERRNO_HANDLE = stateHandle("errno");

    private PanamaSupport() {
    }

    public static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = DEFAULT_LOOKUP.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("Native symbol not found: " + symbol));
        return LINKER.downcallHandle(address, descriptor);
    }

    public static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("Native symbol not found: " + symbol));
        return LINKER.downcallHandle(address, descriptor);
    }

    public static MethodHandle downcallWithErrno(String symbol, FunctionDescriptor descriptor, Linker.Option... extra) {
        MemorySegment address = DEFAULT_LOOKUP.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("Native symbol not found: " + symbol));
        Linker.Option[] options = new Linker.Option[extra.length + 1];
        options[0] = Linker.Option.captureCallState("errno");
        System.arraycopy(extra, 0, options, 1, extra.length);
        return LINKER.downcallHandle(address, descriptor, options);
    }

    public static NativeInt invokeIntWithErrno(MethodHandle handle, Object... args) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = arena.allocate(CAPTURE_STATE_LAYOUT);
            List<Object> actual = new ArrayList<>(args.length + 1);
            actual.add(state);
            for (Object arg : args) {
                actual.add(arg);
            }
            int value = (int) handle.invokeWithArguments(actual);
            return new NativeInt(value, errno(state));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static NativeLong invokeLongWithErrno(MethodHandle handle, Object... args) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = arena.allocate(CAPTURE_STATE_LAYOUT);
            List<Object> actual = new ArrayList<>(args.length + 1);
            actual.add(state);
            for (Object arg : args) {
                actual.add(arg);
            }
            long value = ((Number) handle.invokeWithArguments(actual)).longValue();
            return new NativeLong(value, errno(state));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int invokeInt(MethodHandle handle, Object... args) {
        try {
            return ((Number) handle.invokeWithArguments(args)).intValue();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long invokeLong(MethodHandle handle, Object... args) {
        try {
            return ((Number) handle.invokeWithArguments(args)).longValue();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment invokeAddress(MethodHandle handle, Object... args) {
        try {
            return (MemorySegment) handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void copyBytes(MemorySegment segment, long offset, byte[] bytes, int maxBytes) {
        int count = Math.min(bytes.length, maxBytes);
        MemorySegment.copy(MemorySegment.ofArray(bytes), 0, segment, offset, count);
    }

    public static void putCString(MemorySegment segment, long offset, long capacity, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int count = (int) Math.min(bytes.length, Math.max(0, capacity - 1));
        if (count > 0) {
            MemorySegment.copy(MemorySegment.ofArray(bytes), 0, segment, offset, count);
        }
        segment.set(ValueLayout.JAVA_BYTE, offset + count, (byte) 0);
    }

    public static RuntimeException rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Foreign call failed", throwable);
    }

    private static VarHandle stateHandle(String name) {
        return CAPTURE_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(name));
    }

    private static int errno(MemorySegment state) {
        try {
            return ((Number) ERRNO_HANDLE.get(state, 0L)).intValue();
        } catch (java.lang.invoke.WrongMethodTypeException ex) {
            return ((Number) ERRNO_HANDLE.get(state)).intValue();
        }
    }

    public record NativeInt(int value, int errno) {
    }

    public record NativeLong(long value, int errno) {
    }
}
