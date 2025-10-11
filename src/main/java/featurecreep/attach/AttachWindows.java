package featurecreep.attach;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.ptr.IntByReference;

public class AttachWindows {
    // Windows constants
    private static final HANDLE INVALID_HANDLE_VALUE = new HANDLE(Pointer.createConstant(-1));
    private static final int PIPE_ACCESS_INBOUND = 0x00000001;
    private static final int PIPE_TYPE_BYTE = 0x00000000;
    private static final int PIPE_READMODE_BYTE = 0x00000000;
    private static final int PIPE_WAIT = 0x00000000;
    private static final int WAIT_OBJECT_0 = 0;
    private static final int INFINITE = 0xFFFFFFFF;
    private static final int NMPWAIT_USE_DEFAULT_WAIT = 0x00000000;
    private static final boolean IS_64BIT = System.getProperty("os.arch").contains("64");
    // Oracle's DataBlock structure (matches the original exactly)
    @Structure.FieldOrder({
        "GetModuleHandleA", "GetProcAddress",
        "jvmLib", "func1", "func2", "cmd",
        "arg0", "arg1", "arg2", "pipename"
    })
    public static class DataBlock extends Structure {
        public Pointer GetModuleHandleA;
        public Pointer GetProcAddress;
        public byte[] jvmLib = new byte[16];    // MAX_LIBNAME_LENGTH
        public byte[] func1 = new byte[32];     // MAX_FUNC_LENGTH
        public byte[] func2 = new byte[32];     // MAX_FUNC_LENGTH
        public byte[] cmd = new byte[16];       // MAX_CMD_LENGTH
        // Flattened args array (3 args * 1024 bytes each)
        public byte[] arg0 = new byte[1024];    // MAX_ARG_LENGTH
        public byte[] arg1 = new byte[1024];
        public byte[] arg2 = new byte[1024];
        public byte[] pipename = new byte[256]; // MAX_PIPE_NAME_LENGTH
        public DataBlock() {
            super();
        }
        public void setArg(int index, String arg) {
            if (arg == null) arg = "";
            byte[] argBytes = arg.getBytes();
            int len = Math.min(argBytes.length, 1023); // Leave space for null terminator

            byte[] targetArray;
            switch (index) {
                case 0: targetArray = arg0; break;
                case 1: targetArray = arg1; break;
                case 2: targetArray = arg2; break;
                default: return;
            }

            // Clear the array first
            for (int i = 0; i < targetArray.length; i++) {
                targetArray[i] = 0;
            }

            // Copy the argument
            System.arraycopy(argBytes, 0, targetArray, 0, len);
            targetArray[len] = 0; // Null terminate
        }
    }
    // Embedded AttachStub class
    public static class AttachStub {
        private static final int[] ATTACH_STUB_CODE_AMD64 = {
            0x48, 0x89, 0x5C, 0x24, 0x08, 0x57, 0x48, 0x83, 0xEC, 0x30, 0x48, 0x8B,
            0xD9, 0x48, 0x83, 0xC1, 0x10, 0xFF, 0x13, 0x48, 0x8B, 0xF8, 0x48, 0x85,
            0xC0, 0x75, 0x07, 0xB8, 0xC8, 0x00, 0x00, 0x00, 0xEB, 0x59, 0x48, 0x8D,
            0x53, 0x20, 0x48, 0x8B, 0xCF, 0xFF, 0x53, 0x08, 0x4C, 0x8B, 0xD0, 0x48,
            0x85, 0xC0, 0x75, 0x19, 0x48, 0x8D, 0x53, 0x40, 0x48, 0x8B, 0xCF, 0xFF,
            0x53, 0x08, 0x4C, 0x8B, 0xD0, 0x48, 0x85, 0xC0, 0x75, 0x07, 0xB8, 0xC9,
            0x00, 0x00, 0x00, 0xEB, 0x2E, 0x48, 0x8D, 0x4B, 0x60, 0x80, 0x39, 0x00,
            0x75, 0x04, 0x33, 0xC0, 0xEB, 0x21, 0x48, 0x8D, 0x83, 0x70, 0x0C, 0x00,
            0x00, 0x4C, 0x8D, 0x8B, 0x70, 0x08, 0x00, 0x00, 0x48, 0x89, 0x44, 0x24,
            0x20, 0x4C, 0x8D, 0x83, 0x70, 0x04, 0x00, 0x00, 0x48, 0x8D, 0x53, 0x70,
            0x41, 0xFF, 0xD2, 0x48, 0x8B, 0x5C, 0x24, 0x40, 0x48, 0x83, 0xC4, 0x30,
            0x5F, 0xC3, 0xCC, 0xCC
        };
        
        


