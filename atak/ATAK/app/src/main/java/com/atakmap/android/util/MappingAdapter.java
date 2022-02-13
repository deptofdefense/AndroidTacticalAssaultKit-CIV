
package com.atakmap.android.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atakmap.coremap.log.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class to allow ListView View Models to be easily passed to an adapter.
 * This class automatically handles comparing VMs as well as view updates
 *
 * Usage: Subclass MappingAdapter for your ListViews adapter. In init add your mappings ex:
 *  addMapping(ListItemActionsVM.class, ListItemActionsVH.class);
 *
 *  Pass an array of mapped VMs to `replaceItems` and data will passed to bound VMs `bind` method
 *  and views will be handled automatically by the adapter
 **/
public class MappingAdapter extends ArrayAdapter<MappingVM> {

    private static final String TAG = "MappingAdapter";

    private final HashMap<Class<?>, Class<?>> mappings = new HashMap<>();
    private MappingAdapterEventReceiver<MappingVM> eventReceiver;

    public MappingAdapter(@NonNull Context context) {
        super(context, -1);
    }

    /*
    Returns a populated View for an index. based on current list of VMs
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView,
            @NonNull ViewGroup parent) {
        MappingVH<MappingVM> vh = (MappingVH<MappingVM>) convertView;

        MappingVM item = getItem(position);
        final Class<?> clazz = mappings.get(item.getClass());

        if (vh == null || (clazz != null && !clazz.isInstance(vh))) {
            try {
                vh = ((MappingVH<MappingVM>) clazz
                        .getConstructor(ViewGroup.class)
                        .newInstance(parent));
            } catch (Exception e) {
                Log.e(TAG, "error", e);
            }
        }
        if (vh == null)
            throw new IllegalStateException();

        vh.onBind(item, eventReceiver);
        return vh;
    }

    /*
    Returns a view type for an index. based on current list of VMs
     */
    @Override
    public int getItemViewType(int position) {
        List<Class<?>> indexes = (List<Class<?>>) (Object) Arrays
                .asList(mappings.keySet().toArray());
        Collections.sort(indexes, new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> item1, Class<?> item2) {
                return item1.getName().compareTo(item2.getName());
            }
        });
        return indexes.indexOf(getItem(position).getClass());
    }

    /*
    adds a mapping between a VM type and a VH type. Used to create views from a list of given VMs
     */
    public void addMapping(Class<?> vmType, Class<?> vhType) {
        mappings.put(vmType, vhType);
    }

    public void replaceItems(List<MappingVM> items) {
        super.clear();
        super.addAll(items);
        notifyDataSetChanged();
    }

    /*
    Pass in an event receiver to receive click events on generated views
     */
    public void setEventReceiver(
            MappingAdapterEventReceiver<MappingVM> receiver) {
        this.eventReceiver = receiver;
    }
}
