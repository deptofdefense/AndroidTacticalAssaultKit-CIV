
package com.atakmap.database.impl;

import android.database.SQLException;
import android.database.sqlite.SQLiteException;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.interop.Pointer;
import com.atakmap.util.ReadWriteLock;

public final class DatabaseImpl implements DatabaseIface {
    public final static int OPEN_READONLY = 0x01;
    public final static int OPEN_CREATE = 0x02;

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;

    DatabaseImpl(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
    }

    @Override
    public void execute(String sql, String[] args) {
        StatementIface stmt = null;
        try {
            stmt = compileStatement(sql);
            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++)
                    stmt.bind(i + 1, args[i]);
            }
            stmt.execute();
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            if (stmt != null)
                stmt.close();
        }
    }

    @Override
    public CursorIface query(String sql, String[] args) {
        QueryIface result = null;
        try {
            result = compileQuery(sql);
            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++)
                    result.bind(i + 1, args[i]);
            }
            final QueryIface retval = result;
            result = null;
            return retval;
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Override
    public StatementIface compileStatement(String sql) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            final Pointer retval = compileStatement(this.pointer.raw, sql);
            if (retval == null)
                return null;
            return new StatementImpl(retval, this);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public QueryIface compileQuery(String sql) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            final Pointer retval = compileQuery(this.pointer.raw, sql);
            if (retval == null)
                return null;
            return new QueryImpl(retval, this);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean isReadOnly() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return isReadOnly(this.pointer.raw);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void close() {
        this.rwlock.acquireWrite();
        try {
            if (this.pointer.raw != 0L)
                destruct(this.pointer);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    @Override
    public int getVersion() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getVersion(this.pointer.raw);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setVersion(int version) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            setVersion(this.pointer.raw, version);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void beginTransaction() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            beginTransaction(this.pointer.raw);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setTransactionSuccessful() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            setTransactionSuccessful(this.pointer.raw);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void endTransaction() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            endTransaction(this.pointer.raw);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean inTransaction() {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            return inTransaction(this.pointer.raw);
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    protected final void finalize() {
        if (this.pointer.raw != 0L)
            Log.w("DatabaseImpl", "Cursor leaked");
    }

    public static DatabaseIface open(String path, boolean readOnly) {
        return open(path, null, readOnly ? OPEN_READONLY : 0);
    }

    public static DatabaseIface openOrCreate(String path) {
        return open(path, null, OPEN_CREATE);
    }

    public static DatabaseIface open(String path, String passphrase,
            int flags) {
        try {
            final Pointer retval = openImpl(path, passphrase, flags);
            return (retval != null) ? new DatabaseImpl(retval, null) : null;
        } catch (Throwable t) {
            throw (SQLException) new SQLiteException().initCause(t);
        }
    }

    static native Pointer openImpl(String path, String passphrase, int flags);

    static native void destruct(Pointer ptr);

    static native Pointer compileStatement(long ptr, String sql);

    static native Pointer compileQuery(long ptr, String sql);

    static native boolean isReadOnly(long ptr);

    static native int getVersion(long ptr);

    static native void setVersion(long ptr, int version);

    static native void beginTransaction(long ptr);

    static native void setTransactionSuccessful(long ptr);

    static native void endTransaction(long ptr);

    static native boolean inTransaction(long ptr);
}
