
package com.atakmap.android.bpha;

import org.junit.Assert;
import org.junit.Test;

public class BattlePositionHoldingAreaTest {

    @Test
    public void getRows() {
        BattlePositionHoldingArea bpha = new BattlePositionHoldingArea(2, 4);
        Assert.assertEquals("getRows == 2", bpha.getRows(), 2);
    }

    @Test
    public void setRows() {
        BattlePositionHoldingArea bpha = new BattlePositionHoldingArea(2, 4);
        bpha.setRows(4);
        Assert.assertEquals("getRows == 4", bpha.getRows(), 4);
    }

    @Test
    public void getColumns() {
        BattlePositionHoldingArea bpha = new BattlePositionHoldingArea(2, 4);
        Assert.assertEquals("getColumns == 4", bpha.getColumns(), 4);
    }

    @Test
    public void setColumns() {
        BattlePositionHoldingArea bpha = new BattlePositionHoldingArea(2, 4);
        bpha.setColumns(2);
        Assert.assertEquals("getColumns == 2", bpha.getColumns(), 2);
    }
}
