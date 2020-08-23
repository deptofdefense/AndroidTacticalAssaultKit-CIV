
package com.atakmap.android.dropdown;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A generic implementation of a FragmentAdapter for use with Legacy Drop Downs within the system.
 */

public class GenericFragmentAdapter extends Fragment {

    static final Map<String, View> mapping = new HashMap<>();

    private String uid;

    public static final String TAG = "GenericFragmentAdapter";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            uid = savedInstanceState.getString("viewuid");
        }
        if (uid == null) {
            uid = UUID.randomUUID().toString();
            Log.d(TAG, "new fragment wrapper: " + uid);
        } else {
            Log.d(TAG, "fragment restored: " + uid);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG,
                "saving the uid in case the fragment has not been destroyed: "
                        + uid);
        outState.putString("viewuid", uid);
    }

    /**
     * Sets the view that onCreateView will return upon request. 
     */
    public void setView(final View v) {
        if (uid == null) {
            // in the case the Fragment has not been created
            uid = UUID.randomUUID().toString();
        }

        Log.d(TAG, "setting a view for: " + uid);
        mapping.put(uid, v);

    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState) {
        Log.d(TAG, "getting a fragment view for: " + uid);
        return mapping.get(uid);
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "destroying the fragment view for: " + uid);
        View view = mapping.get(uid);
        //Remove the view from the parent group prior to destroying the fragment.
        if (view != null) {
            ViewGroup parentViewGroup = (ViewGroup) view.getParent();
            if (parentViewGroup != null) {
                parentViewGroup.removeView(view);
            }
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapping.remove(uid);
        // XXX: it might be better to place this in onDetach vs onDestroy 
        Log.d(TAG,
                "removing view: " + uid + " retained size: " + mapping.size());
    }

}
