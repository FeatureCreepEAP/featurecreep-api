package featurecreep.attach;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

// JNA library interface
interface CLibraryAttachBSD extends Library {
	
	
	
	CLibraryAttachBSD INSTANCE = (CLibraryAttachBSD) Native.loadLibrary("c", CLibraryAttachBSD.class);

	// Add missing functions
	int geteuid();

	int getegid();

	// Socket functions
	int socket(int domain, int type, int protocol);

	int connect(int sockfd, Structure addr, int addrlen);

	int close(int fd);

	int shutdown(int sockfd, int how);

	int read(int fd, byte[] buf, int count);

	int write(int fd, byte[] buf, int count);

	int stat(String path, StatAttachBSD statbuf);

	int fstat(int fd, StatAttachBSD statbuf);

	int chown(String path, int uid, int gid);

	int kill(int pid, int sig);

	int sysctl(int[] name, int namelen, Structure oldp, IntByReference oldlenp, Structure newp, int newlen);

	int confstr(int name, byte[] buf, int len);

	String strerror(int errnum);

	int open(String pathname, int flags, int mode);

	// Declare getSymbol() for constant lookup
	Pointer getSymbol(String name);

	// Declare pathconf() for PATH_MAX
	int pathconf(String path, int name);

	int mkdir(String absolutePath, int i);
}

public class BSDAttach {

	   private static final String tmpdir;
	    String socket_path;
	    private static final int ATTACH_ERROR_BADVERSION = 101;
	
	
	    public BSDAttach(String vmid)
		        throws IOException
		    {
		        // This provider only understands pids
		        int pid = Integer.parseInt(vmid);
		        if (pid < 1) {
	//Bad
		        }

		        // Find the socket file. If not found then we attempt to start the
		        // attach mechanism in the target VM by sending it a QUIT signal.
		        // Then we attempt to find the socket file again.
		        File socket_file = new File(tmpdir, ".java_pid" + pid);
		        socket_path = socket_file.getPath();
		        if (!socket_file.exists()) {
		            File f = createAttachFile(pid);
		            try {
		            	BSDAttach.checkCatchesAndSendQuitTo(pid,true);
		                // give the target VM time to start the attach mechanism
		                final int delay_step = 100;
		                final long timeout = 30000;
		                long time_spend = 0;
		                long delay = 0;
		                do {
		                    // Increase timeout on each attempt to reduce polling
		                    delay += delay_step;
		                    try {
		                        Thread.sleep(delay);
		                    } catch (InterruptedException x) { }

		                    time_spend += delay;
		                    if (time_spend > timeout/2 && !socket_file.exists()) {
		                        // Send QUIT again to give target VM the last chance to react
		                        BSDAttach.checkCatchesAndSendQuitTo(pid, true);
		                    }
		                } while (time_spend <= timeout && !socket_file.exists());
		                if (!socket_file.exists()) {
	System.out.println("No socket file");
		                }
		            } catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} finally {
		                f.delete();
		            }
		        }

		        // Check that the file owner/permission to avoid attaching to
		        // bogus process
		        BSDAttach.checkPermissions(socket_path);

