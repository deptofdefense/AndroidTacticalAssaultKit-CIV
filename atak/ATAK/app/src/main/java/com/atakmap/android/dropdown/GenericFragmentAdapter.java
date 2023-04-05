
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
            Log.d(TAG, "onCreate: new fragment wrapper: " + uid);
        } else {
            Log.d(TAG, "onCreate: fragment restored: " + uid);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG,
                "onSaveInstanceState: saving the uid in case the fragment has not been destroyed: "
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

        Log.d(TAG, "setView: setting a view for: " + uid);
        mapping.put(uid, v);

    }

    /**
     * Remove the view from the mapping so it can be freed from memory
     */
    public void removeView() {
        if (mapping.remove(uid) != null) {
            Log.d(TAG, "Removing view: " + uid + " retained size: "
                    + mapping.size());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: getting a fragment view for: " + uid);
        return mapping.get(uid);
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView: destroying the fragment view for: " + uid);
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
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach: call to detach view: " + uid);
    }

    @Override
    public void onDestroy() {
        onDestroyView();
        removeView();
        super.onDestroy();
    }
}
