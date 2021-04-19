
package com.atakmap.android.jumpbridge;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;

public class Options {
    public enum Unit {
        KILOMETERS(R.string.km_units),
        MILES(R.string.miles_units),
        NAUTICAL_MILES(
                R.string.nm_units),
        MILES_PER_HOUR(
                R.string.mph_units),
        KILOMETERS_PER_HOUR(R.string.kmph_units),
        KNOTS(R.string.knots_units),
        FPS(
                R.string.fps_units),
        MAGNETIC_NORTH(R.string.mz_units),
        FEET_DIP(
                R.string.feet_above_dip),
        FEET_AGL(R.string.feet_above_ground),
        MGRS(R.string.mgrs),
        LAT_LON(
                R.string.degrees_full),
        GR_PLANNED(
                R.string.planned_gr_alt),
        GR_DIP(R.string.gr_to_dip),
        GR_REF_PLANNED(R.string.color_ag_planned_gr),
        GR_REF_DEST(
                R.string.color_ag_gr_dip),
        UNITLESS(R.string.unitless);
        private final String name;

        Unit(int v) {
            if (v == R.string.mz_units) {
                name = Angle.DEGREE_SYMBOL
                        + MapView.getMapView().getContext().getResources()
                                .getString(v);
            } else {
                name = MapView.getMapView().getContext().getResources()
                        .getString(v);
            }

        }

        public String displayName() {
            return name;
        }
    }

    public enum FieldOptions {
        RANGE(R.string.range_jm),
        SPEED(R.string.groundspeed_jm),
        HEADING(
                R.string.heading_jm),
        BEARING(R.string.bearing_jm),
        GR_TO_DEST(
                R.string.gr_dest_jm),
        GLIDE_RATIO(R.string.glideratio_jm),
        ALTITUDE(
                R.string.altitude_jm),
        POINTER(R.string.pointer_jm),
        LOCATION(
                R.string.currentlocation_jm),
        ETA(R.string.eta_jm),
        X_TRACK(
                R.string.xtrack_jm),
        PROJ_DIST(R.string.proj_dist_jm);
        private final String name;

        FieldOptions(int v) {
            name = MapView.getMapView().getContext().getResources()
                    .getString(v);
        }

        public String displayName() {
            return name;
        }
    }
}
