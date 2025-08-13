package featurecreep.attach;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

/**
 * Attach Agent to AIX. I do not have access to AIX at this time so it is untested for now
 */
public class AttachAix extends AttachUnix {
    private static String socket_path;
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
            pid = (int) io.smallrye.common.os.Process.getProcessId();
        } catch (NumberFormatException x) {
            throw new IOException("Invalid process identifier");
        }

        File socket_file = findSocketFile(pid);
        socket_path = socket_file.getPath();
        if (!socket_file.exists()) {
            File f = createAttachFile(pid);
            try {
                sendQuitTo(pid);

                final int delay_step = 100;
                final long timeout = attachTimeout();
                long time_spend = 0;
                long delay = 0;
                do {
                    delay += delay_step;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException x) { }

                    time_spend += delay;
                    if (time_spend > timeout/2 && !socket_file.exists()) {
                        sendQuitTo(pid);
                    }
                } while (time_spend <= timeout && !socket_file.exists());
                if (!socket_file.exists()) {
                    throw new IOException(
                        String.format("Unable to open socket file %s: " +
                          "target process %d doesn't respond within %dms " +
                          "or HotSpot VM not loaded", socket_path, pid,
                                      time_spend));
                }
            } finally {
                f.delete();
            }
        }

        checkPermissions(socket_path);

        int s = socket();
        try {
            connect(s, socket_path);
        } finally {
            close(s);
        }
    }
    
    private static long attachTimeout() {
        return DEFAULT_TIMEOUT;
    }

    private static final int ENOENT = 2;
    private static final int EINTR  = 4;

    private static int socket() throws IOException {
        int fd = CLibAttachAIX.INSTANCE.socket(CLibAttachAIX.AF_UNIX, CLibAttachAIX.SOCK_STREAM, 0);
        if (fd == -1) {
            throw new IOException("socket: " + CLibAttachAIX.INSTANCE.strerror(Native.getLastError()));
        }
        return fd;
    }

    private static void connect(int fd, String path) throws IOException {
        CLibAttachAIX.SockAddrUnAttachAIX addr = new CLibAttachAIX.SockAddrUnAttachAIX();
        addr.sun_family = CLibAttachAIX.AF_UNIX;
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        
        if (bytes.length >= addr.sun_path.length - 1) {
            throw new IOException("UNIX domain path too long");
        }
        
        System.arraycopy(bytes, 0, addr.sun_path, 0, bytes.length);
        addr.sun_path[bytes.length] = 0;  // NUL-terminate
        addr.write();                     // Copy to native memory

        // Calculate SUN_LEN: sizeof(sun_family) + strlen(sun_path)
        int pathLen = bytes.length;  // strlen (excludes NUL)
        int addrLen = 2 + pathLen;   // AF_UNIX (2 bytes) + path length
        
        int res = CLibAttachAIX.INSTANCE.connect(fd, addr, addrLen);
        if (res == -1) {
            int err = Native.getLastError();
            if (err == ENOENT) {
                throw new FileNotFoundException("socket file not found: " + path);
            }
            throw new IOException("connect: " + CLibAttachAIX.INSTANCE.strerror(err));
        }
    }

    private static void close(int fd) throws IOException {
        CLibAttachAIX.INSTANCE.shutdown(fd, CLibAttachAIX.SHUT_RDWR);
        int res;
        do {
            res = CLibAttachAIX.INSTANCE.close(fd);
        } while (res == -1 && Native.getLastError() == EINTR);
        if (res == -1) {
            throw new IOException("close: " + CLibAttachAIX.INSTANCE.strerror(Native.getLastError()));
        }
    }

    private static void sendQuitTo(int pid) throws IOException {
        if (CLibAttachAIX.INSTANCE.kill(pid, CLibAttachAIX.SIGQUIT) == -1) {
            throw new IOException("kill: " + CLibAttachAIX.INSTANCE.strerror(Native.getLastError()));
        }
    }

    private static void checkPermissions(String path) throws IOException {
        CLibAttachAIX.Stat64AttachAIX st = new CLibAttachAIX.Stat64AttachAIX();
        if (CLibAttachAIX.INSTANCE.stat64(path, st) != 0) {
            throw new IOException("stat64: " + CLibAttachAIX.INSTANCE.strerror(Native.getLastError()));
        }

        int uid = CLibAttachAIX.INSTANCE.geteuid();
        int gid = CLibAttachAIX.INSTANCE.getegid();

        if (st.st_uid != uid && uid != 0) {
            throw new IOException("file owner mismatch (uid " + st.st_uid + ')');
        }
        if (st.st_gid != gid && uid != 0) {
            throw new IOException("file group mismatch (gid " + st.st_gid + ')');
        }
        int bad = (st.st_mode & (CLibAttachAIX.S_IRWXG | CLibAttachAIX.S_IRWXO));
        if (bad != 0) {
            throw new IOException(String.format("file permissions 0%03o too permissive", st.st_mode & 0777));
        }
    }

    static int read(int fd, byte[] dst, int off, int len) throws IOException {
        int todo = Math.min(len, 128);
        byte[] scratch = new byte[todo];

        int n;
        do {
            n = CLibAttachAIX.INSTANCE.read(fd, scratch, todo);
        } while (n == -1 && Native.getLastError() == EINTR);

        if (n == -1) {
            throw new IOException("read: " + CLibAttachAIX.INSTANCE.strerror(Native.getLastError()));
        }
        if (n == 0) {                      // EOF
            return -1;
        }
        System.arraycopy(scratch, 0, dst, off, n);
        return n;
    }

    private static void writeBytes(int fd, byte[] src, int off, int len) throws IOException {
        final byte[] buf = new byte[128];

        int remaining = len;
        while (remaining > 0) {
            int chunk = Math.min(buf.length, remaining);
            System.arraycopy(src, off, buf, 0, chunk);

            int n;
            do {
                n = CLibAttachAIX.INSTANCE.write(fd, buf, chunk);
            } while (n == -1 && Native.getLastError() == EINTR);

            if (n <= 0) {   
                throw new IOException("write: " + CLibAttachAIX.INSTANCE.strerror(Native.getLastError()));
            }
            off       += n;
            remaining -= n;
        }
    }

    private static void writeString(int fd, String s) throws IOException {
        if (!s.isEmpty()) {
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            writeBytes(fd, utf8, 0, utf8.length);
        }
        writeBytes(fd, new byte[] { 0 }, 0, 1);   // trailing NUL
    }

    public static void detach() throws IOException {
        if (socket_path != null) {
            socket_path = null;
        }
    }

    // protocol version
    private final static String PROTOCOL_VERSION = "1";

    /**
     * Execute the given command in the target VM.
     */
    static InputStream execute(String cmd, Object ... args) throws IOException {
        assert args.length <= 3;                // includes null

        if (socket_path == null) {
            throw new IOException("Detached from target VM");
        }

        int s = socket();
        try {
            connect(s, socket_path);
        } catch (IOException x) {
            close(s);
            throw x;
        }

        IOException ioe = null;
        try {
            writeString(s, PROTOCOL_VERSION);
            writeString(s, cmd);

            for (int i = 0; i < 3; i++) {
                if (i < args.length && args[i] != null) {
                    writeString(s, (String) args[i]);
                } else {
                    writeString(s, "");
                }
            }
            CLibAttachAIX.INSTANCE.shutdown(s, CLibAttachAIX.SHUT_WR);
        } catch (IOException x) {
            ioe = x;
        }

        SocketInputStream sis = new SocketInputStream(s);
        int completionStatus;
        try {
            completionStatus = readInt(sis);
        } catch (IOException x) {
            sis.close();
            if (ioe != null) throw ioe;
            throw x;
        }

        if (completionStatus != 0) {
            String message = readErrorMessage(sis);
            sis.close();

            if (completionStatus == ATTACH_ERROR_BADVERSION) {
                throw new IOException("Protocol mismatch with target VM");
            }

            if (cmd.equals("load")) {
                String msg = "Failed to load agent library";
                if (!message.isEmpty()) msg += ": " + message;
                throw new IOException(msg);
            } else {
                throw new IOException(!message.isEmpty() ? message : "Command failed in target VM");
            }
        }

        return sis;
    }

    static int readInt(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte buf[] = new byte[1];
        int n;
        do {
            n = in.read(buf, 0, 1);
            if (n > 0) {
                char c = (char) buf[0];
                if (c == '\n') break;
                sb.append(c);
            }
        } while (n > 0);

        if (sb.length() == 0) {
            throw new IOException("Premature EOF");
        }

        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException x) {
            throw new IOException("Non-numeric value found - int expected");
        }
    }

    static String readErrorMessage(InputStream in) throws IOException {
        StringBuilder message = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                message.append(line);
            }
        }
        return message.toString();
    }

    static class SocketInputStream extends InputStream {
        int s;

        public SocketInputStream(int s) {
            this.s = s;
        }

        public synchronized int read() throws IOException {
            byte b[] = new byte[1];
            int n = this.read(b, 0, 1);
            return (n == 1) ? (b[0] & 0xff) : -1;
        }

        public synchronized int read(byte[] bs, int off, int len) throws IOException {
            if (off < 0 || off > bs.length || len < 0 || 
                (off + len) > bs.length || (off + len) < 0) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) return 0;
            return AttachAix.read(s, bs, off, len);
        }

        public void close() throws IOException {
            AttachAix.close(s);
        }
    }

    private static File findSocketFile(int pid) {
        return new File(tmpdir, ".java_pid" + pid);
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
                    throw new Exception("Agent_OnAttach failed with return code: " + retCode);
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

    interface CLibAttachAIX extends Library {
        CLibAttachAIX INSTANCE = Native.load("c", CLibAttachAIX.class);

        int AF_UNIX = 1;
        int SOCK_STREAM = 1;
        int SIGQUIT = 3;
        int SHUT_RDWR = 2;
        int SHUT_WR = 1;
        int EINTR = 4;
        int ENOENT = 2;
        int S_IRWXG = 0070;
        int S_IRWXO = 0007;

        int socket(int domain, int type, int protocol);
        int connect(int sockfd, SockAddrUnAttachAIX addr, int addrlen);
        int close(int fd);
        int shutdown(int fd, int how);
        int kill(int pid, int sig);
        String strerror(int errnum);
        int stat64(String path, Stat64AttachAIX buf);
        int geteuid();
        int getegid();
        int read(int fd, byte[] buf, int count);
        int write(int fd, byte[] buf, int count);

        @Structure.FieldOrder({"st_dev", "st_ino", "st_nlink", "st_mode", "st_uid", 
                              "st_gid", "st_rdev", "st_size", "st_blksize", "st_blocks",
                              "st_atime", "st_mtime", "st_ctime"})
        class Stat64AttachAIX extends Structure {
            public long st_dev;
            public long st_ino;
            public long st_nlink;
            public int st_mode;
            public int st_uid;
            public int st_gid;
            public long st_rdev;
            public long st_size;
            public int st_blksize;
            public long st_blocks;
            public long st_atime;
            public long st_mtime;
            public long st_ctime;
        }

        @Structure.FieldOrder({"sun_family", "sun_path"})
        class SockAddrUnAttachAIX extends Structure {
            public short sun_family;
            public byte[] sun_path = new byte[108];

            public SockAddrUnAttachAIX() {
                super();
                setAlignType(Structure.ALIGN_NONE);
            }
        }
    }
}