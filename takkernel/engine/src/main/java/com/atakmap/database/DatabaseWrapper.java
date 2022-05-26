package com.atakmap.database;

public class DatabaseWrapper implements DatabaseIface {
    protected DatabaseIface filter;

    public DatabaseWrapper(DatabaseIface filter) {
        this.filter = filter;
    }

    @Override
    public void execute(String sql, String[] args) {
        filter.execute(sql, args);
    }

    @Override
    public CursorIface query(String sql, String[] args) {
        return filter.query(sql, args);
    }

    @Override
    public StatementIface compileStatement(String sql) {
        return filter.compileStatement(sql);
    }

    @Override
    public QueryIface compileQuery(String sql) {
        return filter.compileQuery(sql);
    }

    @Override
    public boolean isReadOnly() {
        return filter.isReadOnly();
    }

    @Override
    public void close() {
        filter.close();
    }

    @Override
    public int getVersion() {
        return filter.getVersion();
    }

    @Override
    public void setVersion(int version) {
        filter.setVersion(version);
    }

    @Override
    public void beginTransaction() {
        filter.beginTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        filter.setTransactionSuccessful();
    }

    @Override
    public void endTransaction() {
        filter.endTransaction();
    }

    @Override
    public boolean inTransaction() {
        return filter.inTransaction();
    }
}
