package featurecreep.attach;

import featurecreep.attach.panama.WindowsNative;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Windows direct HotSpot attach provider implemented with Java 25 FFM. */
public final class AttachWindows implements AutoCloseable {
    private static final int PIPE_ACCESS_INBOUND = 0x00000001;
    private static final int PIPE_TYPE_BYTE = 0x00000000;
    private static final int PIPE_READMODE_BYTE = 0x00000000;
    private static final int PIPE_WAIT = 0x00000000;
    private static final int WAIT_OBJECT_0 = 0;
    private static final int INFINITE = 0xFFFFFFFF;
    private static final int NMPWAIT_USE_DEFAULT_WAIT = 0;
    private static final int TOKEN_ADJUST_PRIVILEGES = 0x0020;
    private static final int SE_PRIVILEGE_ENABLED = 0x00000002;
    private static final int SECURITY_IMPERSONATION = 2;
    private static final String SE_DEBUG_NAME = "SeDebugPrivilege";
    private static final boolean IS_64BIT = ValueLayout.ADDRESS.byteSize() == 8;

    private static final int DATA_BLOCK_SIZE = 3440;
    private static final int OFF_GET_MODULE_HANDLE = 0;
    private static final int OFF_GET_PROC_ADDRESS = 8;
    private static final int OFF_JVM_LIB = 16;
    private static final int OFF_FUNC1 = 32;
    private static final int OFF_FUNC2 = 64;
    private static final int OFF_CMD = 96;
    private static final int OFF_ARG0 = 112;
    private static final int OFF_ARG1 = 1136;
    private static final int OFF_ARG2 = 2160;
    private static final int OFF_PIPE = 3184;

    private MemorySegment process;
    private final int targetPid;

    public AttachWindows(int pid) throws IOException {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        if (!IS_64BIT) throw new IOException("The FeatureCreep Windows attach stub currently requires a 64-bit JVM");
        this.targetPid = pid;
        this.process = openProcess(pid);
    }

    public static final class AttachStub {
    private static final int[] ATTACH_STUB_CODE_AMD64 = { 0x48, 0x89, 0x5C, 0x24, 0x08, 0x57, 0x48, 0x83, 0xEC,
    0x30, 0x48, 0x8B, 0xD9, 0x48, 0x83, 0xC1, 0x10, 0xFF, 0x13, 0x48, 0x8B, 0xF8, 0x48, 0x85, 0xC0, 0x75,
    0x07, 0xB8, 0xC8, 0x00, 0x00, 0x00, 0xEB, 0x59, 0x48, 0x8D, 0x53, 0x20, 0x48, 0x8B, 0xCF, 0xFF, 0x53,
    0x08, 0x4C, 0x8B, 0xD0, 0x48, 0x85, 0xC0, 0x75, 0x19, 0x48, 0x8D, 0x53, 0x40, 0x48, 0x8B, 0xCF, 0xFF,
    0x53, 0x08, 0x4C, 0x8B, 0xD0, 0x48, 0x85, 0xC0, 0x75, 0x07, 0xB8, 0xC9, 0x00, 0x00, 0x00, 0xEB, 0x2E,
    0x48, 0x8D, 0x4B, 0x60, 0x80, 0x39, 0x00, 0x75, 0x04, 0x33, 0xC0, 0xEB, 0x21, 0x48, 0x8D, 0x83, 0x70,
    0x0C, 0x00, 0x00, 0x4C, 0x8D, 0x8B, 0x70, 0x08, 0x00, 0x00, 0x48, 0x89, 0x44, 0x24, 0x20, 0x4C, 0x8D,
    0x83, 0x70, 0x04, 0x00, 0x00, 0x48, 0x8D, 0x53, 0x70, 0x41, 0xFF, 0xD2, 0x48, 0x8B, 0x5C, 0x24, 0x40,
    0x48, 0x83, 0xC4, 0x30, 0x5F, 0xC3, 0xCC, 0xCC };

