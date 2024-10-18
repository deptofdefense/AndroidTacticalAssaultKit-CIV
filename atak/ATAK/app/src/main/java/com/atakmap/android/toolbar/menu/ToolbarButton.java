
package com.atakmap.android.toolbar.menu;

import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class ToolbarButton implements OnGestureListener {
    private final List<OnClickListener> onClickListeners = new ArrayList<>();
    private final List<OnLongClickListener> onLongClickListeners = new ArrayList<>();
    private final List<OnSwipeListener> onSwipeListeners = new ArrayList<>();
    private final ToolbarMenuManager manager = ToolbarMenuManager.getInstance();
    private String menuIdentifier;
    private Button _button;

    public interface OnSwipeListener {
        void OnSwipe(View v);
    }

    public ToolbarButton(String menuIdentifier, View view) {
        if (view instanceof Button) {
            this.initialize(menuIdentifier, (Button) view);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public ToolbarButton(Button button) {
        this.initialize("", button);
    }

    public void dispose() {
        onClickListeners.clear();
        onLongClickListeners.clear();
        onSwipeListeners.clear();
        _button.setOnTouchListener(null);
    }

    public Button getButton() {
        return _button;
    }

    public void initialize(String menuIdentifier, Button button) {

        _button = button;

        final GestureDetector detector = new GestureDetector(
                button.getContext(), this);

        button.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                detector.onTouchEvent(event);
                return true;
            }
        });

        // show arrow to indicate a menu, only if one exist
        if (manager.hasMenu(menuIdentifier))
            this.updateBackground();

        this.menuIdentifier = menuIdentifier;
    }

    private void onSwipeListener(View v) {
        for (OnSwipeListener l : onSwipeListeners) {
            l.OnSwipe(v);
        }

        this.showMenu();
    }

    /**
     * Allow a tool bar button to have one or more click listeners.
     * @param listener the click listener.
     */
    public void addOnClickListener(OnClickListener listener) {
        onClickListeners.add(listener);
    }

    public void addOnLongClickListener(OnLongClickListener listener) {
        onLongClickListeners.add(listener);
    }

    public void addSwipeListener(OnSwipeListener listener) {
        onSwipeListeners.add(listener);
    }

    public void updateButton(Drawable[] icons, String label) {
        _button.setCompoundDrawablesWithIntrinsicBounds(icons[0], icons[1],
                icons[2],
                icons[3]);
        _button.setText(label);
    }

    public void setLaunchableMenu(String identifier) {
        this.menuIdentifier = identifier;
        updateBackground();
    }

    public void setLaunchableMenu(IMenu menu) {
        this.menuIdentifier = menu.getIdentifier();
        updateBackground();
    }

    public void showMenu() {
        if (menuIdentifier != null && menuIdentifier.length() != 0) {
            int[] buttonLocation = new int[2];
            _button.getLocationInWindow(buttonLocation);
            manager.setActiveToolbarButton(this);
            manager.showMenu(menuIdentifier, _button.getLeft(),
                    buttonLocation[1] + _button.getHeight() + 10);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        for (OnLongClickListener l : onLongClickListeners) {
            l.onLongClick(null);
        }
        this.showMenu();
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
        if (e2.getX() - e1.getX() > 50) {
            onSwipeListener(null);
        } else {
            manager.closeMenu();
        }
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (onClickListeners.size() == 0)
            this.showMenu();

        for (OnClickListener l : onClickListeners) {
            l.onClick(null);
        }
        return false;
    }

    public void addOnClickListeners(List<OnClickListener> listeners) {
        onClickListeners.addAll(listeners);
    }

    public void resetOnClickListeners() {
        onClickListeners.clear();
    }

    public void updateBackground() {
        // Drawable drawable = this.getContext().getResources().getDrawable(
        // R.drawable.togglearrowbutton);
        // this.setBackgroundDrawable(drawable);
    }
}
