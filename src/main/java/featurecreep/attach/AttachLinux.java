package featurecreep.attach;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import featurecreep.attach.AttachLinux.CLib.SockAddrUn;

public class AttachLinux extends AttachUnix {
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
                System.out.println("Invalid process identifier");
            }

            int ns_pid = getNamespacePid(pid);


            File socket_file = findSocketFile(pid, ns_pid);
            socket_path = socket_file.getPath();
            if (!socket_file.exists()) {
                File f = createAttachFile(pid, ns_pid);
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
                        System.out.println(
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
        return DEFAULT_TIMEOUT; // Use the defined default timeout
    }





    private static final int ENOENT = 2;
    private static final int EINTR  = 4;

    private static int socket() throws IOException {
        int fd = CLib.INSTANCE.socket(CLib.AF_UNIX, CLib.SOCK_STREAM, 0);
        if (fd == -1) {
            throw new IOException("socket: " + CLib.INSTANCE.strerror(Native.getLastError()));
        }
        return fd;
    }

    private static void connect(int fd, String path) throws IOException {
        // Build struct sockaddr_un exactly as the C code does
        CLib.SockAddrUn addr = new CLib.SockAddrUn();
        addr.sun_family = CLib.AF_UNIX;
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= addr.sun_path.length) {
            throw new IOException("UNIX domain path too long");
        }
        System.arraycopy(bytes, 0, addr.sun_path, 0, bytes.length);
        addr.sun_path[bytes.length] = 0;          // NUL‑terminate
        addr.write();                             // copy to native memory

        int res = CLib.INSTANCE.connect(fd, addr, addr.size());
        if (res == -1) {
            int err = Native.getLastError();
            if (err == ENOENT) {
                throw new FileNotFoundException("socket file not found: " + path);
            }
            throw new IOException("connect: " + CLib.INSTANCE.strerror(err));
        }
    }

    private static void close(int fd) throws IOException {
        CLib.INSTANCE.shutdown(fd, CLib.SHUT_RDWR);
        int res;
        do {
            res = CLib.INSTANCE.close(fd);
        } while (res == -1 && Native.getLastError() == EINTR);
        if (res == -1) {
            throw new IOException("close: " + CLib.INSTANCE.strerror(Native.getLastError()));
        }
    }

    private static void sendQuitTo(int pid) throws IOException {
        if (CLib.INSTANCE.kill(pid, CLib.SIGQUIT) == -1) {
            throw new IOException("kill: " + CLib.INSTANCE.strerror(Native.getLastError()));
        }
    }

    private static void checkPermissions(String path) throws IOException {
        CLib.Stat64 st = new CLib.Stat64();
        if (CLib.INSTANCE.stat64(path, st) != 0) {
            throw new IOException("stat64: " + CLib.INSTANCE.strerror(Native.getLastError()));
        }

        int uid = CLib.INSTANCE.geteuid();
        int gid = CLib.INSTANCE.getegid();

        if (st.st_uid != uid && uid != 0) {
            throw new IOException("file owner mismatch (uid " + st.st_uid + ')');
        }
        if (st.st_gid != gid && uid != 0) {
            throw new IOException("file group mismatch (gid " + st.st_gid + ')');
        }
        int bad = (st.st_mode & (CLib.S_IRWXG | CLib.S_IRWXO));
        if (bad != 0) {
            throw new IOException(String.format("file permissions 0%03o too permissive", st.st_mode & 0777));
        }
    }


    static int read(int fd, byte[] dst, int off, int len) throws IOException {
        int todo = Math.min(len, 128);
        byte[] scratch = new byte[todo];

        int n;
        do {
            n = CLib.INSTANCE.read(fd, scratch, todo);
        } while (n == -1 && Native.getLastError() == EINTR);

        if (n == -1) {
            throw new IOException("read: " + CLib.INSTANCE.strerror(Native.getLastError()));
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
                n = CLib.INSTANCE.write(fd, buf, chunk);
            } while (n == -1 && Native.getLastError() == EINTR);

            if (n <= 0) {   
                throw new IOException("write: " + CLib.INSTANCE.strerror(Native.getLastError()));
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

        // did we detach?
            if (socket_path == null) {
                System.out.println("Detached from target VM");
            }
        

        // create UNIX socket
        int s = socket();

        // connect to target VM
        try {
            connect(s, socket_path);
        } catch (IOException x) {
            close(s);
            throw x;
        }

        IOException ioe = null;

        // connected - write request
        // <ver> <cmd> <args...>
        System.out.println("writing");
        try {
            writeString(s, PROTOCOL_VERSION);
            writeString(s, cmd);

            for (int i=0; i<3; i++) {
                if (i < args.length && args[i] != null) {
                	System.out.println("arg "+(String)args[i]);
                    writeString(s, (String)args[i]);
                } else {
                    writeString(s, "");
                }
            }
            
            CLib.INSTANCE.shutdown(s, CLib.SHUT_WR);
            
        } catch (IOException x) {
            ioe = x;
        }


        // Create an input stream to read reply
        SocketInputStream sis = new SocketInputStream(s);
        System.out.println("sis size "+sis.available());
        // Read the command completion status
        int completionStatus;
        try {
        	System.out.println("readin int");
            completionStatus = readInt(sis);
            System.out.println("done reading int");
        } catch (IOException x) {
            sis.close();
            if (ioe != null) {
                throw ioe;
            } else {
                throw x;
            }
        }

        if (completionStatus != 0) {
            // read from the stream and use that as the error message
            String message = readErrorMessage(sis);
            sis.close();

            // In the event of a protocol mismatch then the target VM
            // returns a known error so that we can throw a reasonable
            // error.
            if (completionStatus == ATTACH_ERROR_BADVERSION) {
                throw new IOException("Protocol mismatch with target VM");
            }

            if (cmd.equals("load")) {
                String msg = "Failed to load agent library";
                if (!message.isEmpty())
                    msg += ": " + message;
                System.out.println(msg);
            } else {
                if (message.isEmpty())
                    message = "Command failed in target VM";
                System.out.println(message);
            }
        }

        return sis;
    }

    
 
    
    
    
    
    
    
    static int readInt(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();

        // read to \n or EOF
        int n;
        byte buf[] = new byte[1];
        do {
            n = in.read(buf, 0, 1);
            if (n > 0) {
                char c = (char)buf[0];
                if (c == '\n') {
                    break;                  // EOL found
                } else {
                    sb.append(c);
                }
            }
        } while (n > 0);

        if (sb.length() == 0) {
            throw new IOException("Premature EOF");
        }

        int value;
        try {
            value = Integer.parseInt(sb.toString());
        } catch (NumberFormatException x) {
            throw new IOException("Non-numeric value found - int expected");
        }
        return value;
    }

    static String readErrorMessage(InputStream in) throws IOException {
        String s;
        StringBuilder message = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        while ((s = br.readLine()) != null) {
            message.append(s);
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
            if (n == 1) {
                return b[0] & 0xff;
            } else {
                return -1;
            }
        }

        public synchronized int read(byte[] bs, int off, int len) throws IOException {
            if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            return AttachLinux.read(s, bs, off, len);
        }

        public void close() throws IOException {
            AttachLinux.close(s);
        }
    }
    
    
    
    
    
    
    
    
    private static File findSocketFile(int pid, int ns_pid) {
        String root = "/proc/" + pid + "/root/" + tmpdir;
        return new File(root, ".java_pid" + ns_pid);
    }
    
    


    private static File createAttachFile(int pid, int ns_pid) throws IOException {
        String fn = ".attach_pid" + ns_pid;
        String path = "/proc/" + pid + "/cwd/" + fn;
        File f = new File(path);
        try {
            f.createNewFile();
        } catch (IOException x) {
            String root;
            if (pid != ns_pid) {
                root = "/proc/" + pid + "/root/" + tmpdir;
            } else {
                root = tmpdir;
            }
            f = new File(root, fn);
            f.createNewFile();
        }
        return f;
    }

    /*
     * Write/sends the given to the target VM. String is transmitted in
     * UTF-8 encoding.
     */
//    private static void writeString(int fd, String s) throws IOException {
//        if (s.length() > 0) {
//            byte b[];
//            try {
//                b = s.getBytes("UTF-8");
//            } catch (java.io.UnsupportedEncodingException x) {
//                throw new InternalError(x);
//            }
//            System.out.println(s);
//            write(fd, b, 0, b.length);
//            System.out.println("written");
//        }
//        byte b[] = new byte[1];
//        b[0] = 0;
//        write(fd, b, 0, 1);
//    }
    
//    private static void writeString(int fd, String s) throws IOException {
//        if (!s.isEmpty()) {
//            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
//            write(fd, bytes, 0, bytes.length);      // big chunk via Memory helper
//        }
//        /* single 0 byte straight through CLib.write */
//        CLib.INSTANCE.write(fd, new byte[] { 0 }, 0, 1);
//    }


    
    
    
    
    
    private static int getNamespacePid(int pid) throws IOException {
        String statusFile = "/proc/" + pid + "/status";
        File f = new File(statusFile);
        if (!f.exists()) {
            return pid;
        }

        Path statusPath = Paths.get(statusFile);

        try {
            for (String line : Files.readAllLines(statusPath, StandardCharsets.UTF_8)) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].trim().equals("NSpid")) {
                    parts = parts[1].trim().split("\\s+");
                  
                    int ns_pid = Integer.parseInt(parts[parts.length - 1]);
                    return ns_pid;
                }
            }
            // Old kernels may not have NSpid field (i.e. 3.10).
            // Fallback to original pid in the event we cannot deduce.
            return pid;
        } catch (NumberFormatException | IOException x) {
            throw x;
        }
    }
    
    
    
    
    
    
    
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
                    throw new Exception("Agent_OnAttach failed"+String.valueOf(retCode));
                }
            } else {
                throw new Exception(result);
            }
        }
    }

    /*
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
            e.printStackTrace();
        }
    }
    
    



    interface CLib extends Library {
        int SHUT_WR = 1;

		CLib INSTANCE = Native.load("c", CLib.class);

        int AF_UNIX = 1;
        int SOCK_STREAM = 1;
        int SIGQUIT = 3;
        int SHUT_RDWR = 2;
        int EINTR = 4;
        int ENOENT = 2;
        int S_IRWXG = 0070;
        int S_IRWXO = 0007;

        int  write(int fd, byte[] buffer, int count);
        int socket(int afUnix, int sockStream, int i);
		int  read (int fd, byte[] buffer, int count);

        int connect(int fd, SockAddrUn addr, int addrlen);
        int close(int fd);
        int shutdown(int fd, int how);
        int kill(int pid, int sig);
        String strerror(int err);
        int stat64(String path, Stat64 st);
        int geteuid();
        int getegid();

        @Structure.FieldOrder({"st_dev", "st_ino", "st_nlink", "st_mode", "st_uid", "st_gid", 
                              "st_rdev", "st_size", "st_blksize", "st_blocks", "st_atime", 
                              "st_mtime", "st_ctime"})
        class Stat64 extends Structure {
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
        class SockAddrUn extends Structure {
            public short sun_family = AF_UNIX;
            public byte[] sun_path = new byte[108];

            
    	    @Override
    	    protected java.util.List<String> getFieldOrder() {
    	        return java.util.Arrays.asList("sun_family", "sun_path");
    	    }
            
        }
    }
}