    private static final int[] ATTACH_STUB_CODE_ARM = { 0xF3, 0x53, 0xBE, 0xA9, 0xFE, 0x0B, 0x00, 0xF9, 0xF3, 0x03,
    0x00, 0xAA, 0x68, 0x02, 0x40, 0xF9, 0x60, 0x42, 0x00, 0x91, 0x00, 0x01, 0x3F, 0xD6, 0xF4, 0x03, 0x00,
    0xAA, 0x74, 0x00, 0x00, 0xB5, 0x00, 0x19, 0x80, 0x52, 0x19, 0x00, 0x00, 0x14, 0x68, 0x06, 0x40, 0xF9,
    0x61, 0x82, 0x00, 0x91, 0xE0, 0x03, 0x14, 0xAA, 0x00, 0x01, 0x3F, 0xD6, 0xE9, 0x03, 0x00, 0xAA, 0x29,
    0x01, 0x00, 0xB5, 0x68, 0x06, 0x40, 0xF9, 0x61, 0x02, 0x01, 0x91, 0xE0, 0x03, 0x14, 0xAA, 0x00, 0x01,
    0x3F, 0xD6, 0xE9, 0x03, 0x00, 0xAA, 0x69, 0x00, 0x00, 0xB5, 0x20, 0x19, 0x80, 0x52, 0x0B, 0x00, 0x00,
    0x14, 0x68, 0x82, 0xC1, 0x39, 0x68, 0x00, 0x00, 0x35, 0x00, 0x00, 0x80, 0x52, 0x07, 0x00, 0x00, 0x14,
    0x64, 0xC2, 0x31, 0x91, 0x63, 0xC2, 0x21, 0x91, 0x62, 0xC2, 0x11, 0x91, 0x61, 0xC2, 0x01, 0x91, 0x60,
    0x82, 0x01, 0x91, 0x20, 0x01, 0x3F, 0xD6, 0xFE, 0x0B, 0x40, 0xF9, 0xF3, 0x53, 0xC2, 0xA8, 0xC0, 0x03,
    0x5F, 0xD6, 0x00, 0x00, 0x00, 0x00 };// Thanks to lexmanos for providing this

    // TODO Itanium if there was java 8 for itanium on windows

    private static final String ARCH = System.getProperty("os.arch").toLowerCase();

    public static byte[] getStubCode() {
    int[] selected = isArm() ? ATTACH_STUB_CODE_ARM : ATTACH_STUB_CODE_AMD64;
    byte[] stubBytes = new byte[selected.length];
    for (int i = 0; i < selected.length; i++) {
    stubBytes[i] = (byte) selected[i];
    }
    return stubBytes;
    }

    public static int getStubSize() {
    return isArm() ? ATTACH_STUB_CODE_ARM.length : ATTACH_STUB_CODE_AMD64.length;
    }

    private static boolean isArm() {
    // Common ARM identifiers: aarch64, arm, arm64
    return ARCH.contains("arm") || ARCH.contains("aarch");
    }
    }

