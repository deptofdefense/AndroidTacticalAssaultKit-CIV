package com.atakmap.lang;

public final class Threads {
    private Threads() {}

    public static String getStack() {
        StringBuilder retval = new StringBuilder();
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for(int i = 3; i < stack.length; i++) {
            retval.append(" -->    ");
            retval.append(stack[i].toString());
            retval.append("\n");
        }
        return retval.toString();
    }
}
