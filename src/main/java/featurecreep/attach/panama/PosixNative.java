package featurecreep.attach.panama;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/** POSIX native calls used by Unix HotSpot attach implementations. */
public final class PosixNative {
    public static final int SIGQUIT = 3;
    public static final int EINTR = 4;
    public static final int ENOENT = 2;

    private static final MethodHandle KILL = PanamaSupport.downcallWithErrno(
            "kill", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle GETEUID = PanamaSupport.downcall(
            "geteuid", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle GETEGID = PanamaSupport.downcall(
            "getegid", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle CLOSE = PanamaSupport.downcallWithErrno(
            "close", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final MethodHandle READ = PanamaSupport.downcallWithErrno(
            "read", FunctionDescriptor.of(PanamaSupport.C_LONG, JAVA_INT, ADDRESS, PanamaSupport.C_SIZE_T));

    private PosixNative() {
    }

    public static void sendQuit(int pid) throws IOException {
        PanamaSupport.NativeInt result = PanamaSupport.invokeIntWithErrno(KILL, pid, SIGQUIT);
        if (result.value() == -1) {
            throw new IOException("kill(SIGQUIT) failed for pid " + pid + " (errno=" + result.errno() + ")");
        }
    }

    public static int effectiveUid() {
        return PanamaSupport.invokeInt(GETEUID);
    }

    public static int effectiveGid() {
        return PanamaSupport.invokeInt(GETEGID);
    }

    public static void close(int fd) throws IOException {
        PanamaSupport.NativeInt result;
        do {
            result = PanamaSupport.invokeIntWithErrno(CLOSE, fd);
        } while (result.value() == -1 && result.errno() == EINTR);
        if (result.value() == -1) {
            throw new IOException("close failed (errno=" + result.errno() + ")");
        }
    }

    public static int read(int fd, byte[] target, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        int request = Math.min(length, 8192);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(request);
            PanamaSupport.NativeLong result;
            do {
                result = PanamaSupport.invokeLongWithErrno(READ, fd, buffer, (long) request);
            } while (result.value() == -1 && result.errno() == EINTR);
            if (result.value() < 0) {
                throw new IOException("read failed (errno=" + result.errno() + ")");
            }
            if (result.value() == 0) {
                return -1;
            }
            int count = Math.toIntExact(result.value());
            MemorySegment.copy(buffer, 0, MemorySegment.ofArray(target), offset, count);
            return count;
        }
    }
}
