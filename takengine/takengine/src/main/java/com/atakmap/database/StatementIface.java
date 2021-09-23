
package com.atakmap.database;

public interface StatementIface extends Bindable {

    /**
     * Executes the statement.
     */
    public void execute();

    /**
     * Closes the statement.
     */
    public void close();
} // StatementIface
