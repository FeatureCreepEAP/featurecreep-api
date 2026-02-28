package featurecreep.attach;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

public class AttachSolaris extends AttachUnix {
    private static int door_fd = -1;
    private static final int ATTACH_ERROR_BADVERSION = 101;
    private static final long DEFAULT_TIMEOUT = 10_000; // 10 sec
    private static final String tmpdir = "/tmp";
    
    /**
     * Default attach. You must call this before attaching an agent
     * @throws IOException
     */
    public static void attach() throws IOException {
        int pid = 0;
        try {
            pid = (int) ProcessHandle.current().pid();
        } catch (NumberFormatException x) {
            throw new IOException("Invalid process identifier");
        }

        String door_path = getDoorPath(pid);
        try {
            door_fd = openDoor(door_path);
        } catch (FileNotFoundException fnf1) {
            File f = createAttachFile(pid);
            try {
                sigquit(pid);

                final int delay_step = 100;
                final long timeout = attachTimeout();
                long time_spend = 0;
                long delay = 0;
                do {
                    delay += delay_step;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException x) { }
                    
                    try {
                        door_fd = openDoor(door_path);
                        break;
                    } catch (FileNotFoundException fnf2) {
                        // Continue trying
                    }

                    time_spend += delay;
                    if (time_spend > timeout/2 && door_fd == -1) {
                        sigquit(pid);
                    }
                } while (time_spend <= timeout);
                
                if (door_fd == -1) {
                    throw new IOException(
                        String.format("Unable to open door %s: " +
                          "target process %d doesn't respond within %dms " +
                          "or HotSpot VM not loaded", door_path, pid, time_spend));
                }
            } finally {
                f.delete();
            }
        }
        
