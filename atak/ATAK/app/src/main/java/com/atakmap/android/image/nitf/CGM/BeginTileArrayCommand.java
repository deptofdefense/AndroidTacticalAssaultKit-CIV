
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class BeginTileArrayCommand extends Command {
    private final Point position;
    private final int cellPathDirection;
    private final int lineProgressionDirection;
    private final int nTilesInPathDirection;
    private final int nTilesInLineDirection;
    private final int nCellsPerTileInPathDirection;
    private final int nCellsPerTileInLineDirection;
    private final double cellSizeInPathDirection;
    private final double cellSizeInLineDirection;
    private final int imageOffsetInPathDirection;
    private final int imageOffsetInLineDirection;
    private final int nCellsInPathDirection;
    private final int nCellsInLineDirection;

    BeginTileArrayCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.position = makePoint();
        this.cellPathDirection = makeEnum();
        this.lineProgressionDirection = makeEnum();
        this.nTilesInPathDirection = makeInt();
        this.nTilesInLineDirection = makeInt();
        this.nCellsPerTileInPathDirection = makeInt();
        this.nCellsPerTileInLineDirection = makeInt();
        this.cellSizeInPathDirection = makeReal();
        this.cellSizeInLineDirection = makeReal();
        this.imageOffsetInPathDirection = makeInt();
        this.imageOffsetInLineDirection = makeInt();
        this.nCellsInPathDirection = makeInt();
        this.nCellsInLineDirection = makeInt();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BeginTileArray [position=");
        builder.append(this.position);
        builder.append(", cellPathDirection=");
        builder.append(this.cellPathDirection);
        builder.append(", lineProgressionDirection=");
        builder.append(this.lineProgressionDirection);
        builder.append(", nTilesInPathDirection=");
        builder.append(this.nTilesInPathDirection);
        builder.append(", nTilesInLineDirection=");
        builder.append(this.nTilesInLineDirection);
        builder.append(", nCellsPerTileInPathDirection=");
        builder.append(this.nCellsPerTileInPathDirection);
        builder.append(", nCellsPerTileInLineDirection=");
        builder.append(this.nCellsPerTileInLineDirection);
        builder.append(", cellSizeInPathDirection=");
        builder.append(this.cellSizeInPathDirection);
        builder.append(", cellSizeInLineDirection=");
        builder.append(this.cellSizeInLineDirection);
        builder.append(", imageOffsetInPathDirection=");
        builder.append(this.imageOffsetInPathDirection);
        builder.append(", imageOffsetInLineDirection=");
        builder.append(this.imageOffsetInLineDirection);
        builder.append(", nCellsInPathDirection=");
        builder.append(this.nCellsInPathDirection);
        builder.append(", nCellsInLineDirection=");
        builder.append(this.nCellsInLineDirection);
        builder.append("]");
        return builder.toString();
    }

}
