
package com.atakmap.android.navigationstack;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

public class DropDownNavigationStack extends DropDownReceiver
        implements DropDown.OnStateListener {

    private final List<NavigationStackView> _viewStack = new ArrayList<>();
    private final Context _context;
    private final Resources _resources;
    private View _rootView;
    private TextView _toolbarTitle;
    private LinearLayout _toolbarButtons;
    private FrameLayout _viewContainer;
    private ImageButton _closeButton;
    private double lwFraction;
    private double lhFraction;
    private double pwFraction;
    private double phFraction;
    private final boolean ignoreBackButton;
    private boolean _reopening;

    public DropDownNavigationStack(MapView mapView) {
        this(mapView, THIRD_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT);
    }

    public DropDownNavigationStack(MapView mapView, double lwFraction,
            double lhFraction,
            double pwFraction, double phFraction) {
        this(mapView, lwFraction, lhFraction, pwFraction, phFraction, false);
    }

    public DropDownNavigationStack(MapView mapView, double lwFraction,
            double lhFraction,
            double pwFraction, double phFraction, boolean ignoreBackButton) {
        super(mapView);
        _context = mapView.getContext();
        _resources = _context.getResources();

        this.lwFraction = lwFraction;
        this.lhFraction = lhFraction;
        this.pwFraction = pwFraction;
        this.phFraction = phFraction;

        this.ignoreBackButton = ignoreBackButton;

        initializeContainer();
    }

    private void showRootView() {
        showDropDown(
                _rootView,
                lwFraction,
                lhFraction,
                pwFraction,
                phFraction,
                ignoreBackButton,
                this);
    }

    /**
     * Return true if there is no more items on the navigation stack.
     * @return true if the view stack of size 1.
     */
    public boolean isRootView() {
        return _viewStack.size() == 1;
    }

    /**
     * Push a view to this drop-down's navigation stack
     * If the view is already in the stack this will have no effect
     * @param view View to add
     */
    public void pushView(NavigationStackView view) {
        // Make sure the drop-down is made visible
        if (!isVisible() && !isClosed()) {
            if (DropDownManager.getInstance().isTopDropDown(this)) {
                // Unhide the collapsed drop-down
                unhideView();
            } else {
                // Reopen the drop-down on top
                _reopening = true;
                showRootView();
            }
        } else if (isClosed()) {
            // Show the drop-down
            if (_viewStack.isEmpty())
                showRootView();
        }

        if (!_viewStack.contains(view)) {
            if (view instanceof NavigationStackManager)
                ((NavigationStackManager) view).setNavigationStack(this);
            _viewStack.add(0, view);
            showView(view);
        }
    }

    public void popView() {
        popView(true);
    }

    private void popView(boolean updateDisplay) {
        if (_viewStack.size() > 0) {
            NavigationStackView removedView = _viewStack.remove(0);
            removedView.onClose();

            if (_viewStack.size() == 0) {
                closeDropDown();
            } else {
                if (updateDisplay) {
                    NavigationStackView topView = _viewStack.get(0);
                    showView(topView);
                }
            }
        }
    }

    /**
     * removal all views off of the view stack.
     */
    public void popToRootView() {
        while (_viewStack.size() > 1) {
            popView(true);
        }
    }

    public void hideView() {
        hideDropDown();
    }

    public void unhideView() {
        unhideDropDown();
    }

    @Override
    protected void resize(double widthFraction, double heightFraction) {
        if (isPortrait()) {
            pwFraction = widthFraction;
            phFraction = heightFraction;
        } else {
            lwFraction = widthFraction;
            lhFraction = heightFraction;
        }
        super.resize(widthFraction, heightFraction);
    }

    @Override
    protected void disposeImpl() {
        _toolbarTitle = null;
        _toolbarButtons = null;
        _closeButton = null;
        _rootView = null;
    }

    @Override
    public boolean onBackButtonPressed() {
        boolean handled = false;

        if (_viewStack.size() > 0) {
            handled = _viewStack.get(0).onBackButton();
            if (!handled) {
                popView();
                handled = true;
            }
        }
        return handled;
    }

    @Override
    public void onDropDownSelectionRemoved() {
        if (_viewStack.size() > 0) {
            NavigationStackView view = _viewStack.get(0);
            if (view instanceof DropDown.OnStateListener) {
                ((DropDown.OnStateListener) view).onDropDownSelectionRemoved();
            }
        }
    }

    @Override
    public void onDropDownClose() {
        if (!_reopening && _viewStack.size() > 0) {
            NavigationStackView view = _viewStack.get(0);
            if (view instanceof DropDown.OnStateListener) {
                ((DropDown.OnStateListener) view).onDropDownClose();
            }

            //programtically closed so clean up the nav stack, e.g DropDownManager.closeAllDropDowns()
            closeNavigationStack();
        }
        _reopening = false;
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
        if (_viewStack.size() > 0) {
            NavigationStackView view = _viewStack.get(0);
            if (view instanceof DropDown.OnStateListener) {
                ((DropDown.OnStateListener) view).onDropDownSizeChanged(width,
                        height);
            }
        }
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (_viewStack.size() > 0) {
            NavigationStackView view = _viewStack.get(0);
            if (view instanceof DropDown.OnStateListener) {
                ((DropDown.OnStateListener) view).onDropDownVisible(v);
            }
        }
    }

    private void initializeContainer() {
        if (_rootView == null) {
            _rootView = LayoutInflater.from(_context)
                    .inflate(R.layout.nav_stack_root, null);
            _toolbarTitle = _rootView
                    .findViewById(R.id.nav_stack_toolbar_title);
            _toolbarButtons = _rootView
                    .findViewById(R.id.nav_stack_toolbar_buttons);
            _viewContainer = _rootView.findViewById(R.id.nav_stack_container);
            _closeButton = _rootView.findViewById(R.id.nav_stack_close);
            _closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!_viewStack.isEmpty()) {
                        NavigationStackView view = _viewStack.get(0);
                        if (!view.onCloseButton())
                            closeNavigationStack();
                    }
                }
            });
        }
    }

    private void showView(NavigationStackView view) {
        _toolbarTitle.setText(view.getTitle());
        setButtons(view.getButtons());
        _viewContainer.removeAllViews();
        _viewContainer.addView(view.getView());
    }

    public void updateButtons() {
        if (_viewStack.size() > 0) {
            final NavigationStackView view = _viewStack.get(0);
            setButtons(view.getButtons());
        }
    }

    private void setButtons(List<ImageButton> buttons) {
        if (_toolbarButtons != null) {
            _toolbarButtons.removeAllViews();
            if (buttons != null) {
                int padding = (int) _resources
                        .getDimension(R.dimen.auto_space_big);
                int size = (int) _resources.getDimension(
                        R.dimen.list_item_action_icon_size);
                for (ImageButton button : buttons) {
                    if (button.getParent() != null) {
                        ViewParent p = button.getParent();
                        if (p instanceof ViewGroup)
                            ((ViewGroup) p).removeView(button);
                    }
                    button.setColorFilter(
                            _resources.getColor(R.color.pale_silver));
                    button.setBackgroundColor(Color.TRANSPARENT);
                    button.setPadding(padding, padding, padding, padding);
                    button.setLayoutParams(new LinearLayout.LayoutParams(
                            size, size));
                    _toolbarButtons.addView(button);
                }
            }
            _toolbarButtons.addView(_closeButton);
        }
    }

    public void closeNavigationStack() {
        while (_viewStack.size() > 0) {
            popView(false);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

    }
}
