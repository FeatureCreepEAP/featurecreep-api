package featurecreep.api.lowlevel;

public enum OS {

    LINUX,
    WINDOWS,
    MAC,
    BSD,
    SOLARIS,
    AIX,
    UNKNOWN;

    private static final OS CURRENT = detect();

    public static OS current() {
        return CURRENT;
    }

    private static OS detect() {
        String name = System.getProperty("os.name").toLowerCase();

        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return MAC;
        }
        if (name.contains("freebsd")
                || name.contains("openbsd")
                || name.contains("netbsd")
                || name.contains("dragonfly")
                || name.contains("bsd")) {
            return BSD;
        }
        if (name.contains("nux") || name.contains("nix")) {
            return LINUX;
        }
        if (name.contains("aix")) {
            return AIX;
        }
        if (name.contains("sunos") || name.contains("solaris")) {
            return SOLARIS;
        }

        return UNKNOWN;
    }

    public boolean isUnixLike() {
        return this == LINUX
                || this == MAC
                || this == BSD
                || this == SOLARIS
                || this == AIX;
    }
}