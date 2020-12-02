package android.database;

public class SQLException extends RuntimeException {
    public SQLException() {}
    public SQLException(String msg) {
        super(msg);
    }
}
