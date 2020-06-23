package com.atakmap.map.layer.model;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;

import org.w3c.dom.Document;
import javax.xml.XMLConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ModelFileUtils {

    public static String TAG = "ModelFileUtils";

    public static String fileExt(File file) {
        String extension = "";
        String fileName = file.getName();
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    public static List<File> findFiles(File f, Collection<String> exts) {
        if(f.isDirectory()) {
            LinkedList<File> result = new LinkedList<File>();
            File[] children = f.listFiles();
            if (children != null) {
                for(File c : children) {
                    List<File> r = findFiles(c, exts);
                    result.addAll(r);
                }
            }
            return result;
        } else if(exts.contains(fileExt(f).toLowerCase())) {
            return Collections.singletonList(f);
        }
        return Collections.emptyList();
    }

    public static File findHereOrAncestorFile(File dir, String base, String[] exts, int generationCount) {
        File file = FileSystemUtils.findFile(dir, base, exts);
        if (file == null && generationCount > 0)
            findHereOrAncestorFile(dir.getParentFile(), base, exts, generationCount - 1);
        return file;
    }

    public static Document parseXML(InputStream stream) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                dbf.setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true);
            } catch (Exception ignored) {
            }

            try {
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (Exception ignored) {
            }

            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(stream);
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            return null;
        }
    }

    public static InputStream openInputStream(String uri) {
        InputStream inputStream = null;
        try {
            File file = new File(uri);
            if (!file.exists()) {
                ZipVirtualFile zvf = new ZipVirtualFile(uri);
                if (zvf.exists())
                    inputStream = zvf.openStream();
            } else {
                inputStream = new FileInputStream(file);
            }
        } catch (IOException e) {
            // ignore
        }
        return inputStream;
    }
}
