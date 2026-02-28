package featurecreep.attach.bsd;

import com.sun.jna.Pointer;

public class NativeConstantsAttachBSD {
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
	public static int O_CREAT = 0x0200; // Octal 0400
	public static int O_EXCL = 0x0800; // Octal 02000
	public static int O_RDWR = 0x0002; // Octal 02

	// Socket constants (from macOS sys/socket.h)
	public static short PF_UNIX = 1;
	public static short SOCK_STREAM = 1;
	public static short AF_UNIX = 1;
	public static int SHUT_RDWR = 2;

	// Signals (from macOS signal.h)
	public static int SIGQUIT = 3;

	// Sysctl constants (from macOS sys/sysctl.h)
	public static int CTL_KERN = 1;
	public static int KERN_PROC = 14;
	public static int KERN_PROC_PID = 1;

	// macOS-specific constants
	public static int PATH_MAX = 1024;

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

	public static int getConstant(String name) {
		System.out.println(name);
		Pointer ptr = LIBC.getSymbol(name);
		if (ptr == null) {
			throw new RuntimeException("Constant not found: " + name);
		}
		return ptr.getInt(0);
	}
}