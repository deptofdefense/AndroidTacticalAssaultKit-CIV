
package com.atakmap.android.mapcompass;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;

import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;

/**
 * Map widget that shows direction of North.
 */
class CompassArrowWidget extends MarkerIconWidget {

    private static final int xIconAnchor = 0;
    private static final int yIconAnchor = 0;

    private static final int xSizeSmall = 36; // Icon.SIZE_DEFAULT;
    private static final int ySizeSmall = 36; // Icon.SIZE_DEFAULT;

    private static final int xSize = 50; // Icon.SIZE_DEFAULT;
    private static final int ySize = 50; // Icon.SIZE_DEFAULT;

    // use a smaller compass if the screen is small
    private static final int smallScreenWidth = 480;
    private static boolean smallScreen = false;

    private final Icon arrowIcon;
    private final Icon userArrowIcon;
    private final Icon userArrowLockedIcon;
    private final MapView _mapView;

    /**
     * 
     */
    CompassArrowWidget(MapView view) {
        _mapView = view;
        setName("Compass Arrow");

        String imageUri = "android.resource://"
                + _mapView.getContext().getPackageName()
                + "/" + R.drawable.northarrow;
        arrowIcon = createIcon(imageUri, _mapView.getContext());
        setIcon(arrowIcon);

        imageUri = "android.resource://"
                + _mapView.getContext().getPackageName()
                + "/" + R.drawable.northarrowrotate;
        userArrowIcon = createIcon(imageUri, _mapView.getContext());

        imageUri = "android.resource://"
                + _mapView.getContext().getPackageName()
                + "/" + R.drawable.northarrowrotatelocked;
        userArrowLockedIcon = createIcon(imageUri, _mapView.getContext());
    }

    private Icon createIcon(final String imageUri, final Context con) {
        Point p = new Point();
        ((Activity) con).getWindowManager().getDefaultDisplay().getSize(p);
        // determine if the screen is small
        smallScreen = p.x <= smallScreenWidth;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(xIconAnchor, yIconAnchor);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);

        if (smallScreen)
            builder.setSize(xSizeSmall, ySizeSmall);
        else
            builder.setSize(xSize, ySize);

        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

        return builder.build();
    }

    public void setMode(MapMode m, boolean locked) {
        if (m == MapMode.USER_DEFINED_UP && !locked) {
            setIcon(userArrowIcon);
        } else if (m == MapMode.USER_DEFINED_UP) {
            setIcon(userArrowLockedIcon);
        } else {
            setIcon(arrowIcon);
        }
    }

    @Override
    public boolean isEnterable() {
        return true;
    }
}
