
package com.atakmap.android.widgets;

import android.view.MotionEvent;

import com.atakmap.android.maps.MapView;
import com.atakmap.math.MathUtils;

import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.platform.marshal.MarshalManager;

/**
 * Parent widget with functions similar to a ScrollLayout view
 */
public class ScrollLayoutWidget extends LinearLayoutWidget {

    private static final String TAG = "ScrollLayoutWidget";

    public interface OnScrollChangedListener {
        void onScrollChanged(ScrollLayoutWidget layout, float scroll);
    }

    private float _scroll = 0;
    private float _startScroll = 0;
    private float _startPos = 0;
    private boolean _scrolling = false;
    private MapWidget _onPressWidget = null, _onClickWidget = null;
    private final ConcurrentLinkedQueue<OnScrollChangedListener> _onScrollListeners = new ConcurrentLinkedQueue<>();

    public ScrollLayoutWidget() {
        super(WRAP_CONTENT, MATCH_PARENT, VERTICAL);
    }

    public ScrollLayoutWidget(int paramWidth, int paramHeight,
            int orientation) {
        super(paramWidth, paramHeight, orientation);
    }

    public void setScroll(float scroll) {
        float maxScroll = Math.max(0,
                _orientation == HORIZONTAL ? _childrenWidth - _width
                        : _childrenHeight - _height);
        scroll = MathUtils.clamp(scroll, 0, maxScroll);
        if (Float.compare(_scroll, scroll) != 0) {
            _scroll = scroll;
            for (OnScrollChangedListener l : _onScrollListeners)
                l.onScrollChanged(this, scroll);
        }
    }

    public float getScroll() {
        return _scroll;
    }

    public void addOnScrollListener(OnScrollChangedListener l) {
        _onScrollListeners.add(l);
    }

    public void removeOnScrollListener(OnScrollChangedListener l) {
        _onScrollListeners.remove(l);
    }

    @Override
    public void setOrientation(int orientation) {
        if (_orientation != orientation)
            setScroll(0);
        super.setOrientation(orientation);
    }

    @Override
    public boolean setSize(float width, float height) {
        boolean ret = super.setSize(width, height);
        setScroll(_scroll);
        return ret;
    }

    @Override
    public void onPress(android.view.MotionEvent event) {
        _onClickWidget = null;
        if (_onPressWidget != null)
            _onPressWidget.onPress(MarshalManager.marshal(event,
                    android.view.MotionEvent.class,
                    gov.tak.platform.ui.MotionEvent.class));
        _startScroll = _scroll;
        _startPos = _orientation == HORIZONTAL ? event.getX()
                : event.getY();
    }

    @Override
    public boolean onMove(android.view.MotionEvent event) {
        float pos = _orientation == HORIZONTAL ? event.getX() : event.getY();
        final gov.tak.platform.ui.MotionEvent pevent = MarshalManager.marshal(
                event, android.view.MotionEvent.class,
                gov.tak.platform.ui.MotionEvent.class);
        if (_onPressWidget == null || Math.abs(_startPos - pos) > 5f
                * MapView.DENSITY) {
            _scrolling = true;
            if (_onPressWidget != null)
                _onPressWidget.onUnpress(pevent);
            _onPressWidget = null;
        }
        if (_scrolling) {
            setScroll(_startScroll + (_startPos - pos));
            super.onMove(pevent);
        } else if (_onPressWidget != null)
            _onPressWidget.onMove(pevent);
        return true;
    }

    @Override
    public void onClick(android.view.MotionEvent event) {
        if (_onClickWidget != null)
            _onClickWidget.onClick(MarshalManager.marshal(event,
                    android.view.MotionEvent.class,
                    gov.tak.platform.ui.MotionEvent.class));
        _onClickWidget = null;
    }

    @Override
    public void onLongPress() {
        if (_onPressWidget != null)
            _onPressWidget.onLongPress();
    }

    @Override
    public void onUnpress(android.view.MotionEvent event) {
        _scrolling = false;
        if (_onPressWidget != null) {
            _onPressWidget.onUnpress(MarshalManager.marshal(event,
                    android.view.MotionEvent.class,
                    gov.tak.platform.ui.MotionEvent.class));
            _onClickWidget = _onPressWidget;
        }
        _onPressWidget = null;
    }

    @Override
    public MapWidget seekHit(MotionEvent event, float x, float y) {
        if (_scrolling)
            return this;
        if (isVisible() && testHit(x, y)) {
            if (event != null && event.getAction() == MotionEvent.ACTION_DOWN)
                _onPressWidget = super.seekHit(event, x, y);
            return this;
        }
        return null;
    }

    // For testing vertical and horizontal scroll layouts
    /*public static void test() {
        MapView mapView = MapView.getMapView();
        RootLayoutWidget root = (RootLayoutWidget)
                mapView.getComponentExtra("rootLayoutWidget");
        final ScrollLayoutWidget scroll = new ScrollLayoutWidget(
                MATCH_PARENT, WRAP_CONTENT, HORIZONTAL);
        scroll.setName("Scroll Layout Test");
        scroll.setBackingColor(0x60000000);
        scroll.setPadding(16f, 16f, 16f, 16f);
    
        if (scroll.getOrientation() == VERTICAL) {
            MapTextFormat mtf = MapView.getTextFormat(Typeface.DEFAULT, 2);
            for (int i = 1; i <= 50; i++) {
                TextWidget t = new TextWidget("List Item #" + i, mtf, false);
                t.addOnClickListener(testOnClick);
                t.addOnLongPressListener(testOnLongClick);
                scroll.addWidget(t);
            }
            root.getLayout(RootLayoutWidget.RIGHT_EDGE).addWidget(scroll);
        } else {
            final String imageUri = "android.resource://"
                    + mapView.getContext().getPackageName() + "/"
                    + R.drawable.bloodhound_widget;
    
            Icon.Builder builder = new Icon.Builder();
            builder.setAnchor(0, 0);
            builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
            builder.setSize(48, 48);
            builder.setImageUri(Icon.STATE_DEFAULT, imageUri);
            Icon icon = builder.build();
            for (int i = 1; i <= 50; i++) {
                MarkerIconWidget m = new MarkerIconWidget();
                m.setName("Icon #" + i);
                m.setIcon(icon);
                m.addOnClickListener(testOnClick);
                m.addOnLongPressListener(testOnLongClick);
                scroll.addWidget(m);
            }
            root.getLayout(RootLayoutWidget.TOP_EDGE).addWidget(scroll);
        }
    }
    
    private static final OnClickListener testOnClick = new OnClickListener() {
        @Override
        public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
            MapView mv = MapView.getMapView();
            if (mv != null)
                Toast.makeText(mv.getContext(), "Clicked " + widget.getName(),
                        Toast.LENGTH_SHORT).show();
        }
    };
    
    private static final OnLongPressListener testOnLongClick = new OnLongPressListener() {
        @Override
        public void onMapWidgetLongPress(MapWidget widget) {
            MapView mv = MapView.getMapView();
            if (mv != null)
                Toast.makeText(mv.getContext(), "Long-clicked " + widget.getName(),
                        Toast.LENGTH_SHORT).show();
        }
    };*/
}
