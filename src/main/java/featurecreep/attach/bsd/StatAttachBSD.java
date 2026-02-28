package featurecreep.attach.bsd;

import com.sun.jna.Structure;

public class StatAttachBSD extends Structure {
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
	public  java.util.List<String> getFieldOrder() {
		return java.util.Arrays.asList("st_dev", "st_mode", "st_nlink", "st_ino", "st_uid", "st_gid", "st_rdev",
				"st_atime", "st_mtime", "st_ctime", "st_size", "st_blocks", "st_blksize", "st_flags", "st_gen",
				"st_lspare", "st_qspare1", "st_qspare2");
	}
}