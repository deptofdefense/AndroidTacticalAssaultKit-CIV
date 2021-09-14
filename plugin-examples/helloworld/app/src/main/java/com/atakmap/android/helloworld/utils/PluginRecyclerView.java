
package com.atakmap.android.helloworld.utils;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.R;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;

/**
 * Overcome an issue with the RecyclerView passing back the wrong 
 * Resouce when used within a plugin.    
 */
public class PluginRecyclerView extends RecyclerView {

    private static Context appContext;

    public PluginRecyclerView(Context context) {
        this(context, null);
    }

    public PluginRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.recyclerViewStyle);
    }

    public PluginRecyclerView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Must be set at the start of the plugin so that the RecyclerView
     * knows about both the plugin context and the application context.
     * This is left in for future possible fixes.
     * @param context the application context.   
     */
    public static void setAppContext(Context context) {
        appContext = context;
    }

    /**
     * Correction to the method getResources so that it returns the 
     * Resources from the application context and not the resources
     * from the plugin context.
     */
    public Resources getResources() {
        return getContext().getResources();
    }
}
