
package com.atakmap.android.toolbar.menu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.widget.PopupWindow;

import com.atakmap.android.maps.MapView;
import android.util.Pair;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolbarMenuManager extends BroadcastReceiver {

    public static final String TAG = "ToolbarMenuManager";

    private static ToolbarMenuManager instance;
    private ToolbarButton activeButton;
    private PopupWindow activePopUpWindow;
    private MapView mapView;
    @SuppressWarnings("unused")
    private final Map<String, Pair<PopupWindow, IMenu>> registeredMenus = new HashMap<>();
    private final Map<IMenu, List<MenuButton>> registeredMenuButtons = new HashMap<>();

    synchronized public static ToolbarMenuManager getInstance() {
        if (instance == null) {
            instance = new ToolbarMenuManager();
        }
        return instance;
    }

    public void initialize(Context c, MapView m) {
        this.mapView = m;
    }

    public void dispose() {
        registeredMenus.clear();
        registeredMenuButtons.clear();
        instance = null;
        activePopUpWindow = null;
        activeButton = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

    }

    public void register(IMenu menu) {
        registeredMenus
                .put(menu.getIdentifier(),
                        new Pair<>(this
                                .createPopupWindow(menu), menu));
    }

    public void register(IMenu parent, MenuButton menu) {
        if (registeredMenuButtons.get(parent) == null) {
            registeredMenuButtons.put(parent, new ArrayList<MenuButton>());
        }
        registeredMenuButtons.get(parent).add(menu);
    }

    public void showMenu(String identifier, int x, int y) {
        PopupWindow newActivePopUpWindow = registeredMenus
                .get(identifier).first;

        if (newActivePopUpWindow != this.activePopUpWindow)
            this.closeMenu();

        this.activePopUpWindow = newActivePopUpWindow;

        if (registeredMenus.get(identifier).second.getPreferredWidth() == -1)
            this.activePopUpWindow
                    .setWidth(registeredMenus.get(identifier).second.getView()
                            .getLayoutParams().width);
        else
            this.activePopUpWindow
                    .setWidth(registeredMenus.get(identifier).second
                            .getPreferredWidth());

        if (registeredMenus.get(identifier).second.getPreferredHeight() == -1)
            this.activePopUpWindow
                    .setHeight(registeredMenus.get(identifier).second.getView()
                            .getLayoutParams().height);
        else
            this.activePopUpWindow
                    .setHeight(registeredMenus.get(identifier).second
                            .getPreferredHeight());

        if (!this.activePopUpWindow.isShowing()) {
            this.activePopUpWindow.showAsDropDown(activeButton.getButton(),
                    0, 0, Gravity.TOP | Gravity.RIGHT);
        } else {
            Log.e(TAG, "tried to show menu that wasn't registered: "
                    + identifier);
        }
    }

    public void closeMenu() {
        if (activePopUpWindow != null && activePopUpWindow.isShowing()) {
            activePopUpWindow.dismiss();
        }
    }

    private PopupWindow createPopupWindow(IMenu menu) {
        PopupWindow popUpWindow = new PopupWindow(this.mapView.getContext());
        popUpWindow.setContentView(menu.getView());
        popUpWindow.setClippingEnabled(false);

        // popUpWindow.setAnimationStyle(R.style.Animation_ButtonMenu);

        if (menu.getPreferredWidth() == -1)
            try {
                popUpWindow.setWidth(menu.getView().getLayoutParams().width);
            } catch (NullPointerException npe) {
                popUpWindow.setWidth(menu.getPreferredWidth());
            }
        else
            popUpWindow.setWidth(menu.getPreferredWidth());

        if (menu.getPreferredHeight() == -1)
            try {
                popUpWindow.setHeight(menu.getView().getLayoutParams().height);
            } catch (NullPointerException npe) {
                popUpWindow.setHeight(menu.getPreferredHeight());
            }
        else
            popUpWindow.setHeight(menu.getPreferredHeight());

        popUpWindow.setBackgroundDrawable(new BitmapDrawable());

        popUpWindow.setFocusable(true);
        popUpWindow.setOutsideTouchable(false);

        return popUpWindow;
    }

    // Update point tool button, change the label and icon
    public void updateActiveToolbarButton(Drawable[] icons, String label) {
        this.activeButton.updateButton(icons, label);
    }

    public boolean hasMenu(String identifier) {
        return registeredMenus.get(identifier) != null;
    }

    public void setActiveToolbarButton(ToolbarButton button) {
        this.activeButton = button;
    }

    public void stealOnClickListeners(List<OnClickListener> listeners) {
        this.activeButton.resetOnClickListeners();
        this.activeButton.addOnClickListeners(listeners);
    }

    public void enableAssociatedMenuButton(IMenu parent) {
        List<MenuButton> buttons = registeredMenuButtons.get(parent);
        if (buttons != null) {
            for (MenuButton b : buttons) {
                b.getButton().setEnabled(true);
            }
        }
    }
}
