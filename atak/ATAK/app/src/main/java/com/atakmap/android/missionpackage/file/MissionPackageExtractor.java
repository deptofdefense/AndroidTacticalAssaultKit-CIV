
package com.atakmap.android.missionpackage.file;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.importfiles.sort.ImportCotSort;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.event.MissionPackageEventProcessor;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Support for extracting a Mission Package
 * 
 * 
 */
public class MissionPackageExtractor implements IMissionPackageExtractor {
    private static final String TAG = "MissionPackageExtractor";


    /*
     * (non-Javadoc)
     * @see com.atakmap.android.missionpackage.file.IMissionPackageExtractor#extract(java.io.File)
     */
    @Override
    public MissionPackageManifest extract(Context context, File inFile,
            File atakRoot, boolean bImport)
            throws IOException {
        if (!FileSystemUtils.isFile(inFile)) {
            throw new IOException("Package does not exist: "
                    + inFile.getAbsolutePath());
        }

        byte[] buffer = new byte[FileSystemUtils.BUF_SIZE];
        char[] charBuffer = new char[FileSystemUtils.CHARBUFFERSIZE];

        // see if package has a manifest to provide name, UUID, etc
        MissionPackageManifest manifest = GetManifest(inFile);
        if (manifest == null || !manifest.isValid()) {
            throw new IOException("Zip does not contain a valid manifest: "
                    + inFile.getAbsolutePath());
        }

        manifest.setPath(inFile.getAbsolutePath());

        List<String> cotXml = new ArrayList<>();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(inFile);
            List<MissionPackageContent> contents = manifest._contents
                    .getContents();
            if (contents == null || contents.size() < 1) {
                Log.w(TAG, "Manifest has no contents: " + manifest);
                return manifest;
            }

            // create importer once rather for each file
            List<ImportResolver> sorters = new ArrayList<>();
            if (bImport) {
                sorters = ImportFilesTask.GetSorters(context,
                        true, false, false, false);
                Log.d(TAG, "Setting import handlers: " + sorters.size());
            } else {
                Log.d(TAG, "Extracting only, no import handlers");
            }
            MapView mv = MapView.getMapView();
            MissionPackageEventProcessor eventProcessor = new MissionPackageEventProcessor(
                    context, mv != null ? mv.getRootGroup() : null);

            Log.d(TAG,
                    "Extracting " + contents.size()
                            + " contents from Manifest: "
                            + manifest);
            for (MissionPackageContent content : contents) {
                if (content == null || !content.isValid()) {
                    Log.w(TAG,
                            "Skipping invalid content: "
                                    + (content == null ? ""
                                            : content
                                                    .toString()));
                    continue;
                }

                if (content.isIgnore()) {
                    Log.d(TAG,
                            "Skipping ignore content: " + content);
                    continue;
                }

                ZipEntry entry = zipFile.getEntry(
                        manifest.getZipPath(content.getManifestUid()));

                if (entry == null) {
                    Log.d(TAG,
                            "Package does not contain manifest content: "
                                    + content.getManifestUid());
                    continue;
                }

                if (content.isCoT()) {
                    Log.d(TAG,
                            "Extracting COT Content: "
                                    + content.getManifestUid());
                    String eventXml = ExtractCoTContent(content,
                            zipFile.getInputStream(entry), charBuffer);
                    if (eventXml != null)
                        cotXml.add(eventXml);
                } else {
                    Log.d(TAG,
                            "Extracting FILE Content: "
                                    + content.getManifestUid());
                    try {
                        eventProcessor.extract(manifest, content, zipFile,
                                atakRoot, buffer,
                                sorters);
                    } catch (IOException e) {
                        Log.e(TAG,
                                "Failed to extract File: "
                                        + content);
                    }
                }
            } // end content loop

            // 1st pass - import CoT events
            List<String> deferred = new ArrayList<>();
            for (String eventXml : cotXml) {
                if (handleCoT(eventXml) == ImportResult.DEFERRED)
                    deferred.add(eventXml);
            }

            // 2nd pass - import deferred events
            for (String eventXml : deferred)
                handleCoT(eventXml);

            // clean up unzip dir if necessary
            File unzipDir = new File(
                    MissionPackageFileIO.getMissionPackageFilesPath(atakRoot
                            .getAbsolutePath()),
                    manifest.getUID());
            if (IOProviderFactory.exists(unzipDir)
                    && IOProviderFactory.isDirectory(unzipDir)) {
                File[] files = IOProviderFactory.listFiles(unzipDir);
                if (files == null || files.length < 1)
                    FileSystemUtils.deleteDirectory(unzipDir, false);
            }

            for (ImportResolver sorter : sorters) {
                if (sorter.getFileSorted()) {
                    sorter.finalizeImport();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract: " + inFile.getAbsolutePath(), e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    Log.e(TAG,
                            "Failed to close zip file: "
                                    + inFile.getAbsolutePath(),
                            e);
                }
            }
        }

        return manifest;
    }

    @Override
    public MissionPackageManifest getManifest(File zipFile) {
        return GetManifest(zipFile);
    }

    public static MissionPackageManifest GetManifest(File zip) {
        return MissionPackageExtractorFactory.getManifest(zip);
    }

    public static void UnzipFile(InputStream zis, File file,
            boolean renameIfExists, byte[] buffer)
            throws IOException {
        // be sure parent dirs exist
        if (!IOProviderFactory.exists(file.getParentFile())) {
            if (!IOProviderFactory.mkdirs(file.getParentFile()))
                throw new IOException("Unable to create directory: "
                        + file.getParent());
        }

        String filepath = file.getAbsolutePath();
        if (IOProviderFactory.exists(file)) {
            if (renameIfExists) {
                filepath = FileSystemUtils.getRandomFilepath(filepath);
                Log.d(TAG, "File already exists, renaming to: " + filepath);
            } else {
                Log.d(TAG, "File already exists, over-writing: " + filepath);
            }
        }

        Log.d(TAG, "Unzipping file to: " + filepath);
        FileOutputStream dest = IOProviderFactory
                .getOutputStream(new File(filepath));
        FileSystemUtils.copyStream(zis, false, dest, true, buffer);
    }

    /**
     * Extract fileName from zipFile into targetDirectory
     *
     * @param manifest the manifest for the data package
     * @param zipFilePath the file that is the data package
     * @param content the content to extract associated with the data package
     * @return true if the file is extracted that is associated with the content
     */
    public static boolean ExtractFile(MissionPackageManifest manifest, File zipFilePath,
            MissionPackageContent content) {
        if (!FileSystemUtils.isFile(zipFilePath)) {
            Log.e(TAG,
                    "Package does not exist: " + zipFilePath.getAbsolutePath());
            return false;
        }

        // Presumably the file was previously imported via Import Manager and its "localpath" was
        // properly set so it a simple re-extract to that location
        NameValuePair filepath = content
                .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
        if (filepath == null || !filepath.isValid()) {
            Log.e(TAG, "Package content invalid local path for content: "
                    + content);
            return false;
        }

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zipFilePath);
            ZipEntry entry = zipFile.getEntry(manifest.getZipPath(content.getManifestUid()));
            if (entry == null) {
                Log.e(TAG,
                        "Package does not contain File: "
                                + content.getManifestUid());
                return false;
            }

            UnzipFile(zipFile.getInputStream(entry),
                    new File(FileSystemUtils
                            .sanitizeWithSpacesAndSlashes(filepath.getValue())),
                    false,
                    new byte[FileSystemUtils.BUF_SIZE]);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract file: " + filepath.getValue(), e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close zip file: " + zipFilePath, e);
                }
            }
        }

        return false;
    }

    private String ExtractCoTContent(MissionPackageContent content,
            InputStream inputStream, char[] charBuffer) {
        // TODO OK to not close BufferedReader? Will it close cleanly later when I
        // close the underlying ZipInputStream
        try {
            String event = FileSystemUtils.copyStreamToString(inputStream,
                    false,
                    FileSystemUtils.UTF8_CHARSET, charBuffer);

            if (FileSystemUtils.isEmpty(event))
                throw new IOException("Failed to unzip CoT Content: "
                        + content.toString());

            // validate valid CoT
            if (!ImportCotSort.isCoT(event)) {
                throw new IOException("Skipping invalid CoT Content: "
                        + content.toString());
            }

            String uid = GetCoTEventUID(event);
            if (FileSystemUtils.isEmpty(uid)) {
                throw new IOException("Failed to unzip CoT UID: "
                        + content.toString());
            }
            // probably already set in the Manifest
            content.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_UID, uid));
            content.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_LOCALISCOT,
                    Boolean.TRUE.toString()));
            return event;
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract CoT: " + content.toString());
        }
        return null;
    }

    private static ImportResult handleCoT(String eventXml) {
        CotMapComponent cmc = CotMapComponent.getInstance();
        if (cmc == null)
            return ImportResult.FAILURE;

        CotEvent event = CotEvent.parse(eventXml);
        if (event == null)
            return ImportResult.FAILURE;

        Bundle extras = new Bundle();
        extras.putString("from", "MissionPackage");
        extras.putString("fromClass", MissionPackageExtractor.class.getName());
        extras.putBoolean("visible", true);
        return cmc.processCotEvent(event, extras);
    }
    /**
     * Extract CoT UID from zipFile, optionally broadcast CoT for internal processing by ATAK
     * Please note - this method does not handle properly mission packages that contain a
     * directory in the root of the zip followed by the manifest folder and contents one level lower
     * Please consider using ExtractCot(Context, File, MissionPackageManagement, MissionPackageContent, boolean)
     * @param context the application context
     * @param zipFilePath the mission package path
     * @param content the mission package content
     * @return null upon error
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since="4.6.1", forRemoval = true, removeAt = "4.9.1")
    public static String ExtractCoT(Context context, File zipFilePath,
            MissionPackageContent content, boolean broadcast) {
        return ExtractCoT(zipFilePath, null, content, broadcast);
    }

    /**
     * Extract CoT UID from zipFile, optionally broadcast CoT for internal processing by ATAK
     * @param zipFilePath the mission package path
     * @param manifest the manifest, if supplied can be used to extract files relative to the manifest location.
     * @param content the content of the mission package
     * @return null upon error
     */
    public static String ExtractCoT(File zipFilePath,
            MissionPackageManifest manifest,
            MissionPackageContent content, boolean broadcast) {
        if (!FileSystemUtils.isFile(zipFilePath)) {
            Log.e(TAG,
                    "Package does not exist: " + zipFilePath.getAbsolutePath());
            return null;
        }

        if (content == null || !content.isValid()) {
            Log.e(TAG, "Package content invalid for zip: " + zipFilePath);
            return null;
        }

        NameValuePair uid = content
                .getParameter(MissionPackageContent.PARAMETER_UID);
        if (uid == null || !uid.isValid()) {
            Log.e(TAG,
                    "Package content invalid UID for content: "
                            + content);
            return null;
        }

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zipFilePath);
            ZipEntry entry;

            if (manifest != null) 
                  entry = zipFile.getEntry(manifest.getZipPath(content.getManifestUid()));
            else 
                  entry = zipFile.getEntry(content.getManifestUid());
 
            if (entry == null) {
                Log.e(TAG,
                        "Package does not contain CoT UID: " + uid.getValue());
                return null;
            }

            String event = FileSystemUtils.copyStreamToString(
                    zipFile.getInputStream(entry), false,
                    FileSystemUtils.UTF8_CHARSET);
            if (FileSystemUtils.isEmpty(event)) {
                Log.e(TAG, "Failed to unzip CoT with UID: " + uid.getValue());
                return null;
            }

            if (broadcast)
                handleCoT(event);

            return event;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract CoT with UID: " + uid.getValue(), e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close zip file: " + zipFilePath, e);
                }
            }
        }

        return null;
    }

    /**
     * no need to parse entire XML, just peek and find the UID
     * 
     * @param event
     * @return
     */
    static String GetCoTEventUID(String event) {
        return GetXmlAttribute(event, "uid");
    }

    /**
     * Find "<name>="<value>" in the specified content Return value
     * 
     * @param xml
     * @param name
     * @return
     */
    public static String GetXmlAttribute(String xml, String name) {

        // TODO error checking, make more robust
        // TODO could be cleaner using REGEX

        // parse UID account for ' or " delimiters
        int length = name.length();
        String value = "";
        // uid='0d00a599-a63e-42d4-b780-abe14195754d'
        int start = xml.indexOf(name + "=");
        if (start > 0) {
            // look for end after uid='
            int end = xml.indexOf("'", start + length + 2);
            int end2 = xml.indexOf("\"", start + length + 2);

            if (end < 0)
                end = end2;

            if (end2 < end && end2 > 0)
                end = end2;

            if (end > 0) {
                value = xml.substring(start + length + 2, end);
            }
        }

        return value;
    }
}
