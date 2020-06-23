
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class ScalingModeCommand extends Command {
    enum Mode {
        ABSTRACT,
        METRIC
    }

    private Mode mode;
    private double metricScalingFactor;

    public ScalingModeCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int mod = l > 0 ? makeEnum() : 0;
        if (mod == 0) {
            this.mode = Mode.ABSTRACT;
        } else if (mod == 1) {
            this.mode = Mode.METRIC;
            this.metricScalingFactor = makeFloatingPoint();

            // make sure all the arguments were read
            if (BuildConfig.DEBUG && this.currentArg != this.args.length)
                throw new AssertionError();
        }
    }

    public Mode getMode() {
        return this.mode;
    }

    /**
     * The scaling factor, only defined if the mode is <code>METRIC</code>.
     * @return
     */
    public double getMetricScalingFactor() {
        return this.metricScalingFactor;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ScalingMode mode=").append(this.mode);
        if (this.mode.equals(Mode.METRIC)) {
            sb.append(" metricScalingFactor=").append(this.metricScalingFactor);
        }
        return sb.toString();
    }
}
