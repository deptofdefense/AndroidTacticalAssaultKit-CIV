
package com.atakmap.android.maps;

/**
 * Engine resource reference that points to an entry inside a SQLite database
 * 
 * 
 */
public class SqliteMapDataRef extends MapDataRef {

    private final String _dbFilePath;
    private final String _selectQuery;

    /**
     * ctor
     * 
     * @param dbFilePath
     * @param selectQuery
     */
    public SqliteMapDataRef(String dbFilePath, String selectQuery) {
        _dbFilePath = dbFilePath;
        _selectQuery = selectQuery;
    }

    /**
     * Get the path to the DB file
     * 
     * @return the database file path.
     */
    public String getDBFilePath() {
        return _dbFilePath;
    }

    /**
     * Get the select query
     * 
     * @return the query
     */
    public String getSelectQuery() {
        return _selectQuery;
    }

    /**
     * Get a human readable representation
     */
    public String toString() {
        return toUri();
    }

    @Override
    public String toUri() {
        return "sqlite://" + _dbFilePath + "?query=" + _selectQuery;
    }
}
