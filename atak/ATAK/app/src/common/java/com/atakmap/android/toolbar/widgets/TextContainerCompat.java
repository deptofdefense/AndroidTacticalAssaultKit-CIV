
package com.atakmap.android.toolbar.widgets;

import android.content.Context;

import com.atakmap.android.maps.MapView;

public class TextContainerCompat {
    public static TextContainer createInstance() {
        return new TextContainer();
    }

    public static void displayPrompt(String title, String text) {
        TextContainer.getInstance().displayPrompt(title + " " + text);
    }

    public static void displayPrompt(int titleId, int textId) {
        final Context context = MapView.getMapView().getContext();
        TextContainer.getInstance()
                .displayPrompt(context.getString(titleId) + " "
                        + context.getString(textId));
    }
}
