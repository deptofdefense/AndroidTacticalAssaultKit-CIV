
package com.atakmap.android.dropdown;

import androidx.fragment.app.Fragment;
import gov.tak.api.util.Disposable;

import android.view.View;

import com.atakmap.coremap.log.Log;

public final class DropDown implements Disposable {

    public static final String TAG = "DropDown";

    public interface OnStateListener {
        void onDropDownSelectionRemoved();

        void onDropDownClose();

        // the drop down size change in percentage of the screen.
        void onDropDownSizeChanged(double width, double height);

        void onDropDownVisible(boolean v);
    }

    private final Fragment fragment;

    final private boolean ignoreBackButton;
    private boolean switchable = false;
    private boolean _visible = false;
    private boolean _prompt = false;
    private boolean _closeBeforeTool = false;
    private DropDownSide _side;

    enum DropDownSide {
        RIGHT_SIDE,
        LEFT_SIDE
    }

    private double _widthFraction = .5;
    private double _heightFraction = 1;
    private OnStateListener listener;
    private final DropDownReceiver ddr;

    DropDown(final Fragment fragment, boolean ignoreBackButton,
            final DropDownReceiver ddr) {
        this.fragment = fragment;
        this.ignoreBackButton = ignoreBackButton;
        _side = DropDownSide.RIGHT_SIDE;
        this.ddr = ddr;
    }

    DropDown(View v, boolean ignoreBackButton, final DropDownReceiver ddr) {
        fragment = new GenericFragmentAdapter();
        ((GenericFragmentAdapter) fragment).setView(v);
        this.ignoreBackButton = ignoreBackButton;
        _side = DropDownSide.RIGHT_SIDE;
        this.ddr = ddr;
    }

    @Override
    public void dispose() {
        // Cleanup the view reference if it hasn't been already
        if (fragment instanceof GenericFragmentAdapter)
            ((GenericFragmentAdapter) fragment).removeView();
    }

    DropDownSide getSide() {
        return _side;
    }

    void setSide(DropDownSide side) {
        // XXX - This does not address how the actual side is switched.
        _side = side;
    }

    /**
     * Is this drop down side switchable
     */
    boolean isSwitchable() {
        return switchable;
    }

    void setSwitchable(final boolean switchable) {
        this.switchable = switchable;
    }

    /**
     * Set whether the drop down should close first when back button
     * is pressed and a tool is active
     * @param closeFirst True to close before tool on back button
     */
    public void setCloseBeforeTool(boolean closeFirst) {
        _closeBeforeTool = closeFirst;
    }

    boolean closeBeforeTool() {
        return _closeBeforeTool;
    }

    void setOnStateListener(OnStateListener onCloseListener) {
        listener = onCloseListener;
    }

    public void close() {
        try {
            if (listener != null)
                listener.onDropDownClose();
        } catch (Exception e) {
            Log.d(TAG, "error occurred closing: " + this.getClass(), e);
        }
    }

    boolean ignoreBackButton() {
        return ignoreBackButton;
    }

    Fragment getFragment() {
        return fragment;
    }

    boolean isVisible() {
        return _visible;
    }

    void setDimensions(double widthFraction, double heightFraction) {
        _widthFraction = widthFraction;
        _heightFraction = heightFraction;
        if (listener != null)
            listener.onDropDownSizeChanged(widthFraction, heightFraction);
    }

    double getWidthFraction() {
        return _widthFraction;
    }

    double getHeightFraction() {
        return _heightFraction;
    }

    public void setPromptEnabled(boolean b) {
        _prompt = b;
    }

    public boolean isPromptEnabled() {
        return _prompt;
    }

    void notifyStateRequested(int state) {
        ddr.onStateRequested(state);
    }

    void setVisibility(boolean b) {
        if (_visible != b) {
            _visible = b;
            if (listener != null)
                listener.onDropDownVisible(b);
        }
    }

    void onDropDownSelectionRemoved() {
        if (listener != null)
            listener.onDropDownSelectionRemoved();

    }
}
