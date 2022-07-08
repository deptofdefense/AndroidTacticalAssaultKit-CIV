
package com.atakmap.android.importfiles.task;

import android.content.Context;
import android.os.AsyncTask;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.sort.ImportAPKSort;
import com.atakmap.android.importfiles.sort.ImportAlternateContactSort;
import com.atakmap.android.importfiles.sort.ImportCertSort;
import com.atakmap.android.importfiles.sort.ImportCotSort;
import com.atakmap.android.importfiles.sort.ImportDRWSort;
import com.atakmap.android.importfiles.sort.ImportDTEDSort;
import com.atakmap.android.importfiles.sort.ImportGMLSort;
import com.atakmap.android.importfiles.sort.ImportGMLZSort;
import com.atakmap.android.importfiles.sort.ImportGPXSort;
import com.atakmap.android.importfiles.sort.ImportGPXRouteSort;
import com.atakmap.android.importfiles.sort.ImportGRGSort;
import com.atakmap.android.importfiles.sort.ImportINFZSort;
import com.atakmap.android.importfiles.sort.ImportJPEGSort;
import com.atakmap.android.importfiles.sort.ImportJSONPrefSort;
import com.atakmap.android.importfiles.sort.ImportKMLSort;
import com.atakmap.android.importfiles.sort.ImportKMZPackageSort;
import com.atakmap.android.importfiles.sort.ImportKMZSort;
import com.atakmap.android.importfiles.sort.ImportLPTSort;
import com.atakmap.android.importfiles.sort.ImportLayersSort;
import com.atakmap.android.importfiles.sort.ImportMVTSort;
import com.atakmap.android.importfiles.sort.ImportMissionPackageSort;
import com.atakmap.android.importfiles.sort.ImportPrefSort;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.sort.ImportSHPSort;
import com.atakmap.android.importfiles.sort.ImportSHPZSort;
import com.atakmap.android.importfiles.sort.ImportDTEDZSort;
import com.atakmap.android.importfiles.sort.ImportSQLiteSort;
import com.atakmap.android.importfiles.sort.ImportSupportInfoSort;
import com.atakmap.android.importfiles.sort.ImportTXTSort;
import com.atakmap.android.importfiles.sort.ImportTilesetSort;
import com.atakmap.android.importfiles.sort.ImportUserIconSetSort;
import com.atakmap.android.importfiles.sort.ImportVideoSort;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.ImageryFileType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Background task to parse a directory and move files to other directories, which are watched by
 * other components which handle import into ATAK
 * 
 * 
 */
public class ImportFilesTask extends AsyncTask<Void, Void, Integer> {

    private static final String TAG = "ImportFilesTask";

    private static final Set<String> extensions = new HashSet<>();
    // array form of the above set
    private static String[] extensionList;

    static {
        // setup list of supported file extensions
        extensions.add("dpk");
        extensions.add("zip");
        extensions.add("kml");
        extensions.add("kmz");
        extensions.add("lpt");
        extensions.add("drw");
        extensions.add("xml");
        extensions.add("txt");
        extensions.add("pref");
        extensions.add("cot");
        extensions.add("sqlite");
        extensions.add("shp");
        extensions.add("gml");
        extensions.add("gpx");
        extensions.add("jpg");
        extensions.add("jpeg");
        extensions.add("png");
        extensions.add("csv");
        extensions.add("inf");
        extensions.add("infz");
        extensions.add("apk");
        extensions.addAll(ImportVideoSort.VIDEO_EXTENSIONS);

        // now pull in all supported native imagery extensions
        String[] exts;
        for (ImageryFileType.AbstractFileType fileType : ImageryFileType
                .getFileTypes()) {
            exts = fileType.getSuffixes();
            if (exts == null || exts.length < 1)
                continue;

            for (String ext : exts) {
                if (FileSystemUtils.isEmpty(ext))
                    continue;

                // found a new supported extension
                extensions.add(ext);
            }
        }

        extensionList = extensions.toArray(new String[0]);
        Log.d(TAG,
                "internal registered file extension: "
                        + Arrays.toString(extensionList));
    }

    /**
     * Register an extension should be called only by ImportExportMapComponent::addImporterClass
     */
    public static synchronized void registerExtension(String extension) {
        if (extension != null && extension.length() > 0) {
            if (extension.startsWith("."))
                extension = extension.substring(1);
            Log.d(TAG, "registering external extension: " + extension);
            extensions.add(extension);
            extensionList = extensions.toArray(new String[0]);
        }
    }

    /**
     * Unregister an extension should be called only by ImportExportMapComponent::addImporterClass
     */
    public static synchronized void unregisterExtension(
            String extension) {
        if (extension != null && extension.length() > 0) {
            if (extension.startsWith("."))
                extension = extension.substring(1);
            Log.d(TAG, "unregistering external extension: " + extension);
            extensions.remove(extension);
            extensionList = extensions.toArray(new String[0]);
        }
    }

