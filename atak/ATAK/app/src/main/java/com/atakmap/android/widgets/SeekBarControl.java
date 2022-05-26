
package com.atakmap.android.widgets;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.AtakMapView.OnMapViewResizedListener;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SeekBarControl implements OnMapViewResizedListener {

    static SeekBarControl instance;

    protected final MapView _mapView;
    protected final Context _context;
    private final SeekBar seekBar;
    protected Subject subject;
    private HideControlAction _hideControlAction;
    protected long timeout;

    protected SeekBarControl() {
        super();
        _mapView = MapView.getMapView();
        _context = _mapView.getContext();

        this.seekBar = ((Activity) _context).findViewById(
                R.id.map_seek_bar_control);
        this.seekBar.setMax(100);
        this.seekBar.setProgress(0);

        this.timeout = 5000L;

        this.seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startHideTimer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopHideTimer();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar,
                    int progress,
                    boolean fromUser) {
                if (subject != null)
                    subject.setValue(progress);
            }
        });
    }

    protected void show(long timeout) {
        this.timeout = timeout;
        this.seekBar.setVisibility(View.VISIBLE);
        _mapView.addOnMapViewResizedListener(this);
        onMapViewResized(_mapView);
        resetHideTimer();
    }

    @Override
    public void onMapViewResized(AtakMapView view) {
        view.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams lp = seekBar.getLayoutParams();
                lp.width = (int) (_mapView.getWidth() * 0.45f);
                seekBar.setLayoutParams(lp);
            }
        });
    }

    protected void setSubject(Subject subject) {
        // notify previous subject that its control is being dismissed and clear
        // the reference
        if (this.subject != null) {
            this.subject.onControlDismissed();
            this.subject = null;
        }
        if (subject != null)
            seekBar.setProgress(subject.getValue());
        this.subject = subject;
    }

    public void hideControl() {
        stopHideTimer();
        _mapView.removeOnMapViewResizedListener(this);
        this.seekBar.setVisibility(View.GONE);
        if (subject != null) {
            subject.onControlDismissed();
            subject = null;
        }
    }

    protected void stopHideTimer() {
        if (_hideControlAction != null) {
            _hideControlAction.cancel();
            _hideControlAction = null;
        }
    }

    protected void startHideTimer() {
        if (_hideControlAction == null) {
            _hideControlAction = new HideControlAction();
            _mapView.postDelayed(_hideControlAction, timeout);
        }
    }

    protected void resetHideTimer() {
        stopHideTimer();
        startHideTimer();
    }

    class HideControlAction implements Runnable {

        protected boolean _cancelled = false;

        @Override
        public void run() {
            if (!_cancelled) {
                _hideControlAction = null;
                hideControl();
            }
        }

        public void cancel() {
            _cancelled = true;
        }
    }

    /**************************************************************************/

    public synchronized static void show(Subject subject, long timeout) {
        if (instance == null) {
            instance = new SeekBarControl();
        }

        instance.setSubject(subject);
        instance.show(timeout);
    }

    public synchronized static void dismiss() {
        if (instance != null) {
            instance.hideControl();
        }
    }

    /**************************************************************************/

    public interface Subject {
        /**
         * Returns the current value, <code>0</code> through <code>100</code>,
         * inclusive.
         */
        int getValue();

        /**
         * Sets the current value, <code>0</code> through <code>100</code>,
         * inclusive.
         */
        void setValue(int value);

        /**
         * Invoked when the control is dismissed or otherwise switches to a new
         * subject.
         */
        void onControlDismissed();
    }
}
