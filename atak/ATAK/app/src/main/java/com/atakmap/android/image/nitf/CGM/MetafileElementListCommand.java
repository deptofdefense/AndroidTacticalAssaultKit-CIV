
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class MetafileElementListCommand extends Command {
    String[] metaFileElements;

    public MetafileElementListCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        int nElements = makeInt();

        this.metaFileElements = new String[nElements];
        for (int i = 0; i < nElements; i++) {
            int code1 = makeIndex();
            int code2 = makeIndex();
            if (code1 == -1) {
                switch (code2) {
                    case 0:
                        this.metaFileElements[i] = "DRAWING SET";
                        break;
                    case 1:
                        this.metaFileElements[i] = "DRAWING PLUS CONTROL SET";
                        break;
                    case 2:
                        this.metaFileElements[i] = "VERSION 2 SET";
                        break;
                    case 3:
                        this.metaFileElements[i] = "EXTENDED PRIMITIVES SET";
                        break;
                    case 4:
                        this.metaFileElements[i] = "VERSION 2 GKSM SET";
                        break;
                    case 5:
                        this.metaFileElements[i] = "VERSION 3 SET";
                        break;
                    case 6:
                        this.metaFileElements[i] = "VERSION 4 SET";
                        break;
                    default:
                        unsupported("unsupported meta file elements set "
                                + code2);
                }
            } else {
                // note: here, we can easily determine if a class/element is implemented or not
                StringBuilder sb = new StringBuilder();
                sb.append(" (").append(code1).append(",").append(code2)
                        .append(")");
                this.metaFileElements[i] = sb.toString();
            }
        }

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MetafileElementList ");
        for (String element : this.metaFileElements) {
            sb.append(element).append(" ");
        }
        return sb.toString();
    }
}
