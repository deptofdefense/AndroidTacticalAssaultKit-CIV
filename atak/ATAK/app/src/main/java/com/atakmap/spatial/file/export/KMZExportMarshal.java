
package com.atakmap.spatial.file.export;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Style;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Marshals <code>Export</code> instances to KMZ
 * 
 * 
 */
public class KMZExportMarshal extends KMLExportMarshal {

    private static final String TAG = "KMZExportMarshal";

    /**
     * Files to include in KMZ
     * Local path paired with path in KMZ
     */
    private final List<Pair<String, String>> _files;

    /**
     * Reuse IO buffer for efficiency
     */
    private final byte[] _buffer;

    public KMZExportMarshal(Context context) {
        super(context, KmlFileSpatialDb.KMZ_TYPE);
        _buffer = new byte[FileSystemUtils.BUF_SIZE];
        _files = new ArrayList<>();
    }

    @Override
    public Class<?> getTargetClass() {
        return KMZFolder.class;
    }

    @Override
    protected boolean marshal(Exportable export)
            throws FormatNotSupportedException {
        if (export == null) {
            Log.w(TAG, "Skipping null export");
            return false;
        }

        //use KMZ if possible, fallback on KML
        Log.d(TAG, "Exporting: " + export.getClass().getName());
        if (export.isSupported(KMZFolder.class)) {
            KMZFolder folder = (KMZFolder) export.toObjectOf(KMZFolder.class,
                    getFilters());
            if (folder == null || folder.isEmpty()) {
                Log.d(TAG, "Skipping empty folder");
                return false;
            }
            Log.d(TAG, "Adding KMZ folder name: " + folder.getName());

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
            final List<Feature> fList = getOrCreateFeatureList(
                    folder.getName());
            KMLUtil.deepFeatures(folder, new FeatureHandler<Placemark>() {
                @Override
                public boolean process(Placemark feature) {
                    fList.add(feature);
                    //Log.d(TAG, features.size() + ": " + feature.getName());
                    return false;
                }
            }, Placemark.class);

            //now process files
            for (Pair<String, String> file : folder.getFiles()) {
                //store the file, only once
                if (!_files.contains(file)) {
                    _files.add(file);
                }
            } //end file loop

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
        } else if (export.isSupported(Folder.class)) {
            return super.marshal(export);
        } else {
            Log.d(TAG, "Skipping unsupported export "
                    + export.getClass().getName());
            return false;
        }
    }

    @Override
    public void finalizeMarshal() throws IOException {
        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }

        //generate KML, zip it, include icons, attachments, etc
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

        //TODO sits at 94% during serialization to KMZ/zip. Could serialize during marshall above
        File kmz = getFile();
        try (ZipOutputStream zos = FileSystemUtils.getZipOutputStream(kmz)) {

            //and doc.kml
            addFile(zos, "doc.kml", kml);

            //loop and add all files
            for (Pair<String, String> file : _files) {
                addFile(zos, file);

                synchronized (this) {
                    if (this.isCancelled) {
                        Log.d(TAG, "Cancelled, in finalizeMarshal");
                        return;
                    }
                }
            }

            Log.d(TAG, "Exported: " + kmz.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to create KMZ file", e);
            throw new IOException(e);
        }
    }

    public static void addFile(ZipOutputStream zos, String zipEntry,
            String content)
            throws IOException {
        if (FileSystemUtils.isEmpty(content)) {
            throw new IOException("Failed to serialize content");
        }

        byte[] contentData = content
                .getBytes(FileSystemUtils.UTF8_CHARSET);
        if (FileSystemUtils.isEmpty(contentData)) {
            throw new IOException("Failed to serialize content Data");
        }

        // create new zip entry
        ZipEntry entry = new ZipEntry(zipEntry);
        zos.putNextEntry(entry);
        Log.d(TAG, "Adding content: " + entry.getName() + " with size: "
                + contentData.length);

        // stream data into zipstream
        // Note, here we dont use write buffering as we've already got the whole
        // event in RAM...
        zos.write(contentData, 0, contentData.length);

        // close zip entry
        zos.closeEntry();
    }

    private void addFile(ZipOutputStream zos, Pair<String, String> file) {
        try (InputStream in = getInputStream(context, file.first)) {
            // create new zip entry
            ZipEntry entry = new ZipEntry(file.second);
            zos.putNextEntry(entry);

            // stream file into zipstream
            FileSystemUtils.copyStream(in, true, zos, false, _buffer);

            // close current file & corresponding zip entry

            Log.d(TAG, "Compressing file " + file.first);
        } catch (IOException e) {
            Log.e(TAG, "Failed to add File: " + file.second, e);
        } finally {
            if (zos != null) {
                try {
                    zos.closeEntry();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static InputStream getInputStream(Context context, String file)
            throws IOException {

        InputStream in;
        if (file.startsWith("asset:")) {
            Uri uri = Uri.parse(file);
            String assetPath = uri.toString().substring(8);
            if (assetPath.startsWith("/")) {
                assetPath = assetPath.substring(1);
            }
            in = context.getAssets().open(assetPath);
            Log.d(TAG, "Asset input stream: " + file);
        } else if (file.startsWith("sqlite:")) {
            //assume icon stored in UserIconDatabase
            byte[] bitmap = UserIcon.GetIconBytes(file, context);
            if (FileSystemUtils.isEmpty(bitmap))
                throw new IOException("Unable to stream icon: " + file);

            return new ByteArrayInputStream(bitmap);
        } else {
            FileInputStream fi = IOProviderFactory
                    .getInputStream(new File(file));
            in = new BufferedInputStream(fi, FileSystemUtils.BUF_SIZE);
            Log.d(TAG, "File input stream: " + file);
        }

        return in;
    }
}