        checkPermissions(door_path);
    }
    
    private static long attachTimeout() {
        return DEFAULT_TIMEOUT;
    }
    
    private static String getDoorPath(int pid) {
        return tmpdir + "/.java_pid" + pid;
    }
    
    private static int openDoor(String path) throws FileNotFoundException {
        int fd = CLibAttachSolaris.INSTANCE.open(path, CLibAttachSolaris.O_RDWR);
        if (fd == -1) {
            int err = Native.getLastError();
            if (err == CLibAttachSolaris.ENOENT) {
                throw new FileNotFoundException("Door file not found: " + path);
            }
            throw new RuntimeException("open: " + CLibAttachSolaris.INSTANCE.strerror(err));
        }
        return fd;
    }
    
    private static void checkPermissions(String path) throws IOException {
//        CLib.Stat64 st = new CLib.Stat64();
//        if (CLib.INSTANCE.stat64(path, st) != 0) {
//            throw new IOException("stat64: " + CLib.INSTANCE.strerror(Native.getLastError()));
//        }
//
//        int uid = CLib.INSTANCE.geteuid();
//        int gid = CLib.INSTANCE.getegid();
//
//        if (st.st_uid != uid && uid != 0) {
//            throw new IOException("file owner mismatch (uid " + st.st_uid + ')');
//        }
//        if (st.st_gid != gid && uid != 0) {
//            throw new IOException("file group mismatch (gid " + st.st_gid + ')');
//        }
//        int bad = (st.st_mode & (CLib.S_IRWXG | CLib.S_IRWXO));
//        if (bad != 0) {
//            throw new IOException(String.format("file permissions 0%03o too permissive", st.st_mode & 0777));
//        }
    }
    
    private static void sigquit(int pid) throws IOException {
        if (CLibAttachSolaris.INSTANCE.kill(pid, CLibAttachSolaris.SIGQUIT) == -1) {
            throw new IOException("kill: " + CLibAttachSolaris.INSTANCE.strerror(Native.getLastError()));
        }
    }
    
    public static void detach() throws IOException {
        if (door_fd != -1) {
            CLibAttachSolaris.INSTANCE.close(door_fd);
            door_fd = -1;
        }
    }
    
    // protocol version
    private final static String PROTOCOL_VERSION = "1";

    /**
     * Execute the given command in the target VM.
     */
    static InputStream execute(String cmd, Object... args) throws IOException {
        assert args.length <= 3;  // includes null

        if (door_fd == -1) {
            throw new IOException("Detached from target VM");
        }

        // Prepare the command buffer: <ver>\0<cmd>\0<arg1>\0<arg2>\0<arg3>\0
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            writeStringToStream(baos, PROTOCOL_VERSION);
            writeStringToStream(baos, cmd);
            
            for (int i = 0; i < 3; i++) {
                if (i < args.length && args[i] != null) {
                    writeStringToStream(baos, (String) args[i]);
                } else {
                    writeStringToStream(baos, "");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error preparing command", e);
        }
        
        byte[] commandBuffer = baos.toByteArray();
        
        // Call the door
        IntByReference resultFd = new IntByReference(-1);
        int status = doorCall(door_fd, commandBuffer, resultFd);
        
        if (status != 0) {
            throw new IOException("Door call failed: " + getErrorMessage(status));
        }
        
        int responseFd = resultFd.getValue();
        if (responseFd == -1) {
            throw new IOException("Door call didn't return a valid file descriptor");
        }
        
        return new SocketInputStream(responseFd);
    }
    
    private static void writeStringToStream(OutputStream os, String s) throws IOException {
        if (s != null) {
            os.write(s.getBytes(StandardCharsets.UTF_8));
        }
        os.write(0);  // NUL terminator
    }


    private static int doorCall(int doorFd, byte[] data, IntByReference resultFd) {
    	CLibAttachSolaris.door_arg arg = new CLibAttachSolaris.door_arg();
        Memory dataMem = new Memory(data.length);
        dataMem.write(0, data, 0, data.length);
        
        arg.data_ptr = dataMem;
        arg.data_size = data.length;
        arg.desc_ptr = Pointer.NULL;
        arg.desc_num = 0;
        
        // Buffer for response data
        Memory responseBuffer = new Memory(128);
        arg.rbuf = responseBuffer;
        arg.rsize = (int)responseBuffer.size();  // Cast long to int
        
        int result = CLibAttachSolaris.INSTANCE.door_call(doorFd, arg);
        
        if (result == 0) {
            // Check if we got a file descriptor back
            if (arg.desc_num > 0 && !arg.desc_ptr.equals(Pointer.NULL)) {
                CLibAttachSolaris.door_desc desc = new CLibAttachSolaris.door_desc(arg.desc_ptr);
                if ((desc.d_attributes & CLibAttachSolaris.DOOR_DESCRIPTOR) != 0) {
                    resultFd.setValue(desc.d_data.d_descriptor);
                }
            }
            
            // Check the response data for status
            byte[] response = responseBuffer.getByteArray(0, 4);
            if (response.length >= 4) {
                int status = (response[0] & 0xFF) | 
                            ((response[1] & 0xFF) << 8) | 
                            ((response[2] & 0xFF) << 16) | 
                            ((response[3] & 0xFF) << 24);
                return status;
            }
        }
        
        return -1;
    }
    
    
    
    private static String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case 100: return "Bad request";
            case 101: return "Protocol mismatch";
            case 102: return "Resource failure";
            case 103: return "Internal error";
            case 104: return "Permission denied";
            default: return "Unknown error: " + errorCode;
        }
    }
    
    static int read(int fd, byte[] dst, int off, int len) throws IOException {
        int todo = Math.min(len, 128);
        byte[] scratch = new byte[todo];

        int n;
        do {
            n = CLibAttachSolaris.INSTANCE.read(fd, scratch, todo);
        } while (n == -1 && Native.getLastError() == CLibAttachSolaris.EINTR);

        if (n == -1) {
            throw new IOException("read: " + CLibAttachSolaris.INSTANCE.strerror(Native.getLastError()));
        }
        if (n == 0) {                      // EOF
            return -1;
        }
        System.arraycopy(scratch, 0, dst, off, n);
        return n;
    }
    
    static class SocketInputStream extends InputStream {
        int fd;
        
        public SocketInputStream(int fd) {
            this.fd = fd;
        }
        
        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return (n == 1) ? (b[0] & 0xFF) : -1;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            
            return AttachSolaris.read(fd, b, off, len);
        }
        
        @Override
        public void close() throws IOException {
            CLibAttachSolaris.INSTANCE.close(fd);
        }
    }
    
    private static File createAttachFile(int pid) throws IOException {
        String fn = ".attach_pid" + pid;
        String path = "/proc/" + pid + "/cwd/" + fn;
        File f = new File(path);
        try {
            f.createNewFile();
        } catch (IOException x) {
            f = new File(tmpdir, fn);
            f.createNewFile();
        }
        return f;
    }
    
    
    

/**
 * Load agent library with absolute path specification
 */
