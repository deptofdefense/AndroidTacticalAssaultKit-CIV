package com.atakmap.database;

public final class Databases {
    private Databases() {}

    public static boolean isSQLiteDatabase(String ignored) { throw new UnsupportedOperationException(); }
    public static DatabaseIface openOrCreateDatabase(String ignored) { throw new UnsupportedOperationException(); }
}