		        // Check that we can connect to the process
		        // - this ensures we throw the permission denied error now rather than
		        // later when we attempt to enqueue a command.
		        int s = BSDAttach.createSocket();
		        try {
		        	BSDAttach.connectSocket(s, socket_path);
		        } finally {
		        	BSDAttach.closeSocket(s);
		        }
		    }
	
	

		
		
		
		
		 /**
	     * Detach from the target VM
	     */
	    public void detach() throws IOException {
	        synchronized (this) {
	            if (socket_path != null) {
	                socket_path = null;
	            }
	        }
	    }

	    // protocol version
	    private static final String PROTOCOL_VERSION = "1";

	    /**
	     * Execute the given command in the target VM.
	     */
	    InputStream execute(String cmd, Object ... args) throws IOException {
	        assert args.length <= 3;                // includes null
	        checkNulls(args);

	        // did we detach?
	        synchronized (this) {
	            if (socket_path == null) {
	                throw new IOException("Detached from target VM");
	            }
	        }

	        // create UNIX socket
	        int s = BSDAttach.createSocket();

	        // connect to target VM
	        try {
	        	BSDAttach.connectSocket(s, socket_path);
	        } catch (IOException x) {
	        	BSDAttach.closeSocket(s);
	            throw x;
	        }

	        IOException ioe = null;

	        // connected - write request
	        // <ver> <cmd> <args...>
	        try {
	            writeString(s, PROTOCOL_VERSION);
	            writeString(s, cmd);

	            for (int i = 0; i < 3; i++) {
	                if (i < args.length && args[i] != null) {
	                    writeString(s, (String)args[i]);
	                } else {
	                    writeString(s, "");
	                }
	            }
	        } catch (IOException x) {
	            ioe = x;
	        }


	        // Create an input stream to read reply
	        SocketInputStreamImpl sis = new SocketInputStreamImpl(s);

	        // Process the command completion status
	        processCompletionStatus(ioe, cmd, sis);

	        // Return the input stream so that the command output can be read
	        return sis;
	    }
	    
	    /*
	     * Utility method to process the completion status after command execution.
	     * If we get IOE during previous command execution, delay throwing it until
	     * completion status has been read.
	     */
	    void processCompletionStatus(IOException ioe, String cmd, InputStream sis) throws IOException {
	        // Read the command completion status
	    	System.out.println(cmd);
	    	int completionStatus;
	        try {
	            completionStatus = readInt(sis);
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
	            System.out.println(message);
	            sis.close();

	            // In the event of a protocol mismatch then the target VM
	            // returns a known error so that we can throw a reasonable
	            // error.
	            if (completionStatus == ATTACH_ERROR_BADVERSION) {
	                throw new IOException("Protocol mismatch with target VM");
	            }

	            // Special-case the "load" command so that the right exception is
	            // thrown.
	            if (cmd.equals("load")) {
	                String msg = "Failed to load agent library";
	                if (!message.isEmpty()) {
	                    msg += ": " + message;
	                }
	               // throw new AgentLoadException(msg);
	            } else {
	                if (message.isEmpty()) {
	                    message = "Command failed in target VM";
	                }
	              //  throw new AttachOperationFailedException(message);
	            }
	        }
	    }
	    
	    
	    
	    
	    
	    /*
	     * Utility method to read an 'int' from the input stream. Ideally
	     * we should be using java.util.Scanner here but this implementation
	     * guarantees not to read ahead.
	     */
	    int readInt(InputStream in) throws IOException {
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
	    
	    

	    /*
	     * InputStream for the socket connection to get target VM
	     */
	    private static class SocketInputStreamImpl extends InputStream {
	       long fd;
	    	public SocketInputStreamImpl(long fd) {
	    		this.fd=fd;
	        }

	        
	        protected int read(long fd, byte[] bs, int off, int len) throws IOException {
	            return BSDAttach.readSocket((int)fd, bs, off, len);
	        }

	        
	        protected void close(long fd) throws IOException {
	        	BSDAttach.closeSocket((int)fd);
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
	            return read(fd, bs, off, len);
	        }

	        public synchronized void close() throws IOException {
	            if (fd != -1) {
	                long toClose = fd;
	                fd = -1;
	                close(toClose);
	            }
	        }
	    }

	    /*
	     * Write/sends the given to the target VM. String is transmitted in
	     * UTF-8 encoding.
	     */
	    private void writeString(int fd, String s) throws IOException {
	        if (s.length() > 0) {
	            byte[] b = s.getBytes("UTF-8");
	            BSDAttach.writeSocket(fd, b, 0, b.length);
	        }
	        byte b[] = new byte[1];
	        b[0] = 0;
	        BSDAttach.writeSocket(fd, b, 0, 1);
	    }

	    private File createAttachFile(int pid) throws IOException {
	        File f = new File(tmpdir, ".attach_pid" + pid);
	        BSDAttach.createAttachFile(f.getPath());
	        return f;
	    }

	    protected static void checkNulls(Object... args) {
	        for (Object arg : args) {
	            if (arg instanceof String) {
	            	String s = (String)arg;
	                if (s.indexOf(0) >= 0) {
	                    throw new IllegalArgumentException("illegal null character in command");
	                }
	            }
	        }
	    }

	    static {
	        System.loadLibrary("attach");
	        tmpdir = BSDAttach.getTempDir();
	    }
		
		
	    /*
	     * Utility method to read data into a String.
	     */
	    String readErrorMessage(InputStream in) throws IOException {
	        String s;
	        StringBuilder message = new StringBuilder();
	        BufferedReader br = new BufferedReader(new InputStreamReader(in));
	        while ((s = br.readLine()) != null) {
	            message.append(s);
	        }
	        return message.toString();
	    }
	    
	    
	    public void loadAgent(String agent, String options)
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
	            } catch (Exception x) {
	                /*
	                 * Translate interesting errors into the right exception and
	                 * message (FIXME: create a better interface to the instrument
	                 * implementation so this isn't necessary)
	                 */
//	                int rc = x.returnValue();
//	                switch (rc) {
//	                    case JNI_ENOMEM:
//	                        throw new Exception("Insuffient memory");
//	                    case ATTACH_ERROR_BADJAR:
//	                        throw new Exception(
//	                            "Agent JAR not found or no Agent-Class attribute");
//	                    case ATTACH_ERROR_NOTONCP:
//	                        throw new Exception(
//	                            "Unable to add JAR file to system class path");
//	                    case ATTACH_ERROR_STARTFAIL:
//	                        throw new Exception(
//	                            "Agent JAR loaded but agent failed to initialize");
//	                    default :
//	                        throw new Exception("" +
//	                            "Failed to load agent - unknown reason: " + rc);
//	                }
	            }
	        }
	    
	    public void loadAgentLibrary(String agentLibrary, String options)
	          throws Exception
	        {
	            loadAgentLibrary(agentLibrary, false, options);
	        }
	    
	    
	    private void loadAgentLibrary(String agentLibrary, boolean isAbsolute, String options)
	          throws Exception
	        {
	            if (agentLibrary == null) {
	                throw new NullPointerException("agentLibrary cannot be null");
	            }

	            String msgPrefix = "return code: ";
	            InputStream in = execute("load",
	                                     agentLibrary,
	                                     isAbsolute ? "true" : "false",
	                                     options);
	            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
	                String result = reader.readLine();
	                if (result == null) {
	                    throw new Exception("Target VM did not respond");
	                } else if (result.startsWith(msgPrefix)) {
	                    int retCode = Integer.parseInt(result.substring(msgPrefix.length()));
	                    if (retCode != 0) {
	                        throw new Exception("Agent_OnAttach failed " + retCode);
	                    }
	                } else {
	                    throw new Exception(result);
	                }
	            }
	        }
		
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	
	public static int createSocket() throws IOException {
		int fd = CLibraryAttachBSD.INSTANCE.socket(NativeConstantsAttachBSD.PF_UNIX, NativeConstantsAttachBSD.SOCK_STREAM, 0);
		if (fd == -1) {
			throw new IOException("Failed to create socket: " + CLibraryAttachBSD.INSTANCE.strerror(Native.getLastError()));
		}
		return fd;
	}

	public static void connectSocket(int fd, String path) throws IOException {
		SockaddrUnAttachBSD addr = new SockaddrUnAttachBSD();
		addr.sun_family = NativeConstantsAttachBSD.AF_UNIX;
		byte[] pathBytes = path.getBytes();

		System.arraycopy(pathBytes, 0, addr.sun_path, 0, Math.min(pathBytes.length, 107));
		addr.sun_path[Math.min(pathBytes.length, 107)] = 0;

		int result = CLibraryAttachBSD.INSTANCE.connect(fd, addr, addr.size());
		if (result == -1) {
			int errno = Native.getLastError();
			if (errno == 2) { // ENOENT
				throw new FileNotFoundException("Socket file not found: " + path);
			}
			throw new IOException("Connect failed: " + CLibraryAttachBSD.INSTANCE.strerror(errno));
		}
	}

	public static boolean checkCatchesAndSendQuitTo(int pid, boolean throwIfNotReady)
			throws IOException, InterruptedException {

		// Check if process exists
		Process psProcess = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "pid=").start();
		psProcess.waitFor();
		if (psProcess.exitValue() != 0) {
			if (throwIfNotReady) {
				throw new IOException("Process " + pid + " does not exist");
			}
			return false;
		}

		// Send SIGQUIT
		Process killProcess = new ProcessBuilder("kill", "-QUIT", String.valueOf(pid)).start();
		killProcess.waitFor();
		if (killProcess.exitValue() != 0) {
			if (throwIfNotReady) {
				throw new IOException("Failed to send SIGQUIT to process " + pid);
			}
			return false;
		}
		return true;
	}

	public static void checkPermissions(String path) throws IOException {
//        Stat stat = new Stat();
//        if (CLibrary.INSTANCE.stat(path, stat) != 0) {
//            throw new IOException("Failed to stat file: " + 
//                CLibrary.INSTANCE.strerror(Native.getLastError()));
//        }
//
//        int uid = CLibrary.INSTANCE.geteuid();
//        int gid = CLibrary.INSTANCE.getegid();
//
//        if (stat.st_uid != uid && uid != 0) {
//            throw new IOException("File owner mismatch: " + stat.st_uid + 
//                " vs current uid " + uid);
//        }
//        if (stat.st_gid != gid && uid != 0) {
//            throw new IOException("File group mismatch: " + stat.st_gid + 
//                " vs current gid " + gid);
//        }
//        if ((stat.st_mode & 077) != 0) {
//            throw new IOException("Invalid file permissions: " + 
//                Integer.toOctalString(stat.st_mode & 0777));
//        }
	}

	public static String getTempDir() {
		// Get TMPDIR environment variable (common on macOS/BSD)
		String tempDir = System.getenv("TMPDIR");
		if (tempDir == null || tempDir.isEmpty()) {
			// Fallback to /tmp if unset
			tempDir = "/tmp";
		}
		return tempDir;
	}

	public static void closeSocket(int fd) {
		CLibraryAttachBSD.INSTANCE.shutdown(fd, NativeConstantsAttachBSD.SHUT_RDWR);
		CLibraryAttachBSD.INSTANCE.close(fd);
	}

	public static int readSocket(int fd, byte[] buffer, int offset, int length) throws IOException {

		int bytesRead = CLibraryAttachBSD.INSTANCE.read(fd, buffer, length);
		if (bytesRead == -1) {
			throw new IOException("Read failed: " + CLibraryAttachBSD.INSTANCE.strerror(Native.getLastError()));
		}
		return bytesRead;
	}

	public static void writeSocket(int fd, byte[] buffer, int offset, int length) throws IOException {

		int bytesWritten = CLibraryAttachBSD.INSTANCE.write(fd, buffer, length);
		if (bytesWritten == -1) {
			throw new IOException("Write failed: " + CLibraryAttachBSD.INSTANCE.strerror(Native.getLastError()));
		}
	}

	public static void createAttachFile(String path) throws IOException {
		int fd = CLibraryAttachBSD.INSTANCE.open(path, NativeConstantsAttachBSD.O_CREAT | NativeConstantsAttachBSD.O_EXCL, 0600);
		if (fd == -1) {
			throw new IOException("Failed to create file: " + CLibraryAttachBSD.INSTANCE.strerror(Native.getLastError()));
		}
		try {
			int uid = CLibraryAttachBSD.INSTANCE.geteuid();
			int gid = CLibraryAttachBSD.INSTANCE.getegid();
			CLibraryAttachBSD.INSTANCE.chown(path, uid, gid); // Ownership change
		} finally {
			CLibraryAttachBSD.INSTANCE.close(fd); // Ensure close
		}
	}

}

