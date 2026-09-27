package featurecreep.attach.panama;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/** Solaris door primitives used by the direct HotSpot attach provider. */
public final class SolarisNative {
    private static final int ENOENT = 2;
    private static final int O_RDWR = 2;
    private static final int DOOR_DESCRIPTOR = 0x4000;

    // door_arg_t on 64-bit Solaris:
    // void*, size_t, door_desc_t*, uint_t, padding, char*, size_t
    private static final MemoryLayout STATUS_TARGET = MemoryLayout.sequenceLayout(4, JAVA_BYTE);
    private static final java.lang.foreign.AddressLayout STATUS_ADDRESS = ADDRESS.withTargetLayout(STATUS_TARGET);
    private static final StructLayout DOOR_DESC = MemoryLayout.structLayout(
            JAVA_INT.withName("d_attributes"),
            MemoryLayout.paddingLayout(4),
            MemoryLayout.unionLayout(
                    JAVA_INT.withName("d_descriptor"),
                    JAVA_LONG.withName("d_id")
            ).withName("d_data")
    );
    private static final java.lang.foreign.AddressLayout DESC_ADDRESS = ADDRESS.withTargetLayout(DOOR_DESC);
    private static final StructLayout DOOR_ARG = MemoryLayout.structLayout(
            STATUS_ADDRESS.withName("data_ptr"),
            PanamaSupport.C_SIZE_T.withName("data_size"),
            DESC_ADDRESS.withName("desc_ptr"),
            JAVA_INT.withName("desc_num"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("rbuf"),
            PanamaSupport.C_SIZE_T.withName("rsize")
    );

    private static final long DATA_PTR = DOOR_ARG.byteOffset(MemoryLayout.PathElement.groupElement("data_ptr"));
    private static final long DATA_SIZE = DOOR_ARG.byteOffset(MemoryLayout.PathElement.groupElement("data_size"));
    private static final long DESC_PTR = DOOR_ARG.byteOffset(MemoryLayout.PathElement.groupElement("desc_ptr"));
    private static final long DESC_NUM = DOOR_ARG.byteOffset(MemoryLayout.PathElement.groupElement("desc_num"));
    private static final long RBUF = DOOR_ARG.byteOffset(MemoryLayout.PathElement.groupElement("rbuf"));
    private static final long RSIZE = DOOR_ARG.byteOffset(MemoryLayout.PathElement.groupElement("rsize"));

    private static final MethodHandle OPEN = PanamaSupport.downcallWithErrno(
            "open", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle DOOR_CALL = PanamaSupport.downcallWithErrno(
            "door_call", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

    private SolarisNative() {}

    public static int openDoor(String path) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(path);
            PanamaSupport.NativeInt result = PanamaSupport.invokeIntWithErrno(OPEN, cPath, O_RDWR);
            if (result.value() < 0) {
                if (result.errno() == ENOENT) throw new FileNotFoundException("Door file not found: " + path);
                throw new IOException("open(" + path + ") failed (errno=" + result.errno() + ")");
            }
            return result.value();
        }
    }

    /**
     * Execute a Solaris door call and return the response file descriptor.
     * The first response integer is the HotSpot attach completion status.
     */
    public static DoorResult doorCall(int doorFd, byte[] command) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(command.length, 1);
            MemorySegment.copy(MemorySegment.ofArray(command), 0, data, 0, command.length);
            MemorySegment response = arena.allocate(128, 8);
            MemorySegment arg = arena.allocate(DOOR_ARG);

            arg.set(ADDRESS, DATA_PTR, data);
            setSizeT(arg, DATA_SIZE, command.length);
            arg.set(ADDRESS, DESC_PTR, MemorySegment.NULL);
            arg.set(JAVA_INT, DESC_NUM, 0);
            arg.set(ADDRESS, RBUF, response);
            setSizeT(arg, RSIZE, response.byteSize());

            PanamaSupport.NativeInt call = PanamaSupport.invokeIntWithErrno(DOOR_CALL, doorFd, arg);
            if (call.value() != 0) {
                throw new IOException("door_call failed (errno=" + call.errno() + ")");
            }

            int status = response.get(JAVA_INT, 0);
            MemorySegment returnedData = arg.get(STATUS_ADDRESS, DATA_PTR);
            if (returnedData != null && returnedData.address() != 0L) {
                try {
                    status = returnedData.get(JAVA_INT, 0);
                } catch (IndexOutOfBoundsException ignored) {
                    // rbuf above already contains the status for the normal HotSpot reply.
                }
            }

            int descCount = arg.get(JAVA_INT, DESC_NUM);
            int responseFd = -1;
            if (descCount > 0) {
                MemorySegment desc = arg.get(DESC_ADDRESS, DESC_PTR);
                if (desc != null && desc.address() != 0L) {
                    int attributes = desc.get(JAVA_INT, 0);
                    if ((attributes & DOOR_DESCRIPTOR) != 0) {
                        responseFd = desc.get(JAVA_INT, 8);
                    }
                }
            }
            return new DoorResult(status, responseFd);
        }
    }

    private static void setSizeT(MemorySegment segment, long offset, long value) {
        if (PanamaSupport.C_SIZE_T.byteSize() == Integer.BYTES) {
            segment.set(JAVA_INT, offset, Math.toIntExact(value));
        } else {
            segment.set(JAVA_LONG, offset, value);
        }
    }

    public record DoorResult(int status, int responseFd) {}
}
