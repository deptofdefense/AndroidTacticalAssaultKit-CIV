
package com.atakmap.database;

public interface DatabaseIface {
    public void execute(String sql, String[] args);

    public CursorIface query(String sql, String[] args);

    public StatementIface compileStatement(String sql);

    public QueryIface compileQuery(String sql);

    public boolean isReadOnly();

    public void close();

    public int getVersion();

    public void setVersion(int version);

    public void beginTransaction();

    public void setTransactionSuccessful();

    public void endTransaction();

    public boolean inTransaction();
}
