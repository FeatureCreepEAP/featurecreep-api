package featurecreep.attach.panama;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/** Minimal Kernel32/Advapi32 Java 25 FFM bindings for the Windows attach path. */
public final class WindowsNative {
    public static final int PROCESS_ALL_ACCESS = 0x001F0FFF;
    public static final int ERROR_ACCESS_DENIED = 5;
    public static final int ERROR_BROKEN_PIPE = 109;
    public static final int ERROR_PIPE_CONNECTED = 535;
    public static final int MEM_COMMIT = 0x1000;
    public static final int MEM_RELEASE = 0x8000;
    public static final int PAGE_EXECUTE_READWRITE = 0x40;
    public static final int PAGE_READWRITE = 0x04;

    private static final SymbolLookup KERNEL32 = library("kernel32.dll");
    private static final SymbolLookup ADVAPI32 = library("advapi32.dll");

    private static final MethodHandle CREATE_NAMED_PIPE_A = fn(KERNEL32, "CreateNamedPipeA",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle CONNECT_NAMED_PIPE = fn(KERNEL32, "ConnectNamedPipe",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CREATE_REMOTE_THREAD = fn(KERNEL32, "CreateRemoteThread",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, PanamaSupport.C_SIZE_T, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle VIRTUAL_ALLOC_EX = fn(KERNEL32, "VirtualAllocEx",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, PanamaSupport.C_SIZE_T, JAVA_INT, JAVA_INT));
    private static final MethodHandle VIRTUAL_FREE_EX = fn(KERNEL32, "VirtualFreeEx",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, PanamaSupport.C_SIZE_T, JAVA_INT));
    private static final MethodHandle WRITE_PROCESS_MEMORY = fn(KERNEL32, "WriteProcessMemory",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, PanamaSupport.C_SIZE_T, ADDRESS));
    private static final MethodHandle GET_EXIT_CODE_THREAD = fn(KERNEL32, "GetExitCodeThread",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GET_MODULE_HANDLE_A = fn(KERNEL32, "GetModuleHandleA",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle GET_PROC_ADDRESS = fn(KERNEL32, "GetProcAddress",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GET_CURRENT_PROCESS_ID = fn(KERNEL32, "GetCurrentProcessId",
            FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle GET_TICK_COUNT = fn(KERNEL32, "GetTickCount",
            FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle GET_CURRENT_PROCESS = fn(KERNEL32, "GetCurrentProcess",
            FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle GET_CURRENT_THREAD = fn(KERNEL32, "GetCurrentThread",
            FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle OPEN_PROCESS = fn(KERNEL32, "OpenProcess",
            FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle CLOSE_HANDLE = fn(KERNEL32, "CloseHandle",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle WAIT_FOR_SINGLE_OBJECT = fn(KERNEL32, "WaitForSingleObject",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle READ_FILE = fn(KERNEL32, "ReadFile",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GET_LAST_ERROR = fn(KERNEL32, "GetLastError", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle IS_WOW64_PROCESS = fn(KERNEL32, "IsWow64Process",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    private static final MethodHandle OPEN_THREAD_TOKEN = fn(ADVAPI32, "OpenThreadToken",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle IMPERSONATE_SELF = fn(ADVAPI32, "ImpersonateSelf",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final MethodHandle LOOKUP_PRIVILEGE_VALUE_A = fn(ADVAPI32, "LookupPrivilegeValueA",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle ADJUST_TOKEN_PRIVILEGES = fn(ADVAPI32, "AdjustTokenPrivileges",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    private WindowsNative() {}

    private static SymbolLookup library(String name) {
        try {
            return SymbolLookup.libraryLookup(name, Arena.global());
        } catch (IllegalArgumentException | UnsatisfiedLinkError ex) {
            return PanamaSupport.DEFAULT_LOOKUP;
        }
    }

    private static MethodHandle fn(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return PanamaSupport.downcall(lookup, name, descriptor);
    }

    public static MemorySegment createNamedPipe(Arena arena, String name, int openMode, int pipeMode,
            int maxInstances, int outSize, int inSize, int timeout) {
        return address(CREATE_NAMED_PIPE_A, arena.allocateFrom(name), openMode, pipeMode, maxInstances,
                outSize, inSize, timeout, MemorySegment.NULL);
    }

    public static boolean connectNamedPipe(MemorySegment pipe) {
        return integer(CONNECT_NAMED_PIPE, pipe, MemorySegment.NULL) != 0;
    }

    public static MemorySegment createRemoteThread(MemorySegment process, MemorySegment start, MemorySegment parameter) {
        return address(CREATE_REMOTE_THREAD, process, MemorySegment.NULL, sizeT(0), start, parameter, 0, MemorySegment.NULL);
    }

    public static MemorySegment virtualAllocEx(MemorySegment process, long bytes, int protect) {
        return address(VIRTUAL_ALLOC_EX, process, MemorySegment.NULL, sizeT(bytes), MEM_COMMIT, protect);
    }

    public static boolean virtualFreeEx(MemorySegment process, MemorySegment address) {
        return integer(VIRTUAL_FREE_EX, process, address, sizeT(0), MEM_RELEASE) != 0;
    }

    public static boolean writeProcessMemory(MemorySegment process, MemorySegment target, MemorySegment source, long bytes) {
        return integer(WRITE_PROCESS_MEMORY, process, target, source, sizeT(bytes), MemorySegment.NULL) != 0;
    }

    public static int getExitCodeThread(Arena arena, MemorySegment thread) {
        MemorySegment result = arena.allocate(JAVA_INT);
        if (integer(GET_EXIT_CODE_THREAD, thread, result) == 0) return -1;
        return result.get(JAVA_INT, 0);
    }

    public static MemorySegment getModuleHandle(Arena arena, String module) {
        return address(GET_MODULE_HANDLE_A, arena.allocateFrom(module));
    }

    public static MemorySegment getProcAddress(Arena arena, MemorySegment module, String symbol) {
        return address(GET_PROC_ADDRESS, module, arena.allocateFrom(symbol));
    }

    public static int currentProcessId() { return integer(GET_CURRENT_PROCESS_ID); }
    public static int tickCount() { return integer(GET_TICK_COUNT); }
    public static MemorySegment currentProcess() { return address(GET_CURRENT_PROCESS); }
    public static MemorySegment currentThread() { return address(GET_CURRENT_THREAD); }

    public static MemorySegment openProcess(int pid) {
        return address(OPEN_PROCESS, PROCESS_ALL_ACCESS, 0, pid);
    }

    public static void closeHandle(MemorySegment handle) {
        if (!isNull(handle) && handle.address() != -1L) integer(CLOSE_HANDLE, handle);
    }

    public static int waitForSingleObject(MemorySegment handle, int millis) {
        return integer(WAIT_FOR_SINGLE_OBJECT, handle, millis);
    }

    public static int readFile(Arena arena, MemorySegment file, MemorySegment buffer, int bytes) {
        MemorySegment count = arena.allocate(JAVA_INT);
        int ok = integer(READ_FILE, file, buffer, bytes, count, MemorySegment.NULL);
        int read = count.get(JAVA_INT, 0);
        if (ok == 0 && lastError() != ERROR_BROKEN_PIPE) return -1;
        return read;
    }

    public static int lastError() { return integer(GET_LAST_ERROR); }

    public static Boolean wow64(Arena arena, MemorySegment process) {
        MemorySegment value = arena.allocate(JAVA_INT);
        if (integer(IS_WOW64_PROCESS, process, value) == 0) return null;
        return value.get(JAVA_INT, 0) != 0;
    }

    public static MemorySegment openCurrentThreadToken(Arena arena, int access) {
        MemorySegment out = arena.allocate(ADDRESS);
        if (integer(OPEN_THREAD_TOKEN, currentThread(), access, 0, out) == 0) return MemorySegment.NULL;
        return out.get(ADDRESS, 0);
    }

    public static boolean impersonateSelf(int level) { return integer(IMPERSONATE_SELF, level) != 0; }

    public static boolean lookupPrivilegeValue(Arena arena, String name, MemorySegment luid) {
        return integer(LOOKUP_PRIVILEGE_VALUE_A, MemorySegment.NULL, arena.allocateFrom(name), luid) != 0;
    }

    public static boolean adjustTokenPrivileges(MemorySegment token, MemorySegment privileges, int bytes) {
        return integer(ADJUST_TOKEN_PRIVILEGES, token, 0, privileges, bytes, MemorySegment.NULL, MemorySegment.NULL) != 0;
    }

    public static boolean isNull(MemorySegment segment) {
        return segment == null || segment.equals(MemorySegment.NULL) || segment.address() == 0L;
    }

    private static Object sizeT(long value) {
        return PanamaSupport.C_SIZE_T.byteSize() == Integer.BYTES ? Math.toIntExact(value) : value;
    }

    private static int integer(MethodHandle handle, Object... args) {
        return PanamaSupport.invokeInt(handle, args);
    }

    private static MemorySegment address(MethodHandle handle, Object... args) {
        return PanamaSupport.invokeAddress(handle, args);
    }
}