class NativeConstantsAttachBSD {
	private static final CLibraryAttachBSD LIBC = CLibraryAttachBSD.INSTANCE;

	public static final int _CS_DARWIN_USER_TEMP_DIR = 65537;

//    // Load O_* flags from fcntl.h
//    public static final int O_CREAT = getConstant("_O_CREAT");
//    public static final int O_EXCL = getConstant("_O_EXCL");
//    public static final int O_RDWR = getConstant("_O_RDWR");
//
//    // Load socket constants
//    public static final int PF_UNIX = getConstant("_PF_UNIX");
//    public static final int SOCK_STREAM = getConstant("_SOCK_STREAM");
//    //public static final int AF_UNIX = getConstant("_AF_UNIX");
//    public static final int SHUT_RDWR = getConstant("_SHUT_RDWR");
//
//    // Load signal constants
//    public static final int SIGQUIT = getConstant("_SIGQUIT");
//
//    // Load sysctl constants
//    public static final int CTL_KERN = getConstant("_CTL_KERN");
//    public static final int KERN_PROC = getConstant("_KERN_PROC");
//    public static final int KERN_PROC_PID = getConstant("_KERN_PROC_PID");
//
//    // Load system constants
//    public static final int PATH_MAX = LIBC.pathconf("/", 0x01 /* _PC_PATH_MAX */);
//    

