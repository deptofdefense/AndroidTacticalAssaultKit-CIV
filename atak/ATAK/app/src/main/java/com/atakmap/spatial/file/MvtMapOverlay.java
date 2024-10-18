
package com.atakmap.spatial.file;

import android.content.Context;
import android.widget.BaseAdapter;

import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.features.FeatureDataStoreMapOverlay;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MvtMapOverlay extends FeatureDataStoreMapOverlay {
    private final static String TAG = "MvtMapOverlay";

    public MvtMapOverlay(Context context, FeatureDataStore spatialDb,
            FeatureDataStoreDeepMapItemQuery query) {
        super(context,
                spatialDb,
                MvtSpatialDb.MVT_TYPE,
                MvtSpatialDb.GROUP_NAME,
                MvtSpatialDb.ICON_PATH,
                query,
                MvtSpatialDb.MVT_CONTENT_TYPE,
                MvtSpatialDb.MVT_FILE_MIME_TYPE);
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter callback,
            long capabilities, HierarchyListFilter filter) {
        if ((capabilities & (Actions.ACTION_GOTO | Actions.ACTION_VISIBILITY
                | Actions.ACTION_DELETE | Actions.ACTION_EXPORT)) == 0) {
            return null;
        }

        return new ListItem(callback, filter);
    }

    final class ListItem extends FeatureDataStoreMapOverlay.ListItem {
        public ListItem(BaseAdapter listener, HierarchyListFilter filter) {
            super(listener, filter);
        }

        @Override
        protected void updateChildren(List<HierarchyListItem> items) {
            Map<File, List<HierarchyListItem>> m = new HashMap<>();
            List<HierarchyListItem> unmapped = new ArrayList<>(items.size());
            for (HierarchyListItem i : items) {
                try {
                    final FeatureSet fs = Utils.getFeatureSet(spatialDb,
                            (Long) i.getUserObject());
                    if (fs != null) {
                        final File f = Utils.getSourceFile(spatialDb, fs);
                        List<HierarchyListItem> mapped = m.get(f);
                        if (mapped == null)
                            m.put(f, mapped = new ArrayList<>());
                        mapped.add(i);
                        continue;
                    }
                } catch (DataStoreException ignored) {
                }

                // not able to map to a file, add as top-level item
                unmapped.add(i);
            }

            ArrayList<HierarchyListItem> items2 = new ArrayList<>(
                    m.size() + unmapped.size());
            for (Map.Entry<File, List<HierarchyListItem>> e : m.entrySet())
                items2.add(
                        new MvtFileListItem(context, e.getKey(), e.getValue()));
            items2.addAll(unmapped);

            super.updateChildren(items2);
        }
    }
}