        private static final int[] ATTACH_STUB_CODE_ARM = {
            0xF3, 0x53, 0xBE, 0xA9, 0xFE, 0x0B, 0x00, 0xF9, 0xF3, 0x03, 0x00, 0xAA,
            0x68, 0x02, 0x40, 0xF9, 0x60, 0x42, 0x00, 0x91, 0x00, 0x01, 0x3F, 0xD6,
            0xF4, 0x03, 0x00, 0xAA, 0x74, 0x00, 0x00, 0xB5, 0x00, 0x19, 0x80, 0x52,
            0x19, 0x00, 0x00, 0x14, 0x68, 0x06, 0x40, 0xF9, 0x61, 0x82, 0x00, 0x91,
            0xE0, 0x03, 0x14, 0xAA, 0x00, 0x01, 0x3F, 0xD6, 0xE9, 0x03, 0x00, 0xAA,
            0x29, 0x01, 0x00, 0xB5, 0x68, 0x06, 0x40, 0xF9, 0x61, 0x02, 0x01, 0x91,
            0xE0, 0x03, 0x14, 0xAA, 0x00, 0x01, 0x3F, 0xD6, 0xE9, 0x03, 0x00, 0xAA,
            0x69, 0x00, 0x00, 0xB5, 0x20, 0x19, 0x80, 0x52, 0x0B, 0x00, 0x00, 0x14,
            0x68, 0x82, 0xC1, 0x39, 0x68, 0x00, 0x00, 0x35, 0x00, 0x00, 0x80, 0x52,
            0x07, 0x00, 0x00, 0x14, 0x64, 0xC2, 0x31, 0x91, 0x63, 0xC2, 0x21, 0x91,
            0x62, 0xC2, 0x11, 0x91, 0x61, 0xC2, 0x01, 0x91, 0x60, 0x82, 0x01, 0x91,
            0x20, 0x01, 0x3F, 0xD6, 0xFE, 0x0B, 0x40, 0xF9, 0xF3, 0x53, 0xC2, 0xA8,
            0xC0, 0x03, 0x5F, 0xD6, 0x00, 0x00, 0x00, 0x00
        };//Thanks to lexmanos for providing this
        
        //TODO Itanium if there was java 8 for itanium on windows


        
        
        
        
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
            return isArm() ? ATTACH_STUB_CODE_ARM.length
                           : ATTACH_STUB_CODE_AMD64.length;
        }