    public static synchronized String[] getSupportedExtensions() {
        return extensionList;
    }

    /**
     * Destination path of already sorted files, do not overwrite during a single import as ATAK may
     * still be processing it...
     */
    private final Set<String> _sortedFiles;

    private final Context _context;

    public ImportFilesTask(Context context) {
        this._context = context;
        this._sortedFiles = new HashSet<>();
    }

    @Override
    protected Integer doInBackground(Void... params) {

        Thread.currentThread().setName("ImportFilesTask");

        String[] atakRoots = FileSystemUtils.findMountPoints();
        if (atakRoots == null || atakRoots.length < 1) {
            Log.w(TAG, "Found no ATAK mount points");
            return 0;
        }

        // get sorters, require proper file extensions, move files into atak data dir
        List<ImportResolver> sorters = GetSorters(_context, true, false, false,
                false);
        if (sorters == null || sorters.size() < 1) {
            Log.w(TAG, "Found no ATAK import sorters");
            return 0;
        }

        int numberSorted = 0;
        for (String dir : atakRoots) {
            if (dir == null || dir.length() < 1)
                continue;

            File importDir = new File(_context.getCacheDir(),
                    FileSystemUtils.ATAKDATA);
            numberSorted += sort(importDir, sorters);
        }

        Log.d(TAG, "Importing from atakroots numberSorted: " + numberSorted);
        return numberSorted;
    }

