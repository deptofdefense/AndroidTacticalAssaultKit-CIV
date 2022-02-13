package gov.tak.platform.ui;

public final class UIEventQueue {
    public static void invokeLater(Runnable runnable) {
        java.awt.EventQueue.invokeLater(runnable);
    }

    public static boolean isCurrentThread() {
        return java.awt.EventQueue.isDispatchThread();
    }
}

