
package com.atakmap.android.features;

import android.content.Context;
import android.content.Intent;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FeatureDataStoreMapOverlay extends AbstractMapOverlay2 {

    private static final String TAG = "FeatureDataStoreMapOverlay";

    protected final Context context;
    protected final String contentSource;
    protected final String name;
    protected final String iconUri;
    protected final FeatureDataStore2 spatialDb;
    protected final FeatureDataStoreDeepMapItemQuery query;
    protected final String contentType;
    protected final String mimeType;

    private final MapGroup mapGroup;

    public FeatureDataStoreMapOverlay(Context context,
            FeatureLayer layer,
            String iconUri) {

        this(context,
                layer.getDataStore(),
                null,
                layer.getName(),
                iconUri,
                new FeatureDataStoreDeepMapItemQuery(layer),
                null,
                null);
    }

    public FeatureDataStoreMapOverlay(Context context,
            FeatureDataStore spatialDb, String contentSource,
            String name, String iconUri,
            FeatureDataStoreDeepMapItemQuery query,
            String contentType, String mimeType) {
        this(context,
                Adapters.adapt(spatialDb),
                contentSource,
                name,
                iconUri,
                query,
                contentType,
                mimeType);
    }

    public FeatureDataStoreMapOverlay(Context context,
            FeatureDataStore2 spatialDb, String contentSource,
            String name, String iconUri,
            FeatureDataStoreDeepMapItemQuery query,
            String contentType, String mimeType) {
        this.context = context;
        this.spatialDb = spatialDb;
        this.contentSource = contentSource;
        this.name = name;
        this.iconUri = iconUri;
        this.query = query;
        this.contentType = contentType;
        this.mimeType = mimeType;

        this.mapGroup = new DefaultMapGroup(this.name);
        this.mapGroup.setMetaBoolean("customRenderer", true);
    }

    /**************************************************************************/

    @Override
    public String getIdentifier() {
        return this.name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public MapGroup getRootGroup() {
        return this.mapGroup;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return this.query;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter filter) {
        if ((capabilities & (Actions.ACTION_GOTO | Actions.ACTION_VISIBILITY
                | Actions.ACTION_DELETE | Actions.ACTION_EXPORT)) == 0) {
            return null;
        }

        return new ListItem(adapter, filter);
    }

    protected Set<String> getChildFiles() {
        FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
        if (this.contentSource != null)
            params.types = Collections.singleton(this.contentSource);

        Set<String> files = new HashSet<>();
        FeatureSetCursor result = null;
        try {
            result = this.spatialDb
                    .queryFeatureSets(Adapters.adapt(params, null));
            FeatureSet featureSet;
            File featureFile;
            while (result.moveToNext()) {
                featureSet = result.get();
                featureFile = Utils.getSourceFile(this.spatialDb, featureSet);
                if (featureFile != null)
                    files.add(featureFile.getAbsolutePath());
            }

        } catch (DataStoreException dse) {
            Log.e(TAG, "datastore exception occurred", dse);
        } finally {
            if (result != null)
                result.close();
        }
        return files;
    }

    /**************************************************************************/

    public class ListItem extends FeatureDataStoreHierarchyListItem implements
            Delete, Export {

        public ListItem(BaseAdapter listener, HierarchyListFilter filter) {
            super(FeatureDataStoreMapOverlay.this.spatialDb,
                    FeatureDataStoreMapOverlay.this.getName(),
                    FeatureDataStoreMapOverlay.this.iconUri,
                    FeatureDataStoreMapOverlay.this.contentSource,
                    FeatureDataStoreMapOverlay.this.mimeType,
                    FeatureDataStoreMapOverlay.this.context,
                    listener, filter);

            this.supportedActions.add(Delete.class);
            this.supportedActions.add(Export.class);
        }

        @Override
        public boolean delete() {
            Set<String> files = getChildFiles();
            if (files == null || files.size() < 1) {
                Log.d(TAG, "No " + FeatureDataStoreMapOverlay.this.contentType
                        + " children to delete");
                return true;
            }

            Log.d(TAG, "Deleting "
                    + FeatureDataStoreMapOverlay.this.contentType
                    + " children count: " + files.size());
            Intent deleteIntent = new Intent();
            deleteIntent.setAction(ImportExportMapComponent.ACTION_DELETE_DATA);
            deleteIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    FeatureDataStoreMapOverlay.this.contentType);
            deleteIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    FeatureDataStoreMapOverlay.this.mimeType);
            deleteIntent.putExtra(ImportReceiver.EXTRA_URI_LIST,
                    new ArrayList<>(files));
            AtakBroadcast.getInstance().sendBroadcast(deleteIntent);
            return true;
        }

        @Override
        public boolean isSupported(Class<?> target) {
            return MissionPackageExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters)
                throws FormatNotSupportedException {

            if (MissionPackageExportWrapper.class.equals(target)) {
                return toMissionPackage();
            }

            return null;
        }

        private MissionPackageExportWrapper toMissionPackage() {
            Set<String> children = getChildFiles();

            if (children == null || children.size() < 1) {
                Log.d(TAG, "No " + FeatureDataStoreMapOverlay.this.contentType
                        + " children to export");
                return null;
            }

            Log.d(TAG, "Exporting "
                    + FeatureDataStoreMapOverlay.this.contentType
                    + " children count: " + children.size());
            MissionPackageExportWrapper f = new MissionPackageExportWrapper();
            for (String path : children) {
                if (FileSystemUtils.isEmpty(path))
                    continue;
                if (path.startsWith("file://"))
                    path = path.substring("file://".length());
                f.getFilepaths().add(path);
            }
            return f;
        }
    }
}
