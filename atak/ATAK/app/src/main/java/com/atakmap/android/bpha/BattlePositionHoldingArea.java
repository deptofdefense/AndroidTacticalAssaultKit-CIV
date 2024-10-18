
package com.atakmap.android.bpha;

public class BattlePositionHoldingArea {

    private int rows;
    private int columns;

    /**
     * Construct a BattlePositionHoldingArea with defined number or rows and columns
     * @param rows the number of rows
     * @param columns the number of columns
     */
    public BattlePositionHoldingArea(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
    }

    /**
     * Retrieve the number of rows for the specific BPHA
     * @return the number of rows
     */
    public int getRows() {
        return rows;
    }

    /**
     * Sets the number of rows for the specific BPHA
     * @param rows the number of rows
     */
    public void setRows(int rows) {
        this.rows = rows;
    }

    /**
     * Retrieve the number of columns for the specific BPHA
     * @return the number of columns
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Sets the number of columns for the specific BPHA
     * @param columns the number of columns
     */
    public void setColumns(int columns) {
        this.columns = columns;
    }
}
