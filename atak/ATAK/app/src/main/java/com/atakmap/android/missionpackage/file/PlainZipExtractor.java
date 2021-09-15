
package com.atakmap.android.missionpackage.file;

import android.content.Context;

import com.atakmap.android.importfiles.sort.ImportCotSort;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Plain Old Zip File Extractor Convert to a Mission Package Zip with a manifest Use current
 * extractor to process the converted mission package
 * 
 * 
 */
public class PlainZipExtractor implements IMissionPackageExtractor {

    private static final String TAG = "PlainZipExtractor";

    /**
     * Specifies how to handle a given Zip Entry
     * 
     * 
     */
    protected enum ZipEntryAction {
        INCLUDE,
        IGNORE,
        OMIT
    }

    @Override
    public MissionPackageManifest extract(Context context, File inZip,
            File atakRoot, boolean bImport) {
        if (!FileSystemUtils.isFile(inZip)) {
            Log.e(TAG,
                    "Cannot extract missing file: " + inZip.getAbsolutePath());
            return null;
        }

        // write out to a temp file
        File outZip = new File(
                MissionPackageFileIO
                        .getMissionPackageIncomingDownloadPath(atakRoot
                                .getAbsolutePath()),
                UUID.randomUUID().toString());

        Log.d(TAG,
                "Processing plain zip: " + inZip.getAbsolutePath() + " into "
                        + outZip.getAbsolutePath());
        byte[] buffer = new byte[FileSystemUtils.BUF_SIZE];
        char[] charBuffer = new char[FileSystemUtils.CHARBUFFERSIZE];
        List<String> warnings = new ArrayList<>();

        // prep IO
        MissionPackageManifest manifest = getManifest(inZip);
        try (ZipInputStream zin = new ZipInputStream(
                IOProviderFactory.getInputStream(inZip));
                ZipOutputStream zos = FileSystemUtils
                        .getZipOutputStream(outZip)) {
            // read in from plain old zip
            ZipEntry zinEntry = null;

            // iterate all zip entries
            while ((zinEntry = zin.getNextEntry()) != null) {
                ZipEntryAction action = getAction(zinEntry);
                if (action == ZipEntryAction.OMIT) {
                    continue;
                }

                // upon error skip to next entry
                try {

                    // if .cot and matches sorter, then create a COT entry (with UID set)
                    MissionPackageContent content = new MissionPackageContent(
                            zinEntry.getName());

                    if (zinEntry.getName().toLowerCase(LocaleUtil.getCurrent())
                            .endsWith(".cot")) {
                        Log.d(TAG, "Processing COT: " + zinEntry.getName());

                        // read event from stream
                        String event = FileSystemUtils.copyStreamToString(zin,
                                false,
                                FileSystemUtils.UTF8_CHARSET, charBuffer);
                        if (FileSystemUtils.isEmpty(event)) {
                            throw new IOException(
                                    "Failed to unzip CoT Content: "
                                            + zinEntry.getName());
                        }

                        // Note currently if a zip has a .cot that does not pass the matcher then it
                        // is thrown away, it will
                        // not make it into the output mission package .zip
                        if (!ImportCotSort.isCoT(event)) {
                            throw new IOException(
                                    "Skipping invalid CoT Content: "
                                            + zinEntry.getName());
                        }

                        String uid = MissionPackageExtractor
                                .GetCoTEventUID(event);
                        if (FileSystemUtils.isEmpty(uid)) {
                            throw new IOException("Failed to unzip CoT UID: "
                                    + zinEntry.getName());
                        }

                        content.setParameter(new NameValuePair(
                                MissionPackageContent.PARAMETER_UID,
                                uid));
                        content.setParameter(new NameValuePair(
                                MissionPackageContent.PARAMETER_LOCALISCOT,
                                Boolean.TRUE.toString()));

                        // create new zip entry
                        ZipEntry entry = new ZipEntry(content.getManifestUid());
                        zos.putNextEntry(entry);

                        // stream from string to out zip
                        FileSystemUtils.copyStream(new StringBufferInputStream(
                                event), true, zos,
                                false, buffer);

                    } else {
                        Log.d(TAG, "Processing FILE: " + zinEntry.getName());
                        // Note, MissionPackageExtractor sets LOCALPATH during extraction

                        // create new zip entry
                        ZipEntry entry = new ZipEntry(content.getManifestUid());
                        zos.putNextEntry(entry);

                        // stream from in zip to out zip
                        FileSystemUtils.copyStream(zin, false, zos, false,
                                buffer);
                    }

                    // close current file
                    zos.closeEntry();

                    // now add content to manifest
                    if (action == ZipEntryAction.IGNORE) {
                        Log.d(TAG,
                                "Setting ignore flag for "
                                        + zinEntry.getName());
                        content.setIgnore(true);
                    }
                    addContent(manifest, content);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to add Entry: " + zinEntry.getName(), e);
                    warnings.add("Skipping, Zip Error for Entry: "
                            + zinEntry.getName());
                }
            } // end zin loop

            // add manifest to zos
            MissionPackageBuilder.AddManifest(zos, manifest);
        } catch (IOException ie) {
            Log.e(TAG, "Failed to extract: " + inZip.getAbsolutePath(), ie);
        }

        try {
            // now see if out zip was created successfully
            if (!FileSystemUtils.isFile(outZip)
                    || IOProviderFactory.length(outZip) < 1) {
                Log.e(TAG,
                        "Failed to create file: " + outZip.getAbsolutePath());
                return null;
            }

            // move (or copy) file to permanent location (overwrite input zip)
            if (!FileSystemUtils.renameTo(outZip, inZip)) {
                throw new IOException("Failed to rename package: "
                        + outZip.getName());
            }

            // TODO could make warnings available to user...
            // now use current extractor to process the package
            Log.d(TAG, "Re-created mission package:" + inZip.getAbsolutePath());
            IMissionPackageExtractor extractor = new MissionPackageExtractor();
            return extractor.extract(context, inZip, atakRoot, bImport);
        } catch (IOException ie) {
            Log.e(TAG, "Failed to extract: " + inZip.getAbsolutePath(), ie);
        }

        return null;
    }

    @Override
    public MissionPackageManifest getManifest(File zipFile) {
        return new MissionPackageManifest(
                MissionPackageUtils.getUniqueName(MapView.getMapView()
                        .getContext(), zipFile.getName()),
                null);
    }

    /**
     * Omit directories and manifests, include all others
     * 
     * @param zinEntry
     * @return
     */
    protected ZipEntryAction getAction(ZipEntry zinEntry) {
        if (zinEntry.isDirectory()) {
            Log.d(TAG, "Skipping directory: " + zinEntry.getName());
            return ZipEntryAction.OMIT;
        }

        // if in "manifest" folder, skip it
        if (zinEntry
                .getName()
                .toLowerCase(LocaleUtil.getCurrent())
                .startsWith(
                        (MissionPackageBuilder.MANIFEST_PATH + File.separator)
                                .toLowerCase(LocaleUtil.getCurrent()))) {
            Log.d(TAG, "Skipping manifest: " + zinEntry.getName());
            return ZipEntryAction.OMIT;
        }

        return ZipEntryAction.INCLUDE;
    }

    protected boolean addContent(MissionPackageManifest manifest,
            MissionPackageContent content) {
        if (manifest == null || content == null || !content.isValid()) {
            Log.w(TAG, "Cannot add invalid content");
            return false;
        }

        return manifest.addContent(content);
    }
}
