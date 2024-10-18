
package com.atakmap.android.missionpackage.event;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.sort.ImportSHPSort;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.MissionPackageManifestAdapter;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.missionpackage.ui.MissionPackageListFileItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.ShapefileSpatialDb;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Zip all relevant shapefile physical files and add to Mission Package as a zip
 * 
 * 
 */
public class MissionPackageShapefileHandler implements
        IMissionPackageEventHandler {

    private static final String TAG = "MissionPackageShapefileHandler";

    /**
     * Import Manager sort to match and import
     */
    private final ImportSHPSort _sort;
    private final Context _context;

    public MissionPackageShapefileHandler(Context context) {
        _context = context;
        _sort = new ImportSHPSort(context, true, false, false);
    }

    @Override
    public boolean add(MissionPackageListGroup group, File file) {
        if (!_sort.match(file))
            return false;

        // Add main .shp file
        MissionPackageContent content = MissionPackageManifestAdapter
                .FileToContent(file, null);
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Failed to adapt file path to Mission Package Content");
            return false;
        }

        if (group.getManifest().hasFile(content)) {
            Log.i(TAG,
                    group + " already contains filename: "
                            + file.getName());
            Toast.makeText(_context, _context.getString(
                    R.string.mission_package_already_contains_file,
                    _context.getString(R.string.mission_package_name),
                    file.getName()),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // add file to package
        Log.d(TAG, "Adding file: " + file.getAbsolutePath());
        if (!group.addFile(content, file)) {
            Log.w(TAG, "Failed to add file path to Mission Package Content");
            return false;
        }

        // and mark any other relevant files as "ignore" (treat errors as non fatal for these)
        List<File> files = ImportSHPSort.GetShapefileMemberFiles(file);
        if (files == null || files.size() < 1) {
            // just include the main .shp file...
            Log.w(TAG,
                    "Unable to locate any relevant files for file: "
                            + file.getAbsolutePath());
            return true;
        }

        int dataSetCount = 1; // already added main .shp file
        for (File datasetFile : files) {
            MissionPackageContent dataSetContent = MissionPackageManifestAdapter
                    .FileToContent(
                            datasetFile, null);
            if (dataSetContent == null || !dataSetContent.isValid()) {
                Log.w(TAG,
                        "Failed to adapt file path to Mission Package Content");
                continue;
            }

            // set reference to main .shp file
            dataSetContent.setIgnore(true);
            dataSetContent.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_REFERENCECONTENT, content
                            .getManifestUid()));
            dataSetContent.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_NAME,
                    datasetFile.getName()));

            if (group.getManifest().hasFile(dataSetContent)) {
                Log.i(TAG,
                        group + " already contains filename: "
                                + datasetFile.getName());
                continue;
            }

            // add file to package
            if (!group.addFile(dataSetContent, datasetFile)) {
                Log.w(TAG,
                        "Failed to add file path to Mission Package Content");
                continue;
            }

            dataSetCount++;
        }

        Log.d(TAG,
                "Added a total of " + dataSetCount + " files for shapefile: "
                        + file.getAbsolutePath());
        if (dataSetCount > 1) {
            Toast.makeText(_context, _context.getString(
                    R.string.added_shapefile_to_mission_package,
                    file.getName(),
                    _context.getString(R.string.mission_package_name)),
                    Toast.LENGTH_LONG).show();
        }

        return true;
    }

    /**
     * Static version of member method "add" which operates on a manifest
     * rather than a UI/MissionPackageListGroup
     * 
     * @param manifest Data package manifest
     * @param file Shapefile to send (.shp)
     * @param attachedUID Map item UID the file is attached to (null if N/A)
     * @return True if successfully added
     */
    public static boolean add(Context context, MissionPackageManifest manifest,
            File file, String attachedUID) {
        //resolver only used for matching
        ImportSHPSort sort = new ImportSHPSort(context, true, false, false);
        if (!sort.match(file))
            return false;

        // Add main .shp file
        MissionPackageContent content = MissionPackageManifestAdapter
                .FileToContent(file, attachedUID);
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Failed to adapt file path to Mission Package Content");
            return false;
        }

        if (manifest.hasFile(content)) {
            Log.i(TAG, manifest + " already contains filename: "
                    + file.getName());
            return false;
        }

        Log.d(TAG, "Adding file: " + file.getAbsolutePath());
        if (!manifest.addContent(content)) {
            Log.w(TAG, "Failed to add file path to Mission Package Content");
            return false;
        }

        // and mark any other relevant files as "ignore" (treat errors as non fatal for these)
        List<File> files = ImportSHPSort.GetShapefileMemberFiles(file);
        if (files == null || files.size() < 1) {
            // just include the main .shp file...
            Log.w(TAG,
                    "Unable to locate any relevant files for file: "
                            + file.getAbsolutePath());
            return true;
        }

        int dataSetCount = 1; // already added main .shp file
        for (File datasetFile : files) {
            MissionPackageContent dataSetContent = MissionPackageManifestAdapter
                    .FileToContent(
                            datasetFile, attachedUID);
            if (dataSetContent == null || !dataSetContent.isValid()) {
                Log.w(TAG,
                        "Failed to adapt file path to Mission Package Content");
                continue;
            }

            // set reference to main .shp file
            dataSetContent.setIgnore(true);
            dataSetContent.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_REFERENCECONTENT, content
                            .getManifestUid()));
            dataSetContent.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_NAME,
                    datasetFile.getName()));
            dataSetContent.setParameter(
                    MissionPackageContent.PARAMETER_CONTENT_TYPE,
                    ShapefileSpatialDb.SHP_CONTENT_TYPE);

            if (manifest.hasFile(dataSetContent)) {
                Log.i(TAG, manifest + " already contains filename: "
                        + datasetFile.getName());
                continue;
            }

            // add file to package
            if (!manifest.addContent(dataSetContent)) {
                Log.w(TAG,
                        "Failed to add file path to Mission Package Content");
                continue;
            }

            dataSetCount++;
        }

        Log.d(TAG, "Added a total of " + dataSetCount
                + " files for shapefile: "
                + file.getAbsolutePath());
        return true;
    }

    public static boolean add(Context context, MissionPackageManifest manifest,
            File file) {
        return add(context, manifest, file, null);
    }

    @Override
    public boolean remove(MissionPackageListGroup group,
            MissionPackageListFileItem item) {

        if (item.getContent() == null || !item.getContent().isValid()) {
            Log.w(TAG, "Unable to process invalid item");
            return false;
        }

        String zipEntry = item.getContent().getManifestUid()
                .toLowerCase(LocaleUtil.getCurrent());
        if (!zipEntry.endsWith(".shp")) {
            // not a match, defer to MissionPackageEventHandler
            return false;
        }

        // remove .shp
        Log.d(TAG, "Removing item: " + item);
        if (!group.removeFile(item))
            return false;

        // now gather all content that references this content
        List<MissionPackageContent> references = new ArrayList<>();
        for (MissionPackageContent cur : group.getManifest().getContents()
                .getContents()) {
            if (cur != null
                    && !cur.isCoT()
                    && cur.isIgnore()
                    && cur.hasParameter(
                            MissionPackageContent.PARAMETER_REFERENCECONTENT)) {
                String refContent = cur.getParameter(
                        MissionPackageContent.PARAMETER_REFERENCECONTENT)
                        .getValue();
                if (zipEntry.equalsIgnoreCase(refContent)) {
                    references.add(cur);
                }
            }
        }

        // remove them from manifest
        for (MissionPackageContent reference : references) {
            NameValuePair p = reference
                    .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
            if (p == null || !p.isValid()) {
                Log.w(TAG,
                        "Unable to remove referenced content with no local path: "
                                + reference);
                continue;
            }

            Log.d(TAG, "Removing referenced content: " + reference);
            group.removeFile(MissionPackageManifestAdapter.FileContentToUI(
                    reference,
                    new File(p.getValue())));
        }

        return true;
    }

    @Override
    public boolean extract(MissionPackageManifest manifest,
            MissionPackageContent content, ZipFile zipFile,
            File atakDataDir, byte[] buffer, List<ImportResolver> sorters)
            throws IOException {

        if (content == null || !content.isValid()) {
            Log.w(TAG, "Unable to process invalid item");
            return false;
        }

        String zipEntry = content.getManifestUid().toLowerCase(
                LocaleUtil.getCurrent());
        if (!zipEntry.endsWith(".shp")) {
            // not a match, defer to MissionPackageEventHandler
            return false;
        }

        ZipEntry entry = zipFile.getEntry(content.getManifestUid());
        if (entry == null) {
            throw new IOException("Package does not contain manifest content: "
                    + content.getManifestUid());
        }

        // extract .shp file
        Log.d(TAG, "Exracting file: " + content);
        String parent = MissionPackageFileIO
                .getMissionPackageFilesPath(atakDataDir
                        .getAbsolutePath())
                + File.separatorChar
                + manifest.getUID();
        File shpFile = new File(parent, content.getManifestUid());
        MissionPackageExtractor.UnzipFile(zipFile.getInputStream(entry),
                shpFile, false, buffer);
        File shpDir = shpFile.getParentFile();

        // build out "local" manifest using localpath
        content.setParameter(new NameValuePair(
                MissionPackageContent.PARAMETER_LOCALPATH, shpFile
                        .getAbsolutePath()));

        // now extract all referenced content to same directory as .shp
        parent = shpDir.getAbsolutePath();
        List<MissionPackageContent> unzippedReferences = new ArrayList<>();
        for (MissionPackageContent curContent : manifest.getContents()
                .getContents()) {
            if (curContent != null
                    && !curContent.isCoT()
                    && curContent.isIgnore()
                    && curContent
                            .hasParameter(
                                    MissionPackageContent.PARAMETER_REFERENCECONTENT)
                    && curContent
                            .hasParameter(
                                    MissionPackageContent.PARAMETER_NAME)) {

                NameValuePair nvp = curContent.getParameter(
                        MissionPackageContent.PARAMETER_REFERENCECONTENT);
                String refContent = null;
                if (nvp != null)
                    refContent = nvp.getValue();

                if (zipEntry.equalsIgnoreCase(refContent)) {
                    ZipEntry curEntry = zipFile.getEntry(curContent
                            .getManifestUid());
                    if (curEntry == null) {
                        Log.e(TAG,
                                "Package does not contain referenced manifest content: "
                                        + content.getManifestUid());
                        continue;
                    }

                    final NameValuePair pName = curContent
                            .getParameter(MissionPackageContent.PARAMETER_NAME);
                    if (pName == null) {
                        Log.e(TAG, "Package is malformed - no PARAMETER_NAME");
                        continue;
                    }

                    final File curFile = new File(parent, pName.getValue());
                    MissionPackageExtractor.UnzipFile(
                            zipFile.getInputStream(curEntry), curFile,
                            false, buffer);
                    curContent.setParameter(new NameValuePair(
                            MissionPackageContent.PARAMETER_LOCALPATH, curFile
                                    .getAbsolutePath()));
                    unzippedReferences.add(curContent);
                }
            }
        }

        // now import (moves .shp and all related files, and send intent to load/import the data
        File destPath = _sort.getDestinationPath(shpFile);
        if (!_sort.beginImport(shpFile)) {
            throw new IOException(
                    "Unable to import file: " + shpFile.getName());
        }

        // now update local paths with correct/final paths
        parent = destPath.getParent();
        content.setParameter(new NameValuePair(
                MissionPackageContent.PARAMETER_LOCALPATH, destPath
                        .getAbsolutePath()));
        for (MissionPackageContent curContent : unzippedReferences) {
            if (curContent.hasParameter(MissionPackageContent.PARAMETER_NAME)) {
                NameValuePair nvp = curContent
                        .getParameter(MissionPackageContent.PARAMETER_NAME);
                if (nvp != null) {
                    File curFile = new File(parent, nvp.getValue());
                    curContent.setParameter(new NameValuePair(
                            MissionPackageContent.PARAMETER_LOCALPATH,
                            curFile.getAbsolutePath()));
                }
            }
        }

        // clean up unzip dir if necessary
        if (FileSystemUtils.isFile(shpDir)
                && IOProviderFactory.isDirectory(shpDir)) {
            File[] files = IOProviderFactory.listFiles(shpDir);
            if (files == null || files.length < 1)
                FileSystemUtils.deleteDirectory(shpDir, false);
        }

        // delete it's parent dir too...
        File unzipDir = new File(
                MissionPackageFileIO.getMissionPackageFilesPath(atakDataDir
                        .getAbsolutePath()),
                manifest.getUID());
        if (IOProviderFactory.exists(unzipDir)
                && IOProviderFactory.isDirectory(unzipDir)) {
            File[] files = IOProviderFactory.listFiles(unzipDir);
            if (files == null || files.length < 1)
                FileSystemUtils.deleteDirectory(unzipDir, false);
        }

        return true;
    }

}
