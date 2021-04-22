package android.os;

public final class SystemClock {
    private SystemClock() {}

    public static long elapsedRealtime() {
        return System.currentTimeMillis();
    }
    public static long uptimeMillis() { return System.currentTimeMillis(); }
}