	// File flags (from macOS fcntl.h)
	static int O_CREAT = 0x0200; // Octal 0400
	static int O_EXCL = 0x0800; // Octal 02000
	static int O_RDWR = 0x0002; // Octal 02

	// Socket constants (from macOS sys/socket.h)
	static short PF_UNIX = 1;
	static short SOCK_STREAM = 1;
	static short AF_UNIX = 1;
	static int SHUT_RDWR = 2;

	// Signals (from macOS signal.h)
	static int SIGQUIT = 3;

	// Sysctl constants (from macOS sys/sysctl.h)
	static int CTL_KERN = 1;
	static int KERN_PROC = 14;
	static int KERN_PROC_PID = 1;

	// macOS-specific constants
	static int PATH_MAX = 1024;

//
//    // Special handling for macOS constants
//    public static final int _CS_DARWIN_USER_TEMP_DIR;
//    public static final short AF_UNIX; // Actually uint8_t (byte)
//
//    static {
//        if (System.getProperty("os.name").contains("Mac")) {
//            _CS_DARWIN_USER_TEMP_DIR = 65537; // Hardcode known macOS value
//            AF_UNIX = 1; // Hardcode known value from sys/un.h
//        } else {
//            _CS_DARWIN_USER_TEMP_DIR = -1; // Not applicable on other OS
//            AF_UNIX = (short) LIBC.getSymbol("_AF_UNIX").getInt(0);
//        }
//    }

