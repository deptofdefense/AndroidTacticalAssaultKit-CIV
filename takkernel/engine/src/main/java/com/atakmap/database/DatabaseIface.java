
package com.atakmap.database;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface DatabaseIface {


    /**
     * Execute a single SQL statement that is NOT a SELECT or any other SQL statement that returns data.
     * @param sql the sql statement
     * @param args the arguments to be utilized during execution.
     */
    public void execute(String sql, String[] args);

    /**
     * Runs the provided SQL and returns a cursor over the result set.
     * @param sql the sql statement
     * @param args the arguments to be utilized during execution
     * @return the cursor interface
     */
    public CursorIface query(String sql, String[] args);

    /**
     * Compiles an SQL statement that is NOT a SELECT or any other SQL statement that returns data
     * into a reusable pre-compiled statement object.
     * @return the compiled statement.
     */
    public StatementIface compileStatement(String sql);

    /**
     * Compiles an SQL statement into a reusable pre-compiled statement object.
     * @param sql the statement
     * @return the compiled query
     */
    public QueryIface compileQuery(String sql);

    /**
     * Returns the state of the database.
     * @return true if the database is read only
     */
    public boolean isReadOnly();

    /**
     * Closes the database.
     */
    public void close();

    /**
     * Returns the version of the database.
     * @return a number indicating the version of the database.
     */
    public int getVersion();

    /**
     * Modifies the database version to reflect the version number passed.
     * @param version the version
     */
    public void setVersion(int version);

    /**
     * Begins a transaction for database manipulation.
     */
    public void beginTransaction();

    /**
     * Sets the state of the transaction to successful.
     */
    public void setTransactionSuccessful();

    /**
     * Ends a transaction for database manipulation.
     */
    public void endTransaction();

    /**
     * Get the current state of the transaction
     * @return true if the transaction is in progress.
     */
    public boolean inTransaction();
}
