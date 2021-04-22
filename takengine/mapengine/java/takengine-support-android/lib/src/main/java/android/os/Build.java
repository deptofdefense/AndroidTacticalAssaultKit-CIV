package android.os;

public class Build {
    public static class VERSION {
        public static final int SDK_INT = 26;

        // XXX - workaround to prevent detection in JOGL
        private VERSION(boolean b) {}
    }

    // XXX - workaround to prevent detection in JOGL
    private Build(boolean b) {}
}