	private static int getConstant(String name) {
		System.out.println(name);
		Pointer ptr = LIBC.getSymbol(name);
		if (ptr == null) {
			throw new RuntimeException("Constant not found: " + name);
		}
		return ptr.getInt(0);
	}
}

class StatAttachBSD extends Structure {
	public int st_dev;
	public short st_mode;
	public short st_nlink;
	public int st_ino;
	public int st_uid;
	public int st_gid;
	public int st_rdev;
	public long st_atime;
	public long st_mtime;
	public long st_ctime;
	public long st_size;
	public long st_blocks;
	public int st_blksize;
	public int st_flags;
	public int st_gen;
	public int st_lspare;
	public long st_qspare1;
	public long st_qspare2;

	@Override
	protected java.util.List<String> getFieldOrder() {
		return java.util.Arrays.asList("st_dev", "st_mode", "st_nlink", "st_ino", "st_uid", "st_gid", "st_rdev",
				"st_atime", "st_mtime", "st_ctime", "st_size", "st_blocks", "st_blksize", "st_flags", "st_gen",
				"st_lspare", "st_qspare1", "st_qspare2");
	}
}

class SockaddrUnAttachBSD extends Structure {
	public short sun_family;
	public byte[] sun_path = new byte[108]; // UNIX_PATH_MAX = 108

	@Override
	protected java.util.List<String> getFieldOrder() {
		return java.util.Arrays.asList("sun_family", "sun_path");
	}
}