    public void loadAgent(String agentPath, String options) throws IOException {
        Path agent = Path.of(agentPath).toAbsolutePath();
        if (!Files.isRegularFile(agent)) throw new IOException("Agent file not found: " + agent);
        String payload = options == null || options.isEmpty() ? agent.toString() : agent + "=" + options;
        String pipeName = "\\\\.\\pipe\\featurecreep-attach-" + Integer.toUnsignedString(WindowsNative.tickCount())
                + "-" + targetPid;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pipe = WindowsNative.createNamedPipe(arena, pipeName, PIPE_ACCESS_INBOUND,
                    PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT, 1, 4096, 8192, NMPWAIT_USE_DEFAULT_WAIT);
            if (WindowsNative.isNull(pipe) || pipe.address() == -1L) {
                throw new IOException("CreateNamedPipe failed (error=" + WindowsNative.lastError() + ")");
            }
            try {
                injectThread(pipeName, new String[] { "load", "instrument", "false", payload });
                String response = readResponse(pipe);
                validateAgentResponse(response);
            } finally {
                WindowsNative.closeHandle(pipe);
            }
        }
    }

    private MemorySegment openProcess(int pid) throws IOException {
        MemorySegment handle = WindowsNative.openProcess(pid);
        if (WindowsNative.isNull(handle) && WindowsNative.lastError() == WindowsNative.ERROR_ACCESS_DENIED) {
            if (enableDebugPrivileges()) handle = WindowsNative.openProcess(pid);
        }
        if (WindowsNative.isNull(handle)) {
            throw new IOException("Could not open process " + pid + " (error=" + WindowsNative.lastError() + ")");
        }
        if (!checkBitness(handle)) {
            WindowsNative.closeHandle(handle);
            throw new IOException("Architecture mismatch between current process and target JVM");
        }
        return handle;
    }

    private boolean enableDebugPrivileges() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment token = WindowsNative.openCurrentThreadToken(arena, TOKEN_ADJUST_PRIVILEGES);
            if (WindowsNative.isNull(token)) {
                if (!WindowsNative.impersonateSelf(SECURITY_IMPERSONATION)) return false;
                token = WindowsNative.openCurrentThreadToken(arena, TOKEN_ADJUST_PRIVILEGES);
                if (WindowsNative.isNull(token)) return false;
            }
            try {
                MemorySegment luid = arena.allocate(8, 4);
                if (!WindowsNative.lookupPrivilegeValue(arena, SE_DEBUG_NAME, luid)) return false;
                MemorySegment privileges = arena.allocate(16, 4);
                privileges.set(ValueLayout.JAVA_INT, 0, 1);
                MemorySegment.copy(luid, 0, privileges, 4, 8);
                privileges.set(ValueLayout.JAVA_INT, 12, SE_PRIVILEGE_ENABLED);
                return WindowsNative.adjustTokenPrivileges(token, privileges, 16);
            } finally {
                WindowsNative.closeHandle(token);
            }
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean checkBitness(MemorySegment target) {
        try (Arena arena = Arena.ofConfined()) {
            Boolean targetWow64 = WindowsNative.wow64(arena, target);
            if (targetWow64 == null) return true;
            if (IS_64BIT) return !targetWow64;
            Boolean selfWow64 = WindowsNative.wow64(arena, WindowsNative.currentProcess());
            return selfWow64 == null || selfWow64.equals(targetWow64);
        }
    }

    private void injectThread(String pipeName, String[] args) throws IOException {
        byte[] stub = AttachStub.getStubCode();
        MemorySegment remoteCode = WindowsNative.virtualAllocEx(process, stub.length, WindowsNative.PAGE_EXECUTE_READWRITE);
        if (WindowsNative.isNull(remoteCode)) {
            throw new IOException("VirtualAllocEx(code) failed (error=" + WindowsNative.lastError() + ")");
        }
        MemorySegment remoteData = MemorySegment.NULL;
        MemorySegment thread = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment localCode = arena.allocate(stub.length, 1);
            MemorySegment.copy(MemorySegment.ofArray(stub), 0, localCode, 0, stub.length);
            if (!WindowsNative.writeProcessMemory(process, remoteCode, localCode, stub.length)) {
                throw new IOException("WriteProcessMemory(code) failed (error=" + WindowsNative.lastError() + ")");
            }

            byte[] dataBlock = createDataBlock(arena, pipeName, args);
            remoteData = WindowsNative.virtualAllocEx(process, dataBlock.length, WindowsNative.PAGE_READWRITE);
            if (WindowsNative.isNull(remoteData)) {
                throw new IOException("VirtualAllocEx(data) failed (error=" + WindowsNative.lastError() + ")");
            }
            MemorySegment localData = arena.allocate(dataBlock.length, 1);
            MemorySegment.copy(MemorySegment.ofArray(dataBlock), 0, localData, 0, dataBlock.length);
            if (!WindowsNative.writeProcessMemory(process, remoteData, localData, dataBlock.length)) {
                throw new IOException("WriteProcessMemory(data) failed (error=" + WindowsNative.lastError() + ")");
            }

            thread = WindowsNative.createRemoteThread(process, remoteCode, remoteData);
            if (WindowsNative.isNull(thread)) {
                throw new IOException("CreateRemoteThread failed (error=" + WindowsNative.lastError() + ")");
            }
            int wait = WindowsNative.waitForSingleObject(thread, INFINITE);
            if (wait != WAIT_OBJECT_0) throw new IOException("WaitForSingleObject failed (result=" + wait + ")");
            int exitCode = WindowsNative.getExitCodeThread(arena, thread);
            if (exitCode != 0) throw new IOException(threadError(exitCode));
        } finally {
            WindowsNative.closeHandle(thread);
            if (!WindowsNative.isNull(remoteData)) WindowsNative.virtualFreeEx(process, remoteData);
            WindowsNative.virtualFreeEx(process, remoteCode);
        }
    }

    private byte[] createDataBlock(Arena arena, String pipeName, String[] args) throws IOException {
        MemorySegment kernel32 = WindowsNative.getModuleHandle(arena, "kernel32.dll");
        if (WindowsNative.isNull(kernel32)) kernel32 = WindowsNative.getModuleHandle(arena, "kernel32");
        if (WindowsNative.isNull(kernel32)) throw new IOException("GetModuleHandle(kernel32) failed");
        MemorySegment getModuleHandle = WindowsNative.getProcAddress(arena, kernel32, "GetModuleHandleA");
        MemorySegment getProcAddress = WindowsNative.getProcAddress(arena, kernel32, "GetProcAddress");
        if (WindowsNative.isNull(getModuleHandle) || WindowsNative.isNull(getProcAddress)) {
            throw new IOException("Unable to resolve Kernel32 attach helper functions");
        }

        byte[] data = new byte[DATA_BLOCK_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_GET_MODULE_HANDLE, getModuleHandle.address());
        buffer.putLong(OFF_GET_PROC_ADDRESS, getProcAddress.address());
        putCString(data, OFF_JVM_LIB, 16, "jvm.dll");
        putCString(data, OFF_FUNC1, 32, "JVM_EnqueueOperation");
        putCString(data, OFF_FUNC2, 32, "JVM_EnqueueOperation");
        if (args.length > 0) putCString(data, OFF_CMD, 16, args[0]);
        if (args.length > 1) putCString(data, OFF_ARG0, 1024, args[1]);
        if (args.length > 2) putCString(data, OFF_ARG1, 1024, args[2]);
        if (args.length > 3) putCString(data, OFF_ARG2, 1024, args[3]);
        putCString(data, OFF_PIPE, 256, pipeName);
        return data;
    }

    private String readResponse(MemorySegment pipe) throws IOException {
        final IOException[] failure = { null };
        final String[] response = { null };
        Thread reader = Thread.ofPlatform().name("featurecreep-attach-pipe").start(() -> {
            try (Arena arena = Arena.ofConfined()) {
                if (!WindowsNative.connectNamedPipe(pipe)) {
                    int error = WindowsNative.lastError();
                    if (error != WindowsNative.ERROR_PIPE_CONNECTED) {
                        failure[0] = new IOException("ConnectNamedPipe failed (error=" + error + ")");
                        return;
                    }
                }
                MemorySegment bytes = arena.allocate(8192, 1);
                int count = WindowsNative.readFile(arena, pipe, bytes, 8191);
                if (count < 0) {
                    failure[0] = new IOException("ReadFile failed (error=" + WindowsNative.lastError() + ")");
                    return;
                }
                byte[] result = new byte[count];
                if (count > 0) MemorySegment.copy(bytes, 0, MemorySegment.ofArray(result), 0, count);
                response[0] = new String(result, StandardCharsets.UTF_8).trim();
            } catch (RuntimeException ex) {
                failure[0] = new IOException("Named pipe response failed", ex);
            }
        });
        try {
            reader.join(5000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for target JVM response", ex);
        }
        if (reader.isAlive()) throw new IOException("Timed out waiting for target JVM named-pipe response");
        if (failure[0] != null) throw failure[0];
        return response[0] == null ? "" : response[0];
    }

    private static void validateAgentResponse(String response) throws IOException {
        if (response == null || response.isBlank()) return;
        String normalized = response.trim();
        int index = normalized.lastIndexOf("return code: ");
        if (index >= 0) {
            String number = normalized.substring(index + "return code: ".length()).split("\\s+", 2)[0];
            try {
                int code = Integer.parseInt(number);
                if (code != 0) throw new IOException(agentError(code));
                return;
            } catch (NumberFormatException ignored) {
                // Fall through to generic response handling.
            }
        }
        if (normalized.matches("-?\\d+")) {
            int code = Integer.parseInt(normalized);
            if (code != 0) throw new IOException(agentError(code));
            return;
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("error") || lower.contains("failed")) {
            throw new IOException("Agent loading failed: " + normalized);
        }
    }

    private static String agentError(int code) {
        return switch (code) {
            case -4 -> "Insufficient memory while loading agent";
            case 100 -> "Agent JAR not found or missing Agent-Class";
            case 101 -> "Unable to add agent JAR to target class path";
            case 102 -> "Agent JAR loaded but agentmain failed to initialize";
            default -> "Failed to load agent, error code " + code;
        };
    }

    private static String threadError(int code) {
        return switch (code) {
            case 1 -> "Remote attach operation not supported";
            case 2 -> "Remote attach operation ran out of memory";
            case 100 -> "JVM_EnqueueOperation not found";
            case 101 -> "Failed to load jvm.dll";
            case 102 -> "Failed to resolve JVM_EnqueueOperation";
            case 103 -> "Failed to enqueue attach operation";
            default -> "Remote attach stub failed with error code " + code;
        };
    }

    private static void putCString(byte[] target, int offset, int capacity, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, capacity - 1);
        System.arraycopy(bytes, 0, target, offset, count);
        target[offset + count] = 0;
    }

    public void detach() {
        MemorySegment old = process;
        process = MemorySegment.NULL;
        WindowsNative.closeHandle(old);
    }

    @Override
    public void close() {
        detach();
    }
}