private static void loadAgentLibrary(String agentLibrary, boolean isAbsolute, String options)
    throws Exception
{
    InputStream in = execute("load",
                             agentLibrary,
                             isAbsolute ? "true" : "false",
                             options);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
        String result = reader.readLine();
        if (result == null) {
            throw new Exception("Target VM did not respond");
        } else if (result.startsWith("return code: ")) {
            int retCode = Integer.parseInt(result.substring("return code: ".length()));
            if (retCode != 0) {
                throw new Exception("Agent_OnAttach failed with code: " + retCode);
            }
        } else {
            throw new Exception(result);
        }
    }
}

/**
 * Load agent library - library name will be expanded in target VM
 */
private static void loadAgentLibrary(String agentLibrary, String options)
    throws Exception
{
    loadAgentLibrary(agentLibrary, false, options);
}

/**
 * Loads a Java agent
 * @param agent Agent Jar
 * @param options Args
 * @throws IOException
 */
public static void loadAgent(String agent, String options)
    throws IOException
{
    if (agent == null) {
        throw new NullPointerException("agent cannot be null");
    }

    String args = agent;
    if (options != null) {
        args = args + "=" + options;
    }
    try {
        loadAgentLibrary("instrument", args);
    } catch (Exception e) {
        throw new IOException("Failed to load agent: " + e.getMessage(), e);
    }
}
    
    interface CLibAttachSolaris extends Library {
        CLibAttachSolaris INSTANCE = Native.load("c", CLibAttachSolaris.class);
        
        // File flags
        int O_RDWR = 0x0002;
        
        // Signal
        int SIGQUIT = 3;
        
        // Error codes
        int EINTR = 4;
        int ENOENT = 2;
        
        // File permissions
        int S_IRWXG = 0070;
        int S_IRWXO = 0007;
        
        // Door constants
        int DOOR_DESCRIPTOR = 0x4000;
        
        // File operations
        int open(String path, int flags);
        int close(int fd);
        int read(int fd, byte[] buf, int count);
        int kill(int pid, int sig);
        String strerror(int err);
        int geteuid();
        int getegid();
        
        // Door operations
        int door_call(int doorFd, door_arg arg);
        
        // Solaris-specific stat64 implementation
        int __xstat64(int version, String path, Stat64AttachSolaris st);
        
        // Wrapper method for stat64 that uses the correct underlying function
        default int stat64(String path, Stat64AttachSolaris st) {
            // In Solaris, stat64 is implemented as __xstat64(2, path, st)
            return __xstat64(2, path, st);
        }
        
        @Structure.FieldOrder({
            "st_dev", "st_ino", "st_mode", "st_nlink", 
            "st_uid", "st_gid", "st_rdev", "st_size", 
            "st_atime", "st_mtime", "st_ctime", "st_blksize", 
            "st_blocks"
        })
        class Stat64AttachSolaris extends Structure {
            public Stat64AttachSolaris() {
                // Use ALIGN_DEFAULT which works for most Unix systems
                setAlignType(Structure.ALIGN_DEFAULT);
            }

            public long   st_dev;
            public long   st_ino;
            public int    st_mode;
            public int    st_nlink;
            public int    st_uid;
            public int    st_gid;
            public long   st_rdev;
            public long   st_size;
            public long   st_atime;
            public long   st_mtime;
            public long   st_ctime;
            public int    st_blksize;
            public long   st_blocks;
        }
        
        @Structure.FieldOrder({"d_attributes", "d_data"})
        class door_desc extends Structure {
            public int d_attributes;
            
            @FieldOrder({"d_descriptor"})
            public static class door_data extends Structure {
                public int d_descriptor;
                
                public door_data() {
                    setAlignType(Structure.ALIGN_DEFAULT);
                }
            }
            
            public door_data d_data;
            
            public door_desc(Pointer desc_ptr) {
                setAlignType(Structure.ALIGN_DEFAULT);
            }
        }
        
        @Structure.FieldOrder({"data_ptr", "data_size", "desc_ptr", "desc_num", "rbuf", "rsize"})
        class door_arg extends Structure {
            public Pointer data_ptr;
            public int data_size;
            public Pointer desc_ptr;
            public int desc_num;
            public Pointer rbuf;
            public int rsize;
            
            public door_arg() {
                setAlignType(Structure.ALIGN_DEFAULT);
            }
        }
    }
}