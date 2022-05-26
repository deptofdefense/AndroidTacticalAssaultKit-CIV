
package com.atakmap.spatial.file.export;

import android.content.Context;

import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.Serializer;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Marshals <code>Export</code> instances to a KML file
 * 
 * 
 */
public class KMLExportMarshal extends ExportFileMarshal {

    private static final String TAG = "KMLExportMarshal";

    //features organized by parent folder, and unique styles
    protected final Map<String, List<Feature>> features;
    protected final Map<String, Style> styles;

    public KMLExportMarshal(Context context) {
        this(context, KmlFileSpatialDb.KML_TYPE);
    }

    public KMLExportMarshal(Context context, String type) {
        super(context, type.toUpperCase(LocaleUtil.getCurrent()),
                KmlFileSpatialDb.KML_FILE_MIME_TYPE,
                KmlFileSpatialDb.KML_FILE_ICON_ID);
        features = new HashMap<>();
        styles = new HashMap<>();
    }

    @Override
    public Class<?> getTargetClass() {
        return Folder.class;
    }

    @Override
    public File getFile() {
        return new File(
                FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY),
                FileSystemUtils.sanitizeWithSpacesAndSlashes(filename));
    }

    @Override
    protected boolean marshal(final Exportable export)
            throws FormatNotSupportedException {
        if (export == null || !export.isSupported(Folder.class)) {
            Log.d(TAG, "Skipping unsupported export "
                    + (export == null ? "" : export.getClass().getName()));
            return false;
        }

        Folder folder = (Folder) export.toObjectOf(Folder.class, getFilters());
        if (folder == null || folder.getFeatureList() == null
                || folder.getFeatureList().size() < 1) {
            Log.d(TAG, "Skipping empty folder");
            return false;
        }
        Log.d(TAG, "Adding folder name: " + folder.getName());

        //gather all unique styles which we haven't encountered before
        //Note, assumes the UID is unique, i.e. a hash of the style
        List<Style> curStyles = new ArrayList<>();
        KMLUtil.getStyles(folder, curStyles, Style.class);
        for (Style style : curStyles) {
            //store the style, only once
            if (!styles.containsKey(style.getId())) {
                styles.put(style.getId(), style);
            }
        } //end style loop

        //gather all Placemarks
        final List<Feature> fList = getOrCreateFeatureList(folder.getName());
        KMLUtil.deepFeatures(folder, new FeatureHandler<Placemark>() {
            @Override
            public boolean process(Placemark feature) {
                fList.add(feature);
                //Log.d(TAG, features.size() + ": " + feature.getName());
                return false;
            }
        }, Placemark.class);

        if (fList.size() > 0) {
            //TODO technically if .marshall were called multiple times, then fList
            //may have already had features in it, so checking size may not truly
            //imply something was exported
            //TODO could also have subfolders with additional placemarks
            Log.d(TAG, "Added " + folder.getName() + ", feature count: "
                    + folder.getFeatureList().size());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get feature list based on folder name
     * This stores features from the specified folder in class member
     * folders to prep for export
     * 
     * @param folderName the folder name to use when creating a feaure list
     * @return the feature list
     */
    protected List<Feature> getOrCreateFeatureList(String folderName) {
        List<Feature> f = features.get(folderName);
        if (f == null) {
            f = new ArrayList<>();
            features.put(folderName, f);
            Log.d(TAG, "Adding member list: " + folderName);
        }

        return f;
    }

    protected String getKml() throws IOException {
        if (features == null || features.size() < 1)
            throw new IOException("No features");

        List<Feature> documentFeatures = new ArrayList<>();
        for (Entry<String, List<Feature>> es : features.entrySet()) {
            if (es.getValue() == null || es.getValue().size() < 1) {
                Log.d(TAG, "Skipping empty export folder: " + es.getKey());
                continue;
            }

            Log.d(TAG, "Exporting features for: " + es.getKey());

            //see if this set of features should be placed in a KML subfolder
            Folder folder;
            List<Feature> fList;
            if (FileSystemUtils.isEmpty(es.getKey())) {
                //no folder name, just put in root folder
                fList = documentFeatures;
                folder = null;
            } else {
                //put in subfolder
                folder = new Folder();
                folder.setName(es.getKey());
                fList = new ArrayList<>();
                folder.setFeatureList(fList);
            }

            //now add the features, only once
            for (Feature f : es.getValue()) {
                if (!FileSystemUtils.isEmpty(f.getId())
                        && exportedUIDs.contains(f.getId())) {
                    Log.d(TAG, "Skipping duplicate UID: " + f.getId());
                    continue;
                }

                exportedUIDs.add(f.getId());
                fList.add(f);
            } //end feature loop

            //add folder if any features were added to sub folder (not document root)
            if (folder != null && fList.size() > 0) {
                documentFeatures.add(folder);
            }
        } //end folder loop

        //Simple KML wants them in a StyleSelector list
        List<StyleSelector> styleList = new ArrayList<>(styles.values());

        Log.d(TAG, "Exporting top level features: " + documentFeatures.size());
        Log.d(TAG, "Exporting styles: " + styleList.size());

        Kml kml = new Kml();
        Document document = new Document();
        kml.setFeature(document);
        document.setName(filename);
        document.setDescription(filename
                + " generated by "
                + ATAKConstants.getVersionName()
                +
                " on: "
                + KMLUtil.KMLDateTimeFormatter.get().format(
                        CoordinatedTime.currentDate()));
        document.setOpen(1);
        Collections.sort(documentFeatures, featureComparator);
        document.setFeatureList(documentFeatures);
        document.setStyleSelector(styleList);

        Serializer serializer = new Serializer();
        StringWriter sw = new StringWriter();
        try {
            serializer.write(kml, sw);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return sw.toString();
    }

    @Override
    public void finalizeMarshal() throws IOException {
        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }

        String kml = getKml();
        if (FileSystemUtils.isEmpty(kml)) {
            throw new IOException("Failed to serialize KML");
        }

        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }
        if (hasProgress()) {
            this.progress.publish(94);
        }

        // delete existing file, and then serialize KML out to file
        File file = getFile();
        if (IOProviderFactory.exists(file)) {
            FileSystemUtils.deleteFile(file);
        }

        //TODO optimize? use XmlPullParser
        KMLUtil.write(kml, file);
        Log.d(TAG, "Exported: " + file.getAbsolutePath());
    }

    /**
     * Sort folders alphabetically
     */
    private final Comparator<Feature> featureComparator = new Comparator<Feature>() {

        @Override
        public int compare(Feature lhs, Feature rhs) {
            if (lhs == null || rhs == null)
                return 0;

            //folders first
            if ((lhs instanceof Folder) && !(rhs instanceof Folder)) {
                return 1;
            }
            if (!(lhs instanceof Folder) && (rhs instanceof Folder)) {
                return -1;
            }

            //sort by name 
            if (lhs.getName() == null && rhs.getName() == null)
                return 0;

            if (lhs.getName() == null || rhs.getName() == null)
                return (lhs.getName() == null ? -1 : 1);

            return lhs.getName().compareTo(rhs.getName());
        }
    };
}
