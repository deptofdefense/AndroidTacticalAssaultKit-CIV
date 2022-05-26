
package com.atakmap.database;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
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
