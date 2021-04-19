
package com.atakmap.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.xml.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

class MigrationShim {
    private static final String TAG = "MigrationShim";

    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    static void onMigration(final Activity activity) {
        final File migrationPackage = new File(
                Environment.getExternalStorageDirectory(), "atak/.migration");
        try {
            File dbFile = activity.getDatabasePath("credentials.sqlite");
            if (migrationPackage.exists() && !dbFile.exists()) {

                Log.d(TAG, "migration path triggered");
                try {
                    unzip(migrationPackage, activity);
                } catch (IOException ioe) {
                    Log.e(TAG, "unzip failed", ioe);
                }
            }
            migrationPackage.delete();
        } catch (Exception e) {
            Log.e(TAG, "unable to migrate", e);
        }
    }

    private static void unzip(final File file, Activity activity)
            throws IOException {

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
            // Process each entry
            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = zipFileEntries.nextElement();
                final String currentEntry = entry.getName();

                try (BufferedInputStream is = new BufferedInputStream(zipFile
                        .getInputStream(entry))) {

                    if (!entry.isDirectory()) {

                        if (currentEntry.startsWith("databases")) {

                            final String dbName = currentEntry.substring(10);
                            File dbFile = activity.getDatabasePath(dbName);

                            try {
                                dbFile.getParentFile().mkdirs();
                            } catch (Exception ignored) {
                            }

                            //Log.d(TAG, "debug.remove writing: " + dbFile);
                            try (OutputStream os = new FileOutputStream(dbFile)) {
                                FileSystemUtils.copy(is, os);
                            }
                        } else if (currentEntry.startsWith("files")) {

                            final String fileName = currentEntry.substring(6);
                            final String gdalDataPath = activity.getFilesDir()
                                    .getAbsolutePath() + File.separator + "GDAL";

                            try {
                                activity.getFilesDir().getParentFile().mkdirs();
                            } catch (Exception ignored) {
                            }

                            File f = new File(gdalDataPath, fileName);
                            if (!f.getParentFile().exists()
                                    && !f.getParentFile().mkdirs()) {
                                //Log.d(TAG, "debug.remove could not create: " + fileName);
                            } else {
                                //Log.d(TAG, "debug.remove writing: " + fileName);
                                FileSystemUtils.copy(is, new FileOutputStream(f));
                            }
                        } else if (currentEntry.startsWith("shared_prefs")) {
                            // process the preferences
                            parsePreferences(currentEntry.substring(13), is,
                                    activity);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error extracting", e);
                }
            }
        } finally {
            zipFile.close();
        }
    }

    private static void parsePreferences(final String file,
            final InputStream is, final Activity a) {
        final DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();

        final Document doc;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(is);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            Log.e(TAG, "error parsing: " + file, e);
            return;
        }
        String preferenceName = file.replace(".xml", "");
        if (preferenceName.startsWith("com.atakmap.app"))
            preferenceName = "com.atakmap.app.civ_preferences";

        final SharedPreferences pref = a.getSharedPreferences(preferenceName,
                Context.MODE_PRIVATE);
        //Log.d(TAG, "debug.remove received a request to load: " + preferenceName);

        NodeList items = doc.getChildNodes();
        for (int j = 0; j < items.getLength(); j++) {
            Node entry = items.item(j);
            if (entry.getNodeName().equals("map"))
                readPreferences(entry.getChildNodes(), pref);
            Log.d(TAG, "debug.remove " + entry.getNodeName());
        }
    }

    private static void readPreferences(final NodeList nodeList,
            final SharedPreferences preferences) {
        final SharedPreferences.Editor editor = preferences.edit();
        for (int j = 0; j < nodeList.getLength(); j++) {
            final Node entry = nodeList.item(j);
            if (entry.getNodeType() != Node.ELEMENT_NODE)
                continue;

            final String entryName = entry.getNodeName();
            //Log.d(TAG, "debug.remove name: " + entryName);
            final NamedNodeMap map = entry.getAttributes();
            final String n = map.getNamedItem("name").getNodeValue();
            try {
                switch (entryName) {
                    case "boolean": {
                        final boolean v = Boolean.parseBoolean(
                                map.getNamedItem("value").getNodeValue());
                        //Log.d(TAG, "debug.remove bool " + n + " " + v);
                        editor.putBoolean(n, v);
                        break;
                    }
                    case "string": {
                        final String v = entry.getTextContent();
                        //Log.d(TAG, "debug.remove string " + n + " " + v);
                        editor.putString(n, v);
                        break;
                    }
                    case "int": {
                        final int v = Integer.parseInt(
                                map.getNamedItem("value").getNodeValue());
                        //Log.d(TAG, "debug.remove int " + n + " " + v);
                        editor.putInt(n, v);
                        break;
                    }
                    case "float": {
                        final float v = Float.parseFloat(
                                map.getNamedItem("value").getNodeValue());
                        //Log.d(TAG, "debug.remove float " + n + " " + v);
                        editor.putFloat(n, v);
                        break;
                    }
                    case "long": {
                        final long v = Long.parseLong(
                                map.getNamedItem("value").getNodeValue());
                        //Log.d(TAG, "debug.remove long " + n + " " + v);
                        editor.putLong(n, v);
                        break;
                    }
                    default:
                        break;

                }
            } catch (Exception e) {
                Log.e(TAG, "error reading", e);
            }

        }
        editor.apply();

    }
}
