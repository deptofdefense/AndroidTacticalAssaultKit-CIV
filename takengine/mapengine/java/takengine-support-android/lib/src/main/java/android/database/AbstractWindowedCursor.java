package android.database;

public abstract class AbstractWindowedCursor extends AbstractCursor {
    protected CursorWindow mWindow;
    protected int mPos = -1;
    private boolean closed;

    public abstract boolean onMove(int oldPosition, int newPosition);
    public abstract void setWindow(CursorWindow window);

    @Override
    public boolean moveToNext() {
        if(mPos < getCount())
            mPos++;
        return mPos < getCount();
    }

    @Override
    public long getLong(int col) {
        return mWindow.getLong(mPos, col);
    }

    @Override
    public String getString(int col) {
        return mWindow.getString(mPos, col);
    }

    @Override
    public double getDouble(int col) {
        return mWindow.getDouble(mPos, col);
    }

    @Override
    public int getInt(int col) {
        return mWindow.getInt(mPos, col);
    }

    @Override
    public int getType(int col) {
        return mWindow.getType(mPos, col);
    }

    @Override
    public byte[] getBlob(int col) {
        return mWindow.getBlob(mPos, col);
    }

    @Override
    public boolean isNull(int col) {
        return mWindow.isNull(mPos, col);
    }

    @Override
    public int getColumnCount() {
        return mWindow.getColumnCount();
    }

    public boolean isClosed() {
        return closed;
    }
    public void close() {
        if(closed)
            return;
        closed = true;
        if(this.mWindow != null) {
            this.mWindow.close();
            this.mWindow = null;
        }
    }
}
