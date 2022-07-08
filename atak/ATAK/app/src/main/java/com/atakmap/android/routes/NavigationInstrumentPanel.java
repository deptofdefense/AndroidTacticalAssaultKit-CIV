
package com.atakmap.android.routes;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.MotionEvent;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.animations.MapWidgetAnimationInterpolatorInterface;
import com.atakmap.android.routes.animations.SpeedInterpolationProvider;
import com.atakmap.android.routes.animations.Storyboard;
import com.atakmap.android.routes.animations.StoryboardPlayer;
import com.atakmap.android.routes.nav.RoutePanelViewModel;
import com.atakmap.android.widgets.LayoutHelper;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import java.util.List;

/**
 * This is the UI that will be displayed while navigating.
 *
 * Note: This class is NOT thread-safe.
 */

public class NavigationInstrumentPanel implements
        RoutePanelViewModel.RouteNavigationStateListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        RootLayoutWidget.OnLayoutChangedListener {

    //-------------------- Fields and Properties ---------------------------

    private static final String VOICE_CUE_PREFERENCE_KEY = "useRouteVoiceCues";
    private float density = MapView.DENSITY;
    private MarkerIconWidget volumeWidget;
    private MarkerIconWidget billboardWidget;
    private boolean isdistanceAndETACheckpointBased = true;
    private boolean isInAutoSkipMode = true;

    private RoutePanelViewModel stateManager;

    //TODO:: Create a cue class that, so we can keep state there.
    private boolean isCueTextDismissed = false;
    private StoryboardPlayer cueTextStoryboard = null;

    public RoutePanelViewModel getStateManager() {
        return stateManager;
    }

    private MapView mapView;

    public MapView getMapView() {
        return mapView;
    }

    public void setMapView(MapView mapView) {
        this.mapView = mapView;
    }

    //-------------------- UI Fields ---------------------------

    //-------------------- Sizes ---------------------------
    private int instrumentWidgetHeight = 103;
    private int cueWidgetHeight = 103;
    private int thumbWidth = 35;
    private int rockerSize = 103;
    private int miniButtonSize = 44;
    private int cueImageSize = 72;
    private int cueTextWidth = 484;
    private int instrumentWidth = 460;
    private int instrumentHeight = 103;
    private int cueImageContainerSize = 103;

    //-------------------- Positions ---------------------------
    private int cueWidgetXPos = 27;

    //-------------------- Offsets ---------------------------
    private int cueWidgetOffset = 50;
    private float textNudge = 12; //Used to better vertically center text.
    private float scale = 1f;

    private void scaleLayoutParameters(float scale) {

        //-------------------- Scale Sizes ---------------------------
        instrumentWidgetHeight = Math.round(instrumentWidgetHeight * scale);
        cueWidgetHeight = Math.round(scale * cueWidgetHeight);
        thumbWidth = Math.round(scale * thumbWidth);
        rockerSize = Math.round(scale * rockerSize);
        miniButtonSize = Math.round(scale * miniButtonSize);
        cueImageSize = Math.round(scale * cueImageSize);
        cueTextWidth = Math.round(scale * cueTextWidth);
        instrumentWidth = Math.round(scale * instrumentWidth);
        instrumentHeight = Math.round(scale * instrumentHeight);
        cueImageContainerSize = Math.round(scale * cueImageContainerSize);

    }

    private final RootLayoutWidget rootLayoutWidget;

    private LayoutWidget instrumentLayoutWidget = null;
    private LayoutWidget speedLayoutWidget = null;
    private LayoutWidget cueLayoutWidget = null;

    private TextWidget speedTextWidget = null;
    private TextWidget speedUnitsTextWidget = null;
    private TextWidget etaTextWidget = null;
    private TextWidget distanceTextWidget = null;

    private SharedPreferences _prefs;

    //-------------------- CTOR ---------------------------
    public NavigationInstrumentPanel(MapView mapView,
            RoutePanelViewModel stateManager) {

        if (mapView == null)
            throw new IllegalArgumentException("MapView cannot be null");

        if (stateManager == null)
            throw new IllegalArgumentException("stateManager cannot be null");

        _prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);

        this.mapView = mapView;
        this.stateManager = stateManager;
        this.rootLayoutWidget = (RootLayoutWidget) mapView.getComponentExtra(
                "rootLayoutWidget");
        this.rootLayoutWidget.addOnLayoutChangedListener(this);

        //Install the UI
        addWidgets();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals(VOICE_CUE_PREFERENCE_KEY)) {
            boolean voice_cue = _prefs.getBoolean(key, true);
            if (volumeWidget != null)
                updateVolumeWidget(volumeWidget, !voice_cue);
        } else if (key.equals("route_billboard_enabled"))
            updateBillboardWidget();
    }

    //-------------------- Methods ---------------------------

    private boolean areWidgetsAdded() {
        return instrumentLayoutWidget != null;
    }

    private int getWidth() {
        return ((Activity) mapView.getContext()).getWindowManager()
                .getDefaultDisplay().getWidth();
    }

    private void addWidgets() {

        if (areWidgetsAdded())
            return;

        //-------------------- Setup Instrument Cluster ---------------------------

        //-------------------- Set our scale, if we need one ---------------------------
        float totalRenderWidth = cueWidgetXPos + cueTextWidth
                + cueImageContainerSize;
        totalRenderWidth *= density;

        float minLayoutSize;
        if (mapView.isPortrait()) {
            if (_prefs.getBoolean("nav_orientation_right", true))
                minLayoutSize = mapView.getWidth() - 160; // account for the width of the  overflow
            else
                minLayoutSize = mapView.getWidth();
        } else {
            minLayoutSize = getWidth() / 2f;
        }

        if (totalRenderWidth > minLayoutSize) {
            float availableSpace = minLayoutSize * .98f;
            scale = availableSpace / totalRenderWidth;
            scaleLayoutParameters(scale);
            Log.d("NavUI", "Setting scale to " + scale);
            //Scale all of our sizes.
        }

        instrumentLayoutWidget = buildInstrumentCluster(
                instrumentHeight, instrumentWidth);
        rootLayoutWidget.addWidget(instrumentLayoutWidget);
        invalidatePosition();

        //Start listening for events.
        stateManager.setListener(this);
    }

    /**
     * Position the nav widget so that it makes the most use of the space
     * available in the top-left corner
     */
    private void invalidatePosition() {
        if (instrumentLayoutWidget == null)
            return;

        // Setup layout helper with all top-aligned views + widgets
        Rect mapRect = new Rect(0, 0, mapView.getWidth(), mapView.getHeight());
        List<Rect> bounds = rootLayoutWidget.getOccupiedBounds();
        LayoutHelper layoutHelper = new LayoutHelper(mapRect, bounds);

        // Setup instrument box bounds
        float cueOffset = (cueWidgetHeight + cueWidgetOffset) * density;
        Rect instBounds = LayoutHelper.getBounds(instrumentLayoutWidget);
        instBounds.bottom += cueOffset;

        // Find the best position aligned to the top-right
        instBounds = layoutHelper.findBestPosition(instBounds,
                RootLayoutWidget.TOP_LEFT);

        float top = instBounds.top + (instrumentWidgetHeight * density);
        instrumentLayoutWidget.setPoint(instBounds.left, top);

        // Move cue/waypoint layout directly below
        if (cueLayoutWidget != null)
            cueLayoutWidget.setPoint(instBounds.left, top + cueOffset);
    }

    public void removeWidgets() {
        stateManager.setListener(null);
        clearCue();

        if (rootLayoutWidget != null) {

            if (instrumentLayoutWidget != null) {
                rootLayoutWidget.removeWidget(instrumentLayoutWidget);
                instrumentLayoutWidget = null;
            }
        }
    }

    private void clearCue() {
        if (cueLayoutWidget != null) {
            if (cueTextStoryboard != null) {
                cueTextStoryboard.cancel(true);
                cueTextStoryboard = null;
            }
            rootLayoutWidget.removeWidget(cueLayoutWidget);
        }
    }

    private void showCue(int displayDirection, String cueText) {
        showCue(displayDirection, cueText, true);
    }

    private void showCue(int displayDirection, String cueText,
            boolean isDismissable) {
        clearCue();

        //We're likely going to be chunking this up, so line separators won't be respected.
        cueText = cueText.replace(System.getProperty("line.separator"), " ");

        Context context = MapView.getMapView().getContext();

        int textPadding = (int) (34 * scale);

        cueLayoutWidget = new LayoutWidget();

        //-------------------- Direction Icon ---------------------------

        MarkerIconWidget arrow = buildDirectionalWidget(context,
                displayDirection, cueImageSize);
        float arrowX = ((cueImageContainerSize - cueImageSize) / 2f) * density;
        float arrowY = (-cueImageContainerSize * density) + arrowX;
        arrow.setPoint(arrowX, arrowY); //It's a square

        LayoutWidget directionRoot = new LayoutWidget();
        directionRoot.setSize(cueImageContainerSize * density,
                cueImageContainerSize * density);
        directionRoot.setBackingColor(Color.argb((int) (255 * .8), 34, 34, 34));
        directionRoot.setPoint(0, 0);
        directionRoot.addWidget(arrow);

        cueLayoutWidget.addWidget(directionRoot);

        //-------------------- Text Root ---------------------------
        final LayoutWidget textRoot = new LayoutWidget();
        textRoot.setSize(cueTextWidth * density, cueImageContainerSize
                * density);
        textRoot.setBackingColor(Color.argb((int) (255 * .8), 48, 48, 48));
        textRoot.setPoint(cueImageContainerSize * density, 0);

        //-------------------- Cue Text ---------------------------
        final TextWidget textWidget = buildCueTextWidget(cueText, cueTextWidth,
                cueImageContainerSize,
                textPadding, textPadding / 2);
        textWidget.setColor(Color.argb(255, 128, 203, 196));
        float[] cueSize = textWidget.getSize(true, false);
        textWidget.setPoint(textPadding * density,
                -cueSize[1] - (textRoot.getHeight() - cueSize[1]) / 2);
        textRoot.addWidget(textWidget);

        //-------------------- Thumb ---------------------------
        final MarkerIconWidget thumbWidget = buildMarkerWidget(
                R.drawable.nav_thumb, .8f, thumbWidth, cueImageContainerSize,
                0, 0, 60, 0);
        float thumbX = (cueTextWidth - thumbWidth) * density;
        float thumbY = (-cueImageContainerSize * density);
        thumbWidget.setPoint(thumbX, thumbY); //It's a square
        textRoot.addWidget(thumbWidget);

        //-------------------- Collapse/Expand Listener ---------------------------

        if (isDismissable) {
            final MapWidget.OnClickListener ocl = new MapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(MapWidget widget,
                        MotionEvent event) {

                    if (cueTextStoryboard != null) {
                        cueTextStoryboard.cancel(true);
                        cueTextStoryboard = null;
                    }

                    if (isCueTextDismissed) {
                        cueTextStoryboard = buildShowCueStoryboard(textRoot,
                                textWidget, thumbWidget, cueTextWidth, 255,
                                (int) (255 * .8))
                                        .start();
                    } else {
                        cueTextStoryboard = buildHideCueStoryBoard(textRoot,
                                textWidget, thumbWidget).start();
                    }

                    isCueTextDismissed = !isCueTextDismissed;
                }
            };
            thumbWidget.addOnClickListener(ocl);
            textWidget.addOnClickListener(ocl);
            arrow.addOnClickListener(ocl);
        }

        cueLayoutWidget.addWidget(textRoot);
        rootLayoutWidget.addWidget(cueLayoutWidget);
        invalidatePosition();
    }

    private SpeedInterpolationProvider buildWidgetWidthInterpolator(
            final LayoutWidget widget, final MapWidget thumb, float to,
            float speed) {
        return new SpeedInterpolationProvider(to, speed) {
            @Override
            public float getCurrentValue() {
                return widget.getWidth() / density;
            }

            @Override
            public void setNewValue(float newValue) {
                widget.setSize(newValue * density,
                        widget.getHeight());

                thumb.setPoint((newValue - thumbWidth) * density,
                        -widget.getHeight());
            }
        };
    }

    private SpeedInterpolationProvider buildWidgetOpacityInterpolator(
            final TextWidget widget, float to, float speed) {
        return new SpeedInterpolationProvider(to, speed) {
            @Override
            public float getCurrentValue() {
                return Color.alpha(widget.getColor());
            }

            @Override
            public void setNewValue(float newValue) {
                int currentColor = widget.getColor();
                int mask = 0x00FFFFFF;
                int newColor = currentColor & mask;
                newColor = ((int) newValue << 24) | newColor;
                //int newColor =
                /*int newColor = Color.argb((int) newValue,
                        Color.red(currentColor), Color.green(currentColor),
                        Color.blue(currentColor));*/
                widget.setColor(newColor);
            }
        };
    }

    private SpeedInterpolationProvider buildLayoutBackOpacityInterpolator(
            final LayoutWidget widget, float to, float speed) {
        return new SpeedInterpolationProvider(to, speed) {
            @Override
            public float getCurrentValue() {
                return Color.alpha(widget.getBackingColor());
            }

            @Override
            public void setNewValue(float newValue) {
                int currentColor = widget.getBackingColor();
                int mask = 0x00FFFFFF;
                int newColor = currentColor & mask;
                newColor = ((int) newValue << 24) | newColor;
                //int newColor =
                /*int newColor = Color.argb((int) newValue,
                        Color.red(currentColor), Color.green(currentColor),
                        Color.blue(currentColor));*/
                widget.setBackingColor(newColor);
            }
        };
    }

    //Speeds are pixels/bytes per ms in this case.
    private float textFadeSpeed = 1;
    private float textSlideSpeed = 1.9f;
    private float textLayoutOpacitySpeed = 1;

    private Storyboard buildHideCueStoryBoard(LayoutWidget textRoot,
            TextWidget textWidget, MapWidget thumbWidget) {

        MapWidgetAnimationInterpolatorInterface layoutDimAnimation = buildLayoutBackOpacityInterpolator(
                textRoot, 0, textLayoutOpacitySpeed);

        MapWidgetAnimationInterpolatorInterface textDimAnimation = buildWidgetOpacityInterpolator(
                textWidget, 0, textFadeSpeed);

        MapWidgetAnimationInterpolatorInterface slideAnimation = buildWidgetWidthInterpolator(
                textRoot, thumbWidget, thumbWidth, textSlideSpeed);

        Storyboard storyboard = new Storyboard(
                null,
                /*layoutDimAnimation,*/textDimAnimation, slideAnimation);

        return storyboard;

    }

    private Storyboard buildShowCueStoryboard(LayoutWidget textRoot,
            TextWidget textWidget, MapWidget thumb, int width, int textOpacity,
            int layoutOpacity) {

        MapWidgetAnimationInterpolatorInterface layoutDimAnimation = buildLayoutBackOpacityInterpolator(
                textRoot, layoutOpacity, textLayoutOpacitySpeed);

        MapWidgetAnimationInterpolatorInterface textDimAnimation = buildWidgetOpacityInterpolator(
                textWidget, textOpacity, textFadeSpeed);

        MapWidgetAnimationInterpolatorInterface slideAnimation = buildWidgetWidthInterpolator(
                textRoot, thumb, width, textSlideSpeed);

        Storyboard storyboard = new Storyboard(
                null,
                /*layoutDimAnimation,*/slideAnimation)
                        .continueWith(new Storyboard(null, textDimAnimation));

        return storyboard;

    }

    private MarkerIconWidget buildDirectionalWidget(Context context,
            int direction, int size) {
        int imgId;

        switch (direction) {
            case RoutePanelViewModel.STRAIGHT: {
                imgId = R.drawable.nav_straight;
                break;
            }
            case RoutePanelViewModel.LEFT: {
                imgId = R.drawable.nav_left;
                break;
            }
            case RoutePanelViewModel.SLIGHT_LEFT: {
                imgId = R.drawable.nav_slight_left;
                break;
            }
            case RoutePanelViewModel.SHARP_LEFT: {
                imgId = R.drawable.nav_sharp_left;
                break;
            }
            case RoutePanelViewModel.SLIGHT_RIGHT: {
                imgId = R.drawable.nav_slight_right;
                break;
            }
            case RoutePanelViewModel.RIGHT: {
                imgId = R.drawable.nav_right;
                break;
            }
            case RoutePanelViewModel.SHARP_RIGHT: {
                imgId = R.drawable.nav_sharp_right;
                break;
            }
            case RoutePanelViewModel.START: {
                imgId = R.drawable.nav_sp_icon;
                break;
            }
            case RoutePanelViewModel.END: {
                imgId = R.drawable.nav_vdo_icon;
                break;
            }
            default: {
                imgId = R.drawable.nav_straight;
                break;
            }
        }

        return buildMarkerWidget(imgId, 1, size, size, 0, 0, 0, 0);
    }

    private Icon buildMarkerIcon(int resourceID, float alpha, int width,
            int height) {
        Context context = mapView.getContext();

        final String uri = "android.resource://"
                + context.getPackageName() + "/"
                + resourceID;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT,
                Color.argb((int) (255 * alpha), 255, 255, 255));
        builder.setSize(width, height);

        builder.setImageUri(Icon.STATE_DEFAULT, uri);
        return builder.build();
    }

    private Icon buildMiniIcon(int resourceID, float alpha) {
        return buildMarkerIcon(resourceID, alpha, miniButtonSize,
                miniButtonSize);
    }

    private Icon buildMiniIcon(int resourceID) {
        return buildMiniIcon(resourceID, 1);
    }

    /**
     * Build a marker widget given a resource and other parameters.
     * @param resourceID the graphic for the widget.
     * @param opaqueness the opaqueness for the icon.
     */
    private MarkerIconWidget buildMarkerWidget(final int resourceID,
            final float opaqueness, final int width, final int height,
            final int padLeft, final int padTop, final int padRight,
            final int padBottom) {
        MarkerIconWidget marker = new MarkerIconWidget() {
            @Override
            public void setMarkerHitBounds(int left, int top, int right,
                    int bottom) {
                // enlarge the touch space of the close button
                Icon icon = getIcon();
                if (icon == null) {
                    super.setMarkerHitBounds(left, top, right, bottom);
                } else {
                    super.setMarkerHitBounds(left - padLeft, top - padTop,
                            right + padRight, bottom + padBottom);
                }

            }
        };

        marker.setIcon(buildMarkerIcon(resourceID, opaqueness, width, height));
        return marker;
    }

    private TextWidget buildCueTextWidget(String text, int width, int height,
            int horizontalPadding, int verticalPadding) {

        float availableWidthInDp = (width - (horizontalPadding * 2)) * density;
        float availableHeightInDp = (height - (verticalPadding * 2)) * density;

        int desiredFontSize = 48;
        int actualFontSize = getBestFontSizeForText(text, Typeface.DEFAULT,
                desiredFontSize, availableWidthInDp, availableHeightInDp);
        MapTextFormat formatter = new MapTextFormat(Typeface.DEFAULT,
                actualFontSize);
        String clampedText = constrainText(text, formatter, availableWidthInDp);
        return new TextWidget(clampedText, formatter, false);
    }

    private int getBestFontSizeForText(String text, Typeface typeface,
            int preferredFontSize, float horizontalConstraint,
            float verticalConstraint) {

        MapTextFormat formatter = new MapTextFormat(typeface,
                preferredFontSize);
        String constrainedText = constrainText(text, formatter,
                horizontalConstraint);

        if (formatter.measureTextHeight(constrainedText) <= verticalConstraint
                &&
                formatter.measureTextWidth(
                        constrainedText) <= horizontalConstraint)
            return preferredFontSize;

        int head = 1;
        int tail = preferredFontSize;
        int ptr = head + ((tail - head) / 2);

        while (ptr > head && ptr < tail) {
            //Assume the tail and head have been checked, we just need to know which direction gets us warmer to the sweet spot
            formatter = new MapTextFormat(typeface, ptr);
            constrainedText = constrainText(text, formatter,
                    horizontalConstraint);

            if (formatter
                    .measureTextHeight(constrainedText) > verticalConstraint
                    ||
                    formatter.measureTextWidth(
                            constrainedText) > horizontalConstraint) {
                tail = ptr;
            } else {
                head = ptr;
            }

            ptr = head + ((tail - head) / 2);
        }

        //return head;
        return ptr;

    }

    private String constrainText(String text, MapTextFormat formatter,
            float horizontalConstraintInDp) {
        String[] words = text.split(" ");
        float spaceWidth = formatter.getCharWidth(' ');
        String newlineString = System.getProperty("line.separator");
        StringBuilder sb = new StringBuilder();

        float currentWidth = 0;

        for (String word : words) {
            float width = formatter.measureTextWidth(word);
            float spaceFactor = currentWidth > 0 ? spaceWidth : 0;

            if ((currentWidth + spaceFactor
                    + width) > horizontalConstraintInDp) {
                if (sb.length() > 0) {
                    sb.append(newlineString);
                }
                sb.append(word);
                currentWidth = width;
            } else {
                sb.append(" ");
                sb.append(word);
                currentWidth += spaceWidth + width;
            }
        }

        return sb.toString();
    }

    private MarkerIconWidget buildEtaAndDistanceRockerSwitch(final int width,
            final int height) {

        int switchId = isdistanceAndETACheckpointBased
                ? R.drawable.nav_cp_mode_toggle
                : R.drawable.nav_vdo_mode_toggle;

        final MarkerIconWidget rocker = buildMarkerWidget(switchId, .8f, width,
                height, 0, 0, 0, 0);
        rocker.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {

                //Flip the switch
                isdistanceAndETACheckpointBased = !isdistanceAndETACheckpointBased;
                double newDistance;

                if (isdistanceAndETACheckpointBased) {
                    rocker.setIcon(
                            buildMarkerIcon(R.drawable.nav_cp_mode_toggle, .8f,
                                    width, height));

                    newDistance = stateManager.getDistanceToNextWaypoint();
                } else {
                    rocker.setIcon(
                            buildMarkerIcon(R.drawable.nav_vdo_mode_toggle,
                                    .8f,
                                    width, height));

                    newDistance = stateManager.getDistanceToVdo();
                }

                int units = stateManager.getUnits();
                Pair<Double, String> newFormattedDistance = stateManager
                        .getFormattedDistance(newDistance, units);
                double averageSpeed = stateManager
                        .getAverageSpeedInMetersPerSecond();
                String newEta = stateManager
                        .getFormattedEstimatedTimeOfArrival(
                                newDistance, averageSpeed);
                if (newFormattedDistance != null) {
                    updateEtaAndDistance(newEta,
                            getStringValue(newFormattedDistance.first) + " "
                                    + newFormattedDistance.second);
                }
            }
        });

        return rocker;

    }

    private LayoutWidget buildInstrumentCluster(int height,
            int width) {

        int speedWidgetSize = (int) (height * 1.20); //Increase speed widget size by 20%

        //-------------------- Backdrop ---------------------------
        MarkerIconWidget backdrop = new MarkerIconWidget();
        backdrop.setIcon(buildMarkerIcon(R.drawable.nav_display_backdrop, 1,
                width, height));

        float markerTop = -height;
        markerTop += (speedWidgetSize - height) / 2f;
        backdrop.setPoint(0, markerTop * density);

        backdrop.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
                stateManager.toggleUnits();
            }
        });

        LayoutWidget root = new LayoutWidget();
        root.addWidget(backdrop);

        //-------------------- ETA and Distance ---------------------------
        etaTextWidget = new TextWidget("", new MapTextFormat(Typeface.DEFAULT,
                (int) (36 * scale)), false);
        distanceTextWidget = new TextWidget("", new MapTextFormat(
                Typeface.DEFAULT, (int) (36 * scale)), false);

        root.addWidget(etaTextWidget);
        root.addWidget(distanceTextWidget);

        //-------------------- Speed ---------------------------
        speedLayoutWidget = buildSpeedWidget(speedWidgetSize,
                speedWidgetSize);
        float speedX = (((width - speedWidgetSize)) / 2.0f) * density;
        float speedY = -height * density;
        speedLayoutWidget.setPoint(speedX, speedY);
        root.addWidget(speedLayoutWidget);

        //-------------------- Reference Toggle ---------------------------
        MarkerIconWidget rocker = buildEtaAndDistanceRockerSwitch(rockerSize,
                rockerSize);
        rocker.setPoint(backdrop.getWidth() - (rocker.getWidth() * 0.47f),
                backdrop.getPointY());

        root.addWidget(rocker);

        //-------------------- Build Skip Buttons ---------------------------
        float miniButtonYPos = -miniButtonSize;
        miniButtonYPos += (miniButtonSize / 2.0f) * density;

        int miniButtonXOffset = 38;

        final MarkerIconWidget prevWidget = buildMiniButton(
                R.drawable.nav_left_manual, 0.8f);
        final MarkerIconWidget nextWidget = buildMiniButton(
                R.drawable.nav_right_manual, 0.8f);

        prevWidget.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {

                if (isInAutoSkipMode) {
                    isInAutoSkipMode = false;
                    prevWidget.setIcon(buildMiniIcon(
                            R.drawable.nav_left_manual));
                    nextWidget.setIcon(buildMiniIcon(
                            R.drawable.nav_right_manual));
                }

                if (stateManager != null)
                    stateManager.broadcastPreviousWaypointIntent();
            }
        });

        nextWidget.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {

                if (isInAutoSkipMode) {
                    isInAutoSkipMode = false;
                    prevWidget.setIcon(buildMiniIcon(
                            R.drawable.nav_left_manual));
                    nextWidget.setIcon(buildMiniIcon(
                            R.drawable.nav_right_manual));
                }

                if (stateManager != null)
                    stateManager.boradcastNextWaypointIntent();
            }
        });

        prevWidget.setPoint(miniButtonXOffset * density, miniButtonYPos);
        nextWidget.setPoint((width - miniButtonXOffset - miniButtonSize)
                * density, miniButtonYPos);

        root.addWidget(prevWidget);
        root.addWidget(nextWidget);

        root.setSize(backdrop.getWidth() + rockerSize, backdrop.getHeight());

        return root;
    }

    private void updateEtaAndDistance(String eta, String distance) {
        LayoutWidget root = (LayoutWidget) etaTextWidget.getParent();
        float width = root.getWidth() - rockerSize;
        float height = root.getHeight();
        float segmentWidth = (width - speedLayoutWidget.getWidth()) / 2.0f;

        if (eta != null) {
            etaTextWidget.setText(eta);
            float[] etaSize = etaTextWidget.getSize(true, false);
            float etaX = (width - segmentWidth)
                    + ((segmentWidth - etaSize[0]) / 2f);
            float etaY = -height + textNudge;
            etaY += (height - etaSize[1]) / 2f;
            etaTextWidget.setPoint(etaX, etaY);
        }

        if (distance != null) {
            distanceTextWidget.setText(distance);
            float[] distSize = distanceTextWidget.getSize(true, false);
            float distanceX = (segmentWidth - distSize[0]) / 2f;
            float distanceY = -height + textNudge;
            distanceY += (height - distSize[1]) / 2f;
            distanceTextWidget.setPoint(distanceX, distanceY);
        }
    }

    private void updateVolumeWidget(MarkerIconWidget widget,
            boolean useMuteIcon) {
        int volumeResourceId = useMuteIcon ? R.drawable.nav_mute
                : R.drawable.nav_speak;
        widget.setIcon(buildMiniIcon(volumeResourceId));
    }

    private void updateBillboardWidget() {
        if (billboardWidget != null) {
            boolean enabled = _prefs.getBoolean("route_billboard_enabled",
                    true);
            billboardWidget.setIcon(buildMiniIcon(enabled
                    ? R.drawable.nav_billboard
                    : R.drawable.nav_billboard_disabled,
                    enabled ? 1.0f : 0.8f));
        }
    }

    /** 
     * Create the mini marker icon widget for the small circles.
     */
    private MarkerIconWidget createMiniMarkerIconWidget() {
        return new MarkerIconWidget() {
            @Override
            public void setMarkerHitBounds(int left, int top, int right,
                    int bottom) {
                // enlarge the touch space of the close button
                super.setMarkerHitBounds((int) (-1 * (miniButtonSize / 1.5d)),
                        (int) (-1 * (miniButtonSize / 1.5d)),
                        (int) (miniButtonSize + (miniButtonSize / 1.5d)),
                        (int) (miniButtonSize + (miniButtonSize / 1.5d)));

            }
        };

    }

    private MarkerIconWidget buildVolumeWidget(boolean useMuteIcon) {
        MarkerIconWidget widget = createMiniMarkerIconWidget();
        widget.setName("VolumeWidget");
        updateVolumeWidget(widget, useMuteIcon);
        return widget;
    }

    private LayoutWidget buildSpeedWidget(int height,
            int width) {
        LayoutWidget root = new LayoutWidget();
        root.setSize(width * density, height * density);

        MarkerIconWidget speedBackDropWidget = new MarkerIconWidget();
        speedBackDropWidget.setName("SpeedInstrumentCluster");
        speedBackDropWidget.setIcon(buildMarkerIcon(
                R.drawable.nav_speed_backdrop, 1,
                width, height));

        root.addWidget(speedBackDropWidget);

        speedTextWidget = new TextWidget("", new MapTextFormat(
                Typeface.DEFAULT, (int) (48 * scale)), false);

        speedUnitsTextWidget = new TextWidget("", new MapTextFormat(
                Typeface.DEFAULT, (int) (18 * scale)), false);

        float midButtonY = (height - miniButtonSize / 2.0f) * density;

        //-------------------- Build Exit Nav Button ---------------------------
        final MarkerIconWidget exitNavWidget = new MarkerIconWidget() {
            @Override
            public void setMarkerHitBounds(int left, int top, int right,
                    int bottom) {
                // enlarge the touch space of the close button
                super.setMarkerHitBounds(-1 * miniButtonSize,
                        -1 * miniButtonSize,
                        miniButtonSize, miniButtonSize);
            }
        };

        exitNavWidget.setIcon(buildMiniIcon(R.drawable.nav_exit_button_img));

        exitNavWidget.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
                removeWidgets();
                stateManager.broadcastEndNavIntent();
            }
        });

        exitNavWidget.setPoint(-(miniButtonSize / 4f) * density, midButtonY);
        //exitNavWidget.setMarkerHitBounds(0, 0, closeWidgetSize, closeWidgetSize);

        //-------------------- Toggle Billboards Button ---------------------------
        billboardWidget = new MarkerIconWidget() {
            @Override
            public void setMarkerHitBounds(int l, int t, int r, int b) {
                super.setMarkerHitBounds(-1 * miniButtonSize,
                        -1 * miniButtonSize,
                        miniButtonSize, miniButtonSize);
            }
        };

        updateBillboardWidget();

        billboardWidget.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
                _prefs.edit().putBoolean("route_billboard_enabled",
                        !_prefs.getBoolean("route_billboard_enabled", true))
                        .apply();
            }
        });

        billboardWidget.setPoint((miniButtonSize / 1.075f) * density,
                midButtonY + (miniButtonSize * 0.2f) * density);

        //-------------------- Build Volume Widget ---------------------------
        boolean voice_cue = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext())
                .getBoolean(VOICE_CUE_PREFERENCE_KEY, true);

        volumeWidget = buildVolumeWidget(!voice_cue);

        volumeWidget.addOnClickListener(new MapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
                boolean voice_cue = PreferenceManager
                        .getDefaultSharedPreferences(mapView.getContext())
                        .getBoolean(VOICE_CUE_PREFERENCE_KEY, true);

                updateVolumeWidget(volumeWidget, !voice_cue);

                // need to invert this as part of the tap.
                PreferenceManager
                        .getDefaultSharedPreferences(mapView.getContext())
                        .edit()
                        .putBoolean(VOICE_CUE_PREFERENCE_KEY, !voice_cue)
                        .apply();
            }
        });

        volumeWidget.setPoint((width - (miniButtonSize * 0.75f)) * density,
                midButtonY);

        //-------------------- Build Layers ---------------------------

        root.addWidget(speedTextWidget);
        root.addWidget(speedUnitsTextWidget);
        root.addWidget(exitNavWidget);
        root.addWidget(billboardWidget);
        root.addWidget(volumeWidget);

        return root;
    }

    private MarkerIconWidget buildMiniButton(int imgResource, float opaquness) {
        MarkerIconWidget widget = createMiniMarkerIconWidget();
        widget.setIcon(buildMiniIcon(imgResource, opaquness));
        return widget;
    }

    private void updateSpeed(String speed, String units) {
        LayoutWidget layoutWidget = (LayoutWidget) speedTextWidget.getParent();
        float layoutHeight = layoutWidget.getHeight();
        float layoutWidth = layoutWidget.getWidth();

        speedTextWidget.setText(speed);
        speedUnitsTextWidget.setText(units);

        float[] speedSize = speedTextWidget.getSize(true, false);
        float[] unitsSize = speedUnitsTextWidget.getSize(true, false);

        float contentHeight = speedSize[1] + unitsSize[1];

        float top = (layoutHeight - contentHeight - textNudge) / 2.0f;

        //-------------------- Place the text ---------------------------
        speedTextWidget.setPoint((layoutWidth - speedSize[0]) / 2.0f, top);
        speedUnitsTextWidget.setPoint((layoutWidth - unitsSize[0]) / 2.0f,
                top + speedSize[1]);
    }

    private String getStringValue(double dbl) {
        long roundedValue = Math.round(dbl);
        if ((double) roundedValue == dbl) {
            return String.valueOf(roundedValue);
        } else {
            return String.valueOf(dbl);
        }
    }

    //-------------------- RouteNavigationStateListener Interface Implementation ---------------------------
    @Override
    public void onSpeedChanged(double speed, String units) {
        updateSpeed(String.valueOf(Math.round(speed)), units);
    }

    @Override
    public void onDistanceToNextWaypointChanged(double distance, String units) {
        if (isdistanceAndETACheckpointBased) {
            updateEtaAndDistance(null, getStringValue(distance) + " " + units);
        }
    }

    @Override
    public void onNavigationCueReceived(int direction, String text) {
        showCue(direction, text);
    }

    @Override
    public void onEstimatedTimeOfArrivalToNextWaypointChanged(String eta) {

        if (isdistanceAndETACheckpointBased) {
            updateEtaAndDistance(eta, null);
        }
    }

    @Override
    public void onNavigationCueCleared() {
        clearCue();
    }

    @Override
    public void onDistanceToVdoChanged(double distance, String units) {
        if (!isdistanceAndETACheckpointBased) {
            updateEtaAndDistance(null, getStringValue(distance) + " " + units);
        }
    }

    @Override
    public void onEstimatedTimeOfArrivalToVdoChanged(String eta) {
        if (!isdistanceAndETACheckpointBased) {
            updateEtaAndDistance(eta, null);
        }
    }

    @Override
    public void onLayoutChanged() {
        invalidatePosition();
    }
}
