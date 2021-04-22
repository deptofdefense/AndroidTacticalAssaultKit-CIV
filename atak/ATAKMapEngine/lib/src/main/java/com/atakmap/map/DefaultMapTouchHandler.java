package com.atakmap.map;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.atakmap.coremap.maps.coords.GeoPoint;

import android.util.DisplayMetrics;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class DefaultMapTouchHandler implements MapTouchHandler {

    protected final AtakMapView map;
    protected final AtakMapController controller;

    private final Set<MapTouchListener> touchListeners;

    private CustomLongPress customLongPress = new CustomLongPress();
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestures;

    private float scaleFactor;

    
    public DefaultMapTouchHandler(AtakMapView view) {
        this.map = view;
        this.controller = this.map.getMapController();
        
        gestureDetector = new GestureDetector(this.map.getContext(), new TouchGestures(this.map));
        scaleGestures = new ScaleGestureDetector(this.map.getContext(), new PinchZoomScaleGesture(view.getMapController()));

        // set scale factor for pinch-zoom according to screen density
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        if( metrics.densityDpi <= DisplayMetrics.DENSITY_HIGH)
            scaleFactor = 1.0f;
        else
            scaleFactor = metrics.density;
        
        this.touchListeners = Collections.newSetFromMap(new IdentityHashMap<MapTouchListener, Boolean>());
    }

    public synchronized void addMapTouchListener(MapTouchListener l) {
        this.touchListeners.add(l);
    }
    
    public synchronized void removeMapTouchListener(MapTouchListener l) {
        this.touchListeners.remove(l);
    }
    
    protected synchronized final boolean dispatchOnTouchEvent(MotionEvent event) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onTouchEvent(this.map, event))
                return true;
        return false;
    }
    
    protected synchronized final boolean dispatchOnScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onScroll(this.map, e1, e2, distanceX, distanceY))
                return true;
        return false;
    }

    protected synchronized final boolean dispatchOnDown(MotionEvent event) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onDown(this.map, event))
                return true;
        return false;
    }
    
    protected synchronized final boolean dispatchOnFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onFling(this.map, e1, e2, velocityX, velocityY))
                return true;
        return false;
    }
    
    protected synchronized final boolean dispatchOnDoubleTap(MotionEvent e) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onDoubleTap(this.map, e))
                return true;
        return false;
    }
    
    protected synchronized final boolean dispatchOnLongPress(MotionEvent evt) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onLongPress(this.map, evt))
                return true;
        return false;
    }
    
    protected synchronized final boolean dispatchOnSingleTapUp(MotionEvent evt) {
        for(MapTouchListener l : this.touchListeners)
            if(l.onSingleTapUp(this.map, evt))
                return true;
        return false;
    }
    
    /**************************************************************************/
    // Map Touch Handler

    @Override
    public boolean onTouch(AtakMapView view, MotionEvent event) {
        // Here we want to handle the overlays in reverse order
        if(this.dispatchOnTouchEvent(event))
            return true;

        this.scaleGestures.onTouchEvent(event);

        if(this.scaleGestures.isInProgress() && event.getPointerCount() != 2)
            this.scaleGestures = new ScaleGestureDetector(view.getContext(), new PinchZoomScaleGesture(this.controller));
        this.gestureDetector.onTouchEvent(event);
        return true;
    }
    
    /**************************************************************************/
    
    /**************************************************************************/

    private static boolean isEventOnMap(AtakMapView map, final float x, final float y){
        return (x >= 0 && x < map.getWidth() &&
                y >= 0 && y < map.getHeight());
    }

    /**************************************************************************/
    private enum LongPressState
    {
        CHECK_LONG_PRESS,
        IS_LONG_PRESS,
        NOT_LONG_PRESS
    }
    
    // handle long press event for pixel drift
    private class CustomLongPress
    {
        private final static int minDurationTime = 1000;
        private final static int maxAdditionalTime = 3000;
        private final static float basePixels = 10.0f;
        
        private GeoPoint startLocation;
        private LongPressState currentState;
        private long minThresholdTime;
        private long maxThresholdTime;
        private float maxThresholdPixels;
        private float totalDistX, totalDistY;
        
        public CustomLongPress()
        {
            this.startLocation = null;
            this.currentState = LongPressState.NOT_LONG_PRESS;
        }
        
        public void startChecking( MotionEvent evt)
        {
            // save start location
            this.startLocation =
                    DefaultMapTouchHandler.this.map.inverse(evt.getX(), evt.getY(), AtakMapView.InverseMode.Model).get();
            
            // init current state to check for long press
            currentState = LongPressState.CHECK_LONG_PRESS;
            
            // init min and max time thresholds relative to current time
            minThresholdTime = android.os.SystemClock.elapsedRealtime() + minDurationTime;
            maxThresholdTime = minThresholdTime + maxAdditionalTime;
            
            // init max pixel threshold factoring pixel density of device
            maxThresholdPixels = basePixels * scaleFactor;
            
            // init total pixel drift
            totalDistX = 0;
            totalDistY = 0;
        }
        
        public void processLongPress( MotionEvent evt)
        {
            if( isEventOnMap(DefaultMapTouchHandler.this.map, evt.getX(),evt.getY()) )
            {
                // is long press event
                currentState = LongPressState.IS_LONG_PRESS;
                
                if( DefaultMapTouchHandler.this.dispatchOnLongPress(evt) ) {
                    DefaultMapTouchHandler.this.map.invalidate();
                }
            }
        }
        
        public void notLongPressEvent()
        {
            // set current state to signal not a long press event
            currentState = LongPressState.NOT_LONG_PRESS;
        }
        
        public boolean isLongPressEvent( MotionEvent evt, float distanceX, float distanceY)
        {
            // process long press
            switch( currentState)
            {
            case IS_LONG_PRESS:
                // long press event was handled
                return true;
                
            case CHECK_LONG_PRESS:
                // check if not one pointer for event
                if( evt.getPointerCount() != 1)
                {
                    // not a long press event
                    notLongPressEvent();
                }
                else
                {
                    // add distance change to total where positive and negative
                    // movement values offset to handle finger rotation
                    totalDistX += distanceX;
                    totalDistY += distanceY;
                    
                    // check if absolute distance or time exceeds max threshold
                    if( (Math.abs(totalDistX) > maxThresholdPixels) || 
                        (Math.abs(totalDistY) > maxThresholdPixels) || 
                        (android.os.SystemClock.elapsedRealtime() >= maxThresholdTime))
                    {   
                        // not a long press event
                        notLongPressEvent();
                    }
                    else if( android.os.SystemClock.elapsedRealtime() >= minThresholdTime && this.startLocation != null)
                    {
                        // convert start location to map pixels at the current zoom
                        final PointF startPoint = DefaultMapTouchHandler.this.map.forward(this.startLocation);
                        
                        // overwrite event location with starting point
                        evt.setLocation( startPoint.x, startPoint.y);
                        
                        // process long press event
                        processLongPress( evt);
                    }
                }
                
            // fall through to this case
            case NOT_LONG_PRESS:
            default:
                // not a long press event
                return false;
            }
        }
    }
    
    private class TouchGestures extends GestureDetector.SimpleOnGestureListener {
        private final AtakMapView map;

        TouchGestures(AtakMapView map) {
            this.map = map;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent evt) {
            if( !isEventOnMap(this.map, evt.getX(),evt.getY()) )
                return false;
            
            if( DefaultMapTouchHandler.this.dispatchOnSingleTapUp(evt) ) {
                DefaultMapTouchHandler.this.map.invalidate();
                return true;
            }
            return false;
        }

        @Override
        public void onLongPress(MotionEvent evt) {
            // process long press event
            customLongPress.processLongPress( evt);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if( !isEventOnMap(this.map, e.getX(),e.getY()) )
                return false;
            
            if( DefaultMapTouchHandler.this.dispatchOnDoubleTap(e) ) {
                DefaultMapTouchHandler.this.map.invalidate();
                return true;
            }
            
            // Zoom on any point tapped (snap to integer of zoom)
            //final double zoomLevel = TATouchController.this.mapView.getMapView().getZoomLevelAsDouble();
            //zoomByAbout( Math.floor(zoomLevel) + 1, e.getX(), e.getY());
            // XXX - 
            DefaultMapTouchHandler.this.controller.zoomBy(2, e.getX(), e.getY(), false);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if( DefaultMapTouchHandler.this.dispatchOnFling(e1, e2, velocityX, velocityY) ) {
                DefaultMapTouchHandler.this.map.invalidate();
                return true;
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent event) {
            // start checking for long press event
            customLongPress.startChecking( event);
            
            if( DefaultMapTouchHandler.this.dispatchOnDown(event) ) {
                return true;
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if( DefaultMapTouchHandler.this.dispatchOnScroll(e1, e2, distanceX, distanceY) ) {
                DefaultMapTouchHandler.this.map.invalidate();
                return true;
            }

            // XXX - 
            final boolean lockMapScroll = false;
            if (!scaleGestures.isInProgress() && !lockMapScroll)
            {
                // check if this is not a long press event
                if( !customLongPress.isLongPressEvent( e2, distanceX, distanceY))
                    DefaultMapTouchHandler.this.controller.panBy(distanceX, distanceY, false);
            }
            return true;
        }
    }

    /**************************************************************************/
    
    private class PinchZoomScaleGesture extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private final AtakMapController controller;

        PinchZoomScaleGesture(AtakMapController controller) {
            this.controller = controller;
        }
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            // not a long press event
            customLongPress.notLongPressEvent();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            this.controller.zoomBy(detector.getScaleFactor(),
                                   detector.getFocusX(),
                                   detector.getFocusY(),
                                   false);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {}
    }
    
    public static interface MapTouchListener {
        public boolean onTouchEvent(AtakMapView map, MotionEvent event);
        public boolean onScroll(AtakMapView map, MotionEvent e1, MotionEvent e2, float distanceX, float distanceY);
        public boolean onDown(AtakMapView map, MotionEvent event);
        
        public boolean onFling(AtakMapView map, MotionEvent e1, MotionEvent e2, float velocityX, float velocityY);
        public boolean onDoubleTap(AtakMapView map, MotionEvent e);
        public boolean onLongPress(AtakMapView map, MotionEvent evt);
        public boolean onSingleTapUp(AtakMapView map, MotionEvent evt);
    }
}
