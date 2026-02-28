package featurecreep.attach.bsd;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

//JNA library interface
public interface CLibraryAttachBSD extends Library {
	
	
	
	public CLibraryAttachBSD INSTANCE = (CLibraryAttachBSD) Native.loadLibrary("c", CLibraryAttachBSD.class);

	// Add missing functions
	public int geteuid();

	public int getegid();

	// Socket functions
	public int socket(int domain, int type, int protocol);

	public int connect(int sockfd, Structure addr, int addrlen);

	public int close(int fd);

	public int shutdown(int sockfd, int how);

	public int read(int fd, byte[] buf, int count);

	public int write(int fd, byte[] buf, int count);

	public int stat(String path, StatAttachBSD statbuf);

	public int fstat(int fd, StatAttachBSD statbuf);

	public int chown(String path, int uid, int gid);

	public int kill(int pid, int sig);

	public int sysctl(int[] name, int namelen, Structure oldp, IntByReference oldlenp, Structure newp, int newlen);

	public int confstr(int name, byte[] buf, int len);

	public String strerror(int errnum);

	public int open(String pathname, int flags, int mode);

	// Declare getSymbol() for constant lookup
	public Pointer getSymbol(String name);

	// Declare pathconf() for PATH_MAX
	public int pathconf(String path, int name);

	public int mkdir(String absolutePath, int i);
}