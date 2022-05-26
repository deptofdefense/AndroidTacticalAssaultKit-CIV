
package com.atakmap.android.helloworld;

import com.atakmap.android.helloworld.plugin.R;
import androidx.fragment.app.Fragment;
import com.atakmap.android.user.icon.*;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MapItem;

import android.view.View;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.content.Context;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class PluginIconPallet implements IconPallet {

    private final Fragment fragment;
    static Context pContext = null;

    public PluginIconPallet(Context pContext) {
        PluginIconPallet.pContext = pContext;
        this.fragment = new FooFragment();
    }

    @Override
    public String getTitle() {
        return "HelloPallet";
    }

    @Override
    public String getUid() {
        return "HELLO_PLUGIN-55AE-33GF-3333-21112";
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return "HelloPallet";
    }

    /**
     * Actual function to place the point.     Does not need to be implemented
     * if the fragment handles the user interactions.
     */
    @Override
    public MapItem getPointPlacedIntent(GeoPointMetaData point, String uid) {
        // logic that creates the point //
        return new Marker(point, uid);
    }

    @Override
    public void select(int resId) {
    }

    @Override
    public void clearSelection(boolean bPauseListener) {
        // needs to interact with the custom fragment to do that
        // fooFragment should impl clearSelection;
    }

    @Override
    public void refresh() {
        // needs to interact with the custom fragment to do that 
        // fooFragment should impl refresh;
    }

    static public class FooFragment extends Fragment {
        // The onCreateView method is called when Fragment should create its View object hierarchy,
        // either dynamically or via XML layout inflation. 
        @Override
        public View onCreateView(LayoutInflater donotuse, ViewGroup parent,
                Bundle savedInstanceState) {
            // Defines the xml file for the fragment
            // Note the inflater is using the plugin context.
            LayoutInflater inflater = LayoutInflater.from(pContext);

            return inflater.inflate(R.layout.fragment_foo, null);
        }

        // This event is triggered soon after onCreateView().
        // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            // Setup any handles to view objects here
            // EditText etFoo = (EditText) view.findViewById(R.id.etFoo);
        }
    }
}
