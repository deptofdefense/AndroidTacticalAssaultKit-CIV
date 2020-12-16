
package com.atakmap.database.android;

import com.atakmap.database.StatementIface;

import android.database.sqlite.SQLiteStatement;

final class AndroidStatementAdapter implements StatementIface {
    public final static int STMT_TYPE_INSERT = 0;
    public final static int STMT_TYPE_UPDATE = 1;
    public final static int STMT_TYPE_DELETE = 2;
    public final static int STMT_TYPE_SELECT = 3;
    public final static int STMT_TYPE_OTHER = 4;

    private final SQLiteStatement impl;
    private final int type;

    public AndroidStatementAdapter(SQLiteStatement impl, int type) {
        this.impl = impl;

        switch (type) {
            case STMT_TYPE_INSERT:
            case STMT_TYPE_UPDATE:
            case STMT_TYPE_DELETE:
            case STMT_TYPE_OTHER:
                break;
            case STMT_TYPE_SELECT:
            default:
                throw new IllegalArgumentException();
        }

        this.type = type;
    }

    @Override
    public void execute() {
        switch (this.type) {
            case STMT_TYPE_INSERT:
                this.impl.executeInsert();
                break;
            case STMT_TYPE_UPDATE:
            case STMT_TYPE_DELETE:
                this.impl.executeUpdateDelete();
                break;
            case STMT_TYPE_OTHER:
                this.impl.execute();
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void bind(int idx, byte[] value) {
        this.impl.bindBlob(idx, value);
    }

    @Override
    public void bind(int idx, int value) {
        this.impl.bindLong(idx, value);
    }

    @Override
    public void bind(int idx, long value) {
        this.impl.bindLong(idx, value);
    }

    @Override
    public void bind(int idx, double value) {
        this.impl.bindDouble(idx, value);
    }

    @Override
    public void bind(int idx, String value) {
        this.impl.bindString(idx, value);
    }

    @Override
    public void bindNull(int idx) {
        this.impl.bindNull(idx);
    }

    @Override
    public void clearBindings() {
        this.impl.clearBindings();
    }

    @Override
    public void close() {
        this.impl.close();
    }
}