    /**
     * Get list of sorters for supported file types, with the specified configuration settings
     * 
     * @param context
     * @param validateExt
     * @param copyFile
     * @param importInPlace - see ImportInPlaceResolver.ctor
     * @param bKMZStrict -see ImportKMZSort._bStrict
     * @return
     */
    public static List<ImportResolver> GetSorters(Context context,
            boolean validateExt,
            boolean copyFile, boolean importInPlace, boolean bKMZStrict) {
        List<ImportResolver> sorters = new ArrayList<>();

        sorters.add(new ImportSupportInfoSort(copyFile));

        // check mission package manifest prior to KMZ, since a Mission Package
        // may contain a KML file and in that case import could be improperly
        // classified as a KMZ file (if validateExt is false e.g. Import
        // Manager RemoteResource)
        sorters.add(new ImportMissionPackageSort.ImportMissionV1PackageSort(
                context, validateExt,
                copyFile, true));
        sorters.add(new ImportMissionPackageSort.ImportMissionV2PackageSort(
                context, validateExt,
                copyFile, true));

        sorters.add(new ImportUserIconSetSort(context, validateExt));

        sorters.add(new ImportKMZPackageSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportGRGSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportKMLSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportKMZSort(context, validateExt, copyFile,
                importInPlace, bKMZStrict));
        sorters.add(new ImportTXTSort(context, ".xml", validateExt, copyFile));
        sorters.add(new ImportTXTSort(context, ".txt", validateExt, copyFile));
        sorters.add(new ImportAlternateContactSort(context, validateExt,
                copyFile));
        sorters.add(new ImportSQLiteSort(context, validateExt, copyFile));
        sorters.add(new ImportPrefSort(context, validateExt, copyFile));
        sorters.add(new ImportJSONPrefSort(context, validateExt, copyFile));
        sorters.add(new ImportCertSort(context, validateExt, copyFile));
        sorters.add(new ImportDRWSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportLPTSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportGPXSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportGPXRouteSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportJPEGSort(context, ".jpg", validateExt, copyFile));
        sorters.add(
                new ImportJPEGSort(context, ".jpeg", validateExt, copyFile));
        sorters.add(new ImportCotSort(context, validateExt, copyFile));
        sorters.add(new ImportTilesetSort(context, validateExt, copyFile));

        sorters.add(new ImportSHPSort(context, validateExt, copyFile,
                importInPlace));

        sorters.add(new ImportSHPZSort(context, validateExt, copyFile,
                importInPlace));

        sorters.add(new ImportGMLSort(context, validateExt, copyFile,
                importInPlace));
        sorters.add(new ImportGMLZSort(context, validateExt, copyFile,
                importInPlace));

        sorters.add(new ImportMVTSort(context, validateExt, copyFile,
                importInPlace));

        sorters.add(new ImportDTEDZSort.ImportDTEDZv1Sort(context, validateExt,
                copyFile,
                importInPlace));
        sorters.add(new ImportDTEDZSort.ImportDTEDZv2Sort(context, validateExt,
                copyFile,
                importInPlace));
        sorters.add(new ImportDTEDSort(context));

        sorters.add(new ImportINFZSort(context, validateExt));
        sorters.add(new ImportAPKSort(context, validateExt));

        // TODO: Since the video sorters currently do not have a way of
        //  validating a file w/out the extension, always check the extension
        //  for now - otherwise any file that makes it this far will be
        //  considered a video if validateExt is false
        // See ATAK-10892 - Files were previously falling through and being
        // accepted by the MPEG sorter if all other matchers failed
        sorters.add(new ImportVideoSort(context, true, copyFile));

        // now add dynamically registered importers
        // TODO we could further refactor all resolvers to be dynamically registered, and add
        // priority for importers, to determine order in which they are evaluated, if necessary
        if (ImportExportMapComponent.getInstance() != null) {
            Collection<ImportResolver> importerResolvers = ImportExportMapComponent
                    .getInstance().getImporterResolvers();
            if (importerResolvers != null && importerResolvers.size() > 0) {
                for (ImportResolver resolver : importerResolvers) {
                    try {
                        if (resolver != null) {
                            Log.d(TAG, "Adding Import Resolver of type: "
                                    + resolver.getClass().getName());
                            //update current options on this resolver
                            resolver.setOptions(validateExt, copyFile);
                            sorters.add(resolver);
                        } else {
                            Log.w(TAG, "Failed to add Importer");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to add importer", e);
                    }
                }
            }
        }

        // The Layer importer tends to match a lot of things and can get in the way of things
        // like the GRGSort. Add it near the end so things like the MissionPackage extractor
        // can pick an appropriate importer without prompting the user.
        sorters.add(new ImportLayersSort(context));

        // finally if its a zip (but has no manifest) lets create a Mission Package out of it and
        // import it contents
        sorters.add(new ImportMissionPackageSort.ImportMissionV1PackageSort(
                context, validateExt,
                copyFile, false));
        sorters.add(new ImportMissionPackageSort.ImportMissionV2PackageSort(
                context, validateExt,
                copyFile, false));
        return sorters;
    }

    private int sort(File dir, List<ImportResolver> sorters) {
        if (dir == null) {
            Log.d(TAG, "Import directory null.");
            return 0;
        } else if (!IOProviderFactory.exists(dir)) {
            Log.d(TAG, "Import dir not found: " + dir.getAbsolutePath());
            return 0;
        } else if (!IOProviderFactory.isDirectory(dir)) {
            Log.d(TAG, "Import path not a directory: " + dir.getAbsolutePath());
            return 0;
        }

        Log.d(TAG, "Importing from directory: " + dir.getAbsolutePath());
        int numberSorted = 0;
        File[] files = IOProviderFactory.listFiles(dir);
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file == null || !IOProviderFactory.exists(file))
                    continue;

                // if subdir, recurse
                if (IOProviderFactory.isDirectory(file)) {
                    numberSorted += sort(file, sorters);
                    continue;
                }

                // otherwise attempt to sort the file
                boolean sorted = false;
                for (ImportResolver sorter : sorters) {
                    // see if this sorter can handle the current file
                    if (sorter.match(file)) {
                        // do not overwrite is we've already imported a file to the anticipated
                        // location
                        File destPath = sorter.getDestinationPath(file);
                        if (destPath == null) {
                            Log.w(TAG,
                                    sorter
                                            + ", Unable to determine destination path for: "
                                            + file.getAbsolutePath());
                            continue;
                        }

                        if (_sortedFiles.contains(destPath.getAbsolutePath())) {
                            Log.w(TAG,
                                    sorter
                                            + ", Matched, but destination path already exists: "
                                            + destPath.getAbsolutePath());
                            break;
                        }

                        // now attempt to sort (i.e. move the file to proper location)
                        sorted = sorter.beginImport(file);
                        if (sorted) {
                            numberSorted++;
                            _sortedFiles.add(destPath.getAbsolutePath());
                            Log.d(TAG,
                                    sorter + ", Sorted: "
                                            + file.getAbsolutePath()
                                            + " to "
                                            + destPath.getAbsolutePath());
                            break;
                        } else
                            Log.w(TAG,
                                    sorter
                                            + ", Matched, but did not sort: "
                                            + file.getAbsolutePath());
                    } // end if sorter match was found
                }

                if (!sorted) {
                    Log.i(TAG,
                            "Did not sort unsupported file: "
                                    + file.getAbsolutePath());
                }
            } // end file loop
        }

        // if no files left in this directory, remove it
        files = IOProviderFactory.listFiles(dir);

        if (IOProviderFactory.exists(dir) && IOProviderFactory.isDirectory(dir)
                && (files == null || files.length < 1)) {
            Log.i(TAG, "Cleaning up empty directory: " + dir.getAbsolutePath());
            FileSystemUtils.delete(dir);
        }

        for (ImportResolver sorter : sorters) {
            if (sorter.getFileSorted()) {
                sorter.finalizeImport();
            }
        }

        return numberSorted;
    }
}