        private static boolean isArm() {
            // Common ARM identifiers: aarch64, arm, arm64
            return ARCH.contains("arm")||ARCH.contains("aarch");
        }
    }

    public interface Kernel32Extended extends Library {
        Kernel32Extended INSTANCE = Native.load("kernel32", Kernel32Extended.class);
        HANDLE CreateNamedPipeA(String lpName, int dwOpenMode, int dwPipeMode,
                              int nMaxInstances, int nOutBufferSize, int nInBufferSize,
                              int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes);
        boolean ConnectNamedPipe(HANDLE hNamedPipe, Pointer lpOverlapped);
        HANDLE CreateRemoteThread(HANDLE hProcess, SECURITY_ATTRIBUTES lpThreadAttributes,
                                 SIZE_T dwStackSize, Pointer lpStartAddress, Pointer lpParameter,
                                 int dwCreationFlags, IntByReference lpThreadId);
        Pointer VirtualAllocEx(HANDLE hProcess, Pointer lpAddress, SIZE_T dwSize,
                              int flAllocationType, int flProtect);
        boolean VirtualFreeEx(HANDLE hProcess, Pointer lpAddress, SIZE_T dwSize, int dwFreeType);
        boolean WriteProcessMemory(HANDLE hProcess, Pointer lpBaseAddress, Pointer lpBuffer,
                                  SIZE_T nSize, SIZE_T lpNumberOfBytesWritten);
        boolean GetExitCodeThread(HANDLE hThread, IntByReference lpExitCode);
        boolean DuplicateHandle(HANDLE hSourceProcessHandle, HANDLE hSourceHandle,
                               HANDLE hTargetProcessHandle, HANDLEByReference lpTargetHandle,
                               int dwDesiredAccess, boolean bInheritHandle, int dwOptions);
        HMODULE GetModuleHandleA(String lpModuleName);
        Pointer GetProcAddress(HMODULE hModule, String lpProcName);
        int GetCurrentProcessId();
        int GetTickCount();
        HANDLE GetCurrentProcess();
        HANDLE OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);
        boolean CloseHandle(HANDLE hObject);
        int WaitForSingleObject(HANDLE hHandle, int dwMilliseconds);
        boolean ReadFile(HANDLE hFile, byte[] lpBuffer, int nNumberOfBytesToRead,
                        IntByReference lpNumberOfBytesRead, Pointer lpOverlapped);
        int GetLastError();
        boolean IsWow64Process(HANDLE hProcess, IntByReference Wow64Process);
        // Privilege functions
        boolean OpenThreadToken(HANDLE ThreadHandle, int DesiredAccess, boolean OpenAsSelf, HANDLEByReference TokenHandle);
        boolean ImpersonateSelf(int ImpersonationLevel);
        boolean LookupPrivilegeValueA(String lpSystemName, String lpName, LUID lpLuid);
        boolean AdjustTokenPrivileges(HANDLE TokenHandle, boolean DisableAllPrivileges,
                                    TOKEN_PRIVILEGES NewState, int BufferLength,
                                    Pointer PreviousState, IntByReference ReturnLength);
    }

    // Additional structures for privilege escalation
    @Structure.FieldOrder({"LowPart", "HighPart"})
    public static class LUID extends Structure {
        public int LowPart;
        public int HighPart;
    }

    @Structure.FieldOrder({"Luid", "Attributes"})
    public static class LUID_AND_ATTRIBUTES extends Structure {
        public LUID Luid;
        public int Attributes;
    }

    @Structure.FieldOrder({"PrivilegeCount", "Privileges"})
    public static class TOKEN_PRIVILEGES extends Structure {
        public int PrivilegeCount;
        public LUID_AND_ATTRIBUTES[] Privileges = new LUID_AND_ATTRIBUTES[1];
        public TOKEN_PRIVILEGES() {
            super();
            Privileges[0] = new LUID_AND_ATTRIBUTES();
        }
    }

    private static final int TOKEN_ADJUST_PRIVILEGES = 0x0020;
    private static final int SE_PRIVILEGE_ENABLED = 0x00000002;
    private static final int SecurityImpersonation = 2;
    private static final String SE_DEBUG_NAME = "SeDebugPrivilege";

    private HANDLE hProcess;
    private int targetPid;

    public AttachWindows(int pid) throws IOException {
        this.targetPid = pid;
        this.hProcess = openProcess(pid);
        System.out.println("Successfully attached to process " + pid);
    }

    private HANDLE openProcess(int pid) throws IOException {
        System.out.println("Opening process " + pid + "...");
        HANDLE process = null;
        if (pid == Kernel32Extended.INSTANCE.GetCurrentProcessId()) {
            System.out.println("Attaching to self - getting current process handle");
            HANDLE currentProcess = Kernel32Extended.INSTANCE.GetCurrentProcess();
            HANDLEByReference duplicatedHandle = new HANDLEByReference();
            if (Kernel32Extended.INSTANCE.DuplicateHandle(currentProcess, currentProcess,
                    currentProcess, duplicatedHandle, WinNT.PROCESS_ALL_ACCESS, false, 0)) {
                process = duplicatedHandle.getValue();
                System.out.println("Successfully duplicated process handle");
            } else {
                System.out.println("Failed to duplicate handle, error: " + Kernel32Extended.INSTANCE.GetLastError());
            }
        }
        if (process == null) {
            process = Kernel32Extended.INSTANCE.OpenProcess(WinNT.PROCESS_ALL_ACCESS, false, pid);
            if (process == null && Kernel32Extended.INSTANCE.GetLastError() == WinError.ERROR_ACCESS_DENIED) {
                System.out.println("Access denied, trying to enable debug privileges...");
                if (enableDebugPrivileges()) {
                    process = Kernel32Extended.INSTANCE.OpenProcess(WinNT.PROCESS_ALL_ACCESS, false, pid);
                }
            }
        }
        if (process == null) {
            int error = Kernel32Extended.INSTANCE.GetLastError();
            throw new IOException("Could not open process " + pid + ", error: " + error);
        }
        if (!checkBitness(process)) {
            Kernel32Extended.INSTANCE.CloseHandle(process);
            throw new IOException("Architecture mismatch between current process and target JVM");
        }
        return process;
    }

    private boolean enableDebugPrivileges() {
        try {
            HANDLEByReference hToken = new HANDLEByReference();
            if (!Kernel32Extended.INSTANCE.OpenThreadToken(
                    Kernel32.INSTANCE.GetCurrentThread(), TOKEN_ADJUST_PRIVILEGES, false, hToken)) {
                if (!Kernel32Extended.INSTANCE.ImpersonateSelf(SecurityImpersonation) ||
                    !Kernel32Extended.INSTANCE.OpenThreadToken(
                            Kernel32.INSTANCE.GetCurrentThread(), TOKEN_ADJUST_PRIVILEGES, false, hToken)) {
                    return false;
                }
            }
            LUID luid = new LUID();
            if (!Kernel32Extended.INSTANCE.LookupPrivilegeValueA(null, SE_DEBUG_NAME, luid)) {
                Kernel32Extended.INSTANCE.CloseHandle(hToken.getValue());
                return false;
            }
            TOKEN_PRIVILEGES tp = new TOKEN_PRIVILEGES();
            tp.PrivilegeCount = 1;
            tp.Privileges[0].Luid = luid;
            tp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
            boolean success = Kernel32Extended.INSTANCE.AdjustTokenPrivileges(
                    hToken.getValue(), false, tp, tp.size(), null, null);
            Kernel32Extended.INSTANCE.CloseHandle(hToken.getValue());
            return success;
        } catch (Exception e) {
            System.out.println("Failed to enable debug privileges: " + e.getMessage());
            return false;
        }
    }

    private boolean checkBitness(HANDLE hProcess) {
        try {
            if (IS_64BIT) {
                IntByReference targetWow64 = new IntByReference();
                if (Kernel32Extended.INSTANCE.IsWow64Process(hProcess, targetWow64) &&
                    targetWow64.getValue() != 0) {
                    System.out.println("Cannot attach 64-bit process to 32-bit JVM");
                    return false;
                }
            } else {
                IntByReference thisWow64 = new IntByReference();
                IntByReference targetWow64 = new IntByReference();
                if (Kernel32Extended.INSTANCE.IsWow64Process(Kernel32Extended.INSTANCE.GetCurrentProcess(), thisWow64) &&
                    Kernel32Extended.INSTANCE.IsWow64Process(hProcess, targetWow64)) {
                    if (thisWow64.getValue() != targetWow64.getValue()) {
                        System.out.println("Cannot attach 32-bit process to 64-bit JVM");
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.out.println("Warning: Could not check process bitness: " + e.getMessage());
            return true; // Assume compatible if we can't check
        }
    }

    public void loadAgent(String agentPath, String options) throws IOException {
        if (!Files.exists(Paths.get(agentPath))) {
            throw new IOException("Agent file not found: " + agentPath);
        }
        
        System.out.println("Loading agent using JVM_EnqueueOperation...");
        
        // Create pipe name
        String pipeName = String.format("\\\\.\\pipe\\javatool%d", Kernel32Extended.INSTANCE.GetTickCount());
        System.out.println("Creating named pipe: " + pipeName);
        
        // Create named pipe
        HANDLE hPipe = Kernel32Extended.INSTANCE.CreateNamedPipeA(
                pipeName,
                PIPE_ACCESS_INBOUND,
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
                1, 4096, 8192, NMPWAIT_USE_DEFAULT_WAIT, null);
        
        if (hPipe == null || hPipe.equals(INVALID_HANDLE_VALUE)) {
            throw new IOException("Could not create pipe, error: " + Kernel32Extended.INSTANCE.GetLastError());
        }
        
        try {
            // For JAR files, we need to load the instrument library first
            String instrumentArgs = agentPath;
            if (options != null && !options.isEmpty()) {
                instrumentArgs = instrumentArgs + "=" + options;
            }
            
            // Prepare arguments for loading the instrument library
            String[] args = {
                "load",
                "instrument",
                "false",
                instrumentArgs
            };

            String injectResult = injectThread(pipeName, args);
            
            if (injectResult == null) {
                throw new IOException("Thread injection returned null");
            }
            
            if (!injectResult.toLowerCase().contains("successfully")) {
                throw new IOException("Thread injection failed: " + injectResult);
            }

            System.out.println("Connected to remote process");
            System.out.print("Response: ");
            System.out.flush();
            
            String response = readResponse(hPipe);
            System.out.println();

            // Check if the response contains success indicators
            if (response.contains("✅ Agent attached successfully") || 
                response.contains("Agent attached successfully") ||
                (response.contains("0") && !response.contains("error"))) {
                System.out.println("Agent loaded successfully!");
                return;
            }
            
            // Parse the response for error codes
            if (response.startsWith("return code: ")) {
                int returnCode = Integer.parseInt(response.substring("return code: ".length()).trim());
                if (returnCode != 0) {
                    handleAgentLoadError(returnCode);
                }
            } else if (response.matches("^-?\\d+$")) {
                // If response is just a number
                int returnCode = Integer.parseInt(response.trim());
                if (returnCode != 0) {
                    handleAgentLoadError(returnCode);
                }
            } else if (response.contains("error") || response.contains("failed")) {
                throw new IOException("Agent loading failed: " + response);
            }
            
            // If we get here, assume success
            System.out.println("Agent loaded successfully!");
            
        } finally {
            Kernel32Extended.INSTANCE.CloseHandle(hPipe);
        }
    }

    private void handleAgentLoadError(int returnCode) throws IOException {
        String errorMsg;
        switch (returnCode) {
            case -4:  // JNI_ENOMEM
                errorMsg = "Insufficient memory";
                break;
            case 100: // ATTACH_ERROR_BADJAR
                errorMsg = "Agent JAR not found or no Agent-Class attribute";
                break;
            case 101: // ATTACH_ERROR_NOTONCP
                errorMsg = "Unable to add JAR file to system class path";
                break;
            case 102: // ATTACH_ERROR_STARTFAIL
                errorMsg = "Agent JAR loaded but agent failed to initialize";
                break;
            default:
                errorMsg = "Failed to load agent - error code: " + returnCode;
        }
        throw new IOException(errorMsg);
    }

    private String injectThread(String pipeName, String[] args) {
        System.out.println("Debug: Starting thread injection");
        
        // Validate inputs
        if (pipeName == null || args == null) {
            return "Invalid arguments: pipeName or args is null";
        }
        
        if (hProcess == null) {
            return "Process handle is null";
        }
        
        byte[] stubCode = AttachStub.getStubCode();
        System.out.println("Debug: Stub code size: " + stubCode.length);

        // Allocate and write code memory
        Pointer code = Kernel32Extended.INSTANCE.VirtualAllocEx(hProcess, null,
                new SIZE_T(stubCode.length), WinNT.MEM_COMMIT, WinNT.PAGE_EXECUTE_READWRITE);
        if (code == null) {
            System.out.println("Debug: Failed to allocate code memory");
            return "Could not allocate code memory in target process, error: " +
                   Kernel32Extended.INSTANCE.GetLastError();
        }
        System.out.println("Debug: Allocated code memory at: " + code);

        // Allocate data memory
        Pointer data = allocateDataBlock(pipeName, args);
        if (data == null) {
            System.out.println("Debug: Failed to allocate data memory");
            Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, code, new SIZE_T(0), WinNT.MEM_RELEASE);
            return "Failed to allocate data block memory";
        }
        System.out.println("Debug: Allocated data memory at: " + data);

        // Write stub code to target process
        Memory codeMemory = new Memory(stubCode.length);
        codeMemory.write(0, stubCode, 0, stubCode.length);
        if (!Kernel32Extended.INSTANCE.WriteProcessMemory(hProcess, code,
                codeMemory, new SIZE_T(stubCode.length), null)) {
            String errorMsg = "Debug: Could not write code to target process, error: " +
                             Kernel32Extended.INSTANCE.GetLastError();
            System.out.println(errorMsg);
            Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, code, new SIZE_T(0), WinNT.MEM_RELEASE);
            Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, data, new SIZE_T(0), WinNT.MEM_RELEASE);
            return errorMsg;
        }
        System.out.println("Debug: Wrote stub code to target process");

        // Create remote thread
        System.out.println("Debug: Creating remote thread...");
        HANDLE hThread = Kernel32Extended.INSTANCE.CreateRemoteThread(hProcess, null,
                new SIZE_T(0), code, data, 0, null);
        if (hThread == null) {
            String errorMsg = "Debug: Could not create remote thread, error: " +
                            Kernel32Extended.INSTANCE.GetLastError();
            System.out.println(errorMsg);
            Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, code, new SIZE_T(0), WinNT.MEM_RELEASE);
            Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, data, new SIZE_T(0), WinNT.MEM_RELEASE);
            return errorMsg;
        }
        System.out.println("Debug: Remote thread created");

        // Wait for thread to complete
        int waitResult = Kernel32Extended.INSTANCE.WaitForSingleObject(hThread, INFINITE);
        
        // Initialize with a default message
        String resultMessage = "Thread execution completed";
        
        if (waitResult == WAIT_OBJECT_0) {
            System.out.println("Debug: Remote thread completed");
            IntByReference exitCode = new IntByReference();
            Kernel32Extended.INSTANCE.GetExitCodeThread(hThread, exitCode);
            System.out.println("Debug: Remote thread exit code: " + exitCode.getValue());
            if (exitCode.getValue() != 0) {
                resultMessage = handleThreadExitCode(exitCode.getValue());
                System.out.println("Debug: " + resultMessage);
            } else {
                resultMessage = "Remote thread completed successfully";
            }
        } else {
            resultMessage = "Debug: Remote thread wait failed, result: " + waitResult;
            System.out.println(resultMessage);
        }

        Kernel32Extended.INSTANCE.CloseHandle(hThread);
        // Cleanup
        Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, code, new SIZE_T(0), WinNT.MEM_RELEASE);
        Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, data, new SIZE_T(0), WinNT.MEM_RELEASE);
        
        return resultMessage; // Always return a non-null value
    }


    private Pointer allocateDataBlock(String pipeName, String[] args) {
        System.out.println("Creating DataBlock structure...");
        DataBlock dataBlock = new DataBlock();
        
        // Set function pointers
        HMODULE kernel32 = Kernel32Extended.INSTANCE.GetModuleHandleA("kernel32");
        dataBlock.GetModuleHandleA = Kernel32Extended.INSTANCE.GetProcAddress(kernel32, "GetModuleHandleA");
        dataBlock.GetProcAddress = Kernel32Extended.INSTANCE.GetProcAddress(kernel32, "GetProcAddress");
        System.out.println("GetModuleHandleA: " + dataBlock.GetModuleHandleA);
        System.out.println("GetProcAddress: " + dataBlock.GetProcAddress);
        
        // Set library and function names
        // Try with full dll name
        System.arraycopy("jvm.dll".getBytes(), 0, dataBlock.jvmLib, 0, 7);
        
        // For 64-bit, both function names should be the same (no decoration)
        System.arraycopy("JVM_EnqueueOperation".getBytes(), 0, dataBlock.func1, 0, 20);
        System.arraycopy("JVM_EnqueueOperation".getBytes(), 0, dataBlock.func2, 0, 20);
        
        // Set command and arguments
        if (args.length > 0) {
            System.arraycopy(args[0].getBytes(), 0, dataBlock.cmd, 0,
                           Math.min(args[0].length(), 15));

            for (int i = 1; i < Math.min(args.length, 4); i++) {
                dataBlock.setArg(i - 1, args[i]);
                System.out.println("Set arg[" + (i-1) + "] = " + args[i]);
            }
        }
        
        // Set pipe name
        System.arraycopy(pipeName.getBytes(), 0, dataBlock.pipename, 0,
                       Math.min(pipeName.length(), 255));
        
        System.out.println("DataBlock structure size: " + dataBlock.size());
        
        // Allocate memory in remote process
        Pointer remoteData = Kernel32Extended.INSTANCE.VirtualAllocEx(hProcess, null,
                new SIZE_T(dataBlock.size()), WinNT.MEM_COMMIT, WinNT.PAGE_READWRITE);
        
        if (remoteData != null) {
            // Write the data structure
            dataBlock.write();
            Memory dataMemory = new Memory(dataBlock.size());
            byte[] structBytes = dataBlock.getPointer().getByteArray(0, dataBlock.size());
            dataMemory.write(0, structBytes, 0, structBytes.length);
            
            if (!Kernel32Extended.INSTANCE.WriteProcessMemory(hProcess, remoteData,
                    dataMemory, new SIZE_T(dataBlock.size()), null)) {
                System.out.println("Failed to write DataBlock to target process, error: " +
                                 Kernel32Extended.INSTANCE.GetLastError());
                Kernel32Extended.INSTANCE.VirtualFreeEx(hProcess, remoteData, new SIZE_T(0), WinNT.MEM_RELEASE);
                return null;
            }
            System.out.println("Wrote DataBlock to target process");
        } else {
            System.out.println("Failed to allocate DataBlock memory in target process, error: " +
                             Kernel32Extended.INSTANCE.GetLastError());
        }
        
        return remoteData;
    }

    private String handleThreadExitCode(int exitCode) {
        switch (exitCode) {
            case 0:
                return "Success";
            case 1:
                return "Operation not supported";
            case 2:
                return "Out of memory";
            case 100:
                return "JVM_EnqueueOperation not found";
            case 101:
                return "Failed to load jvm.dll";
            case 102:
                return "Failed to get JVM_EnqueueOperation address";
            case 103:
                return "Failed to enqueue operation";
            // Add more error codes as needed
            default:
                return "Unknown error code: " + exitCode;
        }
    }

    private String readResponse(HANDLE hPipe) throws IOException {
        System.out.println("Waiting for pipe connection...");
        final boolean[] connected = {false};
        final IOException[] connectionError = {null};
        final String[] response = {null};

        Thread connectionThread = new Thread(() -> {
            try {
                if (!Kernel32Extended.INSTANCE.ConnectNamedPipe(hPipe, null)) {
                    int error = Kernel32Extended.INSTANCE.GetLastError();
                    if (error != WinError.ERROR_PIPE_CONNECTED) {
                        connectionError[0] = new IOException("Failed to connect to pipe, error: " + error);
                        return;
                    }
                }
                connected[0] = true;
                byte[] buf = new byte[8192];
                IntByReference bytesRead = new IntByReference();
                if (!Kernel32Extended.INSTANCE.ReadFile(hPipe, buf, buf.length - 1, bytesRead, null)) {
                    int error = Kernel32Extended.INSTANCE.GetLastError();
                    if (error != WinError.ERROR_BROKEN_PIPE) {
                        connectionError[0] = new IOException("Error reading response: " + error);
                        return;
                    }
                }
                response[0] = new String(buf, 0, bytesRead.getValue());
                System.out.print(response[0]);
            } catch (Exception e) {
                connectionError[0] = new IOException("Connection failed: " + e.getMessage());
            }
        });

        connectionThread.start();
        try {
            connectionThread.join(5000); // 5 second timeout
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection interrupted");
        }

        if (connectionError[0] != null) {
            throw connectionError[0];
        }

        if (!connected[0]) {
            String timeoutMessage = "Pipe connection timed out - this might mean JVM_EnqueueOperation is not available";
            System.out.println(timeoutMessage);
            return timeoutMessage;
        }

        return response[0] != null ? response[0] : "";
    }

    public void detach() {
        if (hProcess != null) {
            Kernel32Extended.INSTANCE.CloseHandle(hProcess);
            hProcess = null;
        }
    }


}