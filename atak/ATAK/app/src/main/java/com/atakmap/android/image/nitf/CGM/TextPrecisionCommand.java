
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

class TextPrecisionCommand extends Command {
    enum Precision {
        STRING,
        CHARACTER,
        STROKE
    }

    private Precision precision;

    TextPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int e = makeEnum();
        switch (e) {
            case 0:
                this.precision = Precision.STRING;
                break;
            case 1:
                this.precision = Precision.CHARACTER;
                break;
            case 2:
                this.precision = Precision.STROKE;
                break;
            default:
                this.precision = Precision.STRING;
                unsupported("unsupported text precision " + e);
        }

        unimplemented("TextPrecision");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TextPrecision");
        return sb.toString();
    }
}
