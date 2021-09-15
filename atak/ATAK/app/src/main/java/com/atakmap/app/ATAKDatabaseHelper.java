
package com.atakmap.app;

import com.atakmap.android.location.LocationMapComponent;

import android.os.Bundle;
import android.support.util.Base64;

import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.Databases;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.app.Activity;

import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;

import com.atakmap.android.ipc.AtakBroadcast;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;

public class ATAKDatabaseHelper {

    final static int KEY_STATUS_OK = 1;
    final static int KEY_STATUS_FAILED = 0;
    private static final String TAG = "ATAKDatabaseHelper";

    public interface KeyStatusListener {
        void onKeyStatus(int ks);
    }

    /**
     * Prompts for a key from the user and tests the key to make sure it is valid.
     * If it is valid, then the process continues.   If it is not valid, then the 
     * user has the option to retry, move the databases out of the way, delete the
     * databases, or to exit the app.  
     * @param context the application context.
     * @param ksl the key status listener for when the key is entered properly.
     */
    public static void promptForKey(final Context context,
            final KeyStatusListener ksl) {

        final File testDb = FileSystemUtils.getItem("Databases/ChatDb2.sqlite");

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.v2");

        if (credentials == null)
            credentials = new AtakAuthenticationCredentials();



        // DB doesn't exist and there is no key; create original key
        if (!IOProviderFactory.exists(testDb) && (credentials == null
                || FileSystemUtils.isEmpty(credentials.username))) {

            changeKeyImpl(context, true, ksl);
            return;
        }
        // DB doesn't exist but we have key; create
                    if (!IOProviderFactory.exists(testDb)) {
            upgrade(context, ksl);
            return;
        }

        // DB exists; check key
        if (IOProviderFactory.isDefault() && !checkKeyAgainstDatabase(testDb)) {
            AlertDialog.Builder ad = new AlertDialog.Builder(context);
            ad.setCancelable(false);

            LayoutInflater inflater = LayoutInflater.from(context);

            final View dkView = inflater.inflate(R.layout.database_key, null);

            ad.setView(dkView).setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int id) {
                            EditText et = dkView
                                    .findViewById(R.id.key_entry);
                            String key = et.getText().toString();
                            if (FileSystemUtils.isEmpty(key)) {
                                promptForKey(context, ksl);
                            } else if (key.contains("'")) {
                                Toast.makeText(context,
                                        R.string.invalid_passphrase_tick,
                                        Toast.LENGTH_LONG)
                                        .show();

                                promptForKey(context, ksl);
                            } else {
                                AtakAuthenticationDatabase.saveCredentials(
                                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                                        "com.atakmap.app.v2", "atakuser", key,
                                        false);
                                if (checkKeyAgainstDatabase(testDb)) {
                                    upgrade(context, ksl);
                                } else {
                                    Toast.makeText(context,
                                            R.string.invalid_passphrase,
                                            Toast.LENGTH_SHORT).show();
                                    promptForKey(context, ksl);
                                }
                            }

                        }
                    }).setNeutralButton(R.string.remove_and_quit,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    promptForRemoval(context, ksl);
                                }
                            })
                    .setNegativeButton(R.string.quit,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    if (ksl != null)
                                        ksl.onKeyStatus(KEY_STATUS_FAILED);
                                }
                            });
            ad.show();
        } else {
            if (ksl != null)
                ksl.onKeyStatus(KEY_STATUS_OK);
        }

    }

    public static void removeDatabases() {
        IOProviderFactory.delete(FileSystemUtils.getItem("Databases/ChatDb2.sqlite"), IOProvider.SECURE_DELETE);
        IOProviderFactory.delete(FileSystemUtils.getItem("Databases/statesaver2.sqlite"), IOProvider.SECURE_DELETE);
        IOProviderFactory.delete(FileSystemUtils.getItem("Databases/crumbs2.sqlite"), IOProvider.SECURE_DELETE);
    }

    private static void promptForRemoval(final Context context,
            final KeyStatusListener ksl) {
        AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setCancelable(false);

        ad.setTitle(R.string.encrypted_removal)
                .setMessage(R.string.encrypted_removal_message)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                removeDatabases();
                                if (ksl != null)
                                    ksl.onKeyStatus(KEY_STATUS_FAILED);
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                if (ksl != null)
                                    ksl.onKeyStatus(KEY_STATUS_FAILED);
                            }
                        });
        ad.show();
    }

    private static void upgrade(final Context context,
            final KeyStatusListener ksl) {
        final ProgressDialog encryptDialog = new ProgressDialog(
                context);

        encryptDialog.setIcon(R.drawable.passphrase);
        encryptDialog.setTitle("Encrypting Data");
        encryptDialog.setMessage("please wait...");
        encryptDialog.setCancelable(false);
        encryptDialog.setIndeterminate(true);
        encryptDialog.show();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                encryptDb(FileSystemUtils.getItem("Databases/ChatDb.sqlite"),
                        FileSystemUtils.getItem("Databases/ChatDb2.sqlite"));
                encryptDb(
                        FileSystemUtils.getItem("Databases/statesaver.sqlite"),
                        FileSystemUtils
                                .getItem("Databases/statesaver2.sqlite"));
                encryptDb(FileSystemUtils.getItem("Databases/crumbs.sqlite"),
                        FileSystemUtils.getItem("Databases/crumbs2.sqlite"));

                encryptDialog.dismiss();
                if (ksl != null)
                    ksl.onKeyStatus(KEY_STATUS_OK);
            }
        };
        Thread t = new Thread(r, TAG + "-Upgrade");
        t.start();

    }

    private static final int REKEY_SUCCESS = 0;
    private static final int REKEY_SAME = 1;
    private static final int REKEY_FAILED = 2;

    private static int rekey(final String[] dbs, final String key,
            boolean legacy) {

        if (FileSystemUtils.isEmpty(key))
            return REKEY_FAILED;

        for (String db : dbs) {
            File ctFile = FileSystemUtils.getItem(db);
            if (!IOProviderFactory.exists(ctFile))
                return REKEY_FAILED;
        }

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        legacy ? "com.atakmap.app" : "com.atakmap.app.v2");

        // do not rekey if the key is the same
        if (credentials != null
                && !FileSystemUtils.isEmpty(credentials.password) &&
                credentials.password.equals(key)) {
            return REKEY_SAME;
        }

        for (String db : dbs) {
            File ctFile = FileSystemUtils.getItem(db);
            DatabaseIface ctDb = Databases.openOrCreateDatabase(
                    ctFile.getAbsolutePath());

            if (credentials != null
                    && !FileSystemUtils.isEmpty(credentials.username)) {

                ctDb.execute("PRAGMA key = '" + credentials.password + "'",
                        null);
                ctDb.execute("PRAGMA rekey = '" + key + "'", null);
            }
            ctDb.close();

        }
        return REKEY_SUCCESS;

    }

    public static void changeKey(final Context context) {
        changeKeyImpl(context, false, null);
    }

    private static void changeKeyImpl(final Context context, final boolean init, final KeyStatusListener listener) {
        LayoutInflater inflater = LayoutInflater.from(context);
        final View dkView = inflater.inflate(R.layout.database_rekey, null);

        dkView.findViewById(R.id.decision).setVisibility(View.GONE);
        ((RadioButton) dkView.findViewById(R.id.self)).setChecked(true);
        dkView.findViewById(R.id.key_entry).setEnabled(true);

        if(init)
            ((TextView) dkView.findViewById(R.id.introduction)).setText(R.string.create_encryption_passphrase);

        ((RadioGroup) dkView.findViewById(R.id.decision))
                .setOnCheckedChangeListener(
                        new RadioGroup.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(RadioGroup group,
                                    int checkedId) {
                                dkView.findViewById(R.id.key_entry)
                                        .setEnabled(checkedId == R.id.self);
                            }
                        });

        final AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setCancelable(false);
        ad.setView(dkView).setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int id) {
                        final String key;
                        EditText et = dkView
                                .findViewById(R.id.key_entry);
                        key = et.getText().toString();

                        if (FileSystemUtils.isEmpty(key)) {
                            changeKeyImpl(context, init, listener);
                        } else if (key.contains("'")) {
                            Toast.makeText(context,
                                    "The passphrase cannot contain a ' mark",
                                    Toast.LENGTH_LONG)
                                    .show();
                            changeKeyImpl(context, init, listener);
                        } else if(init) {
                            AtakAuthenticationDatabase
                                    .saveCredentials(
                                            AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                                            "com.atakmap.app.v2",
                                            "atakuser", key,
                                            false);
                            promptForKey(context, listener);
                        } else {
                            // please note, the actual encryption is triggered
                            // by the database classes.  This is because of the 
                            // version number being required.

                            final ProgressDialog encryptDialog = new ProgressDialog(
                                    context);
                            encryptDialog.setIcon(R.drawable.passphrase);
                            encryptDialog.setTitle("Passphrase Change");
                            encryptDialog
                                    .setMessage(
                                            "Please wait...\nApplication will exit when finished");
                            encryptDialog.setCancelable(false);
                            encryptDialog.setIndeterminate(true);
                            encryptDialog.show();

                            Runnable r = new Runnable() {
                                @Override
                                public void run() {

                                    final int status = rekey(new String[] {
                                            "Databases/ChatDb2.sqlite",
                                            "Databases/statesaver2.sqlite",
                                            "Databases/crumbs2.sqlite"
                                    }, key, false);
                                    if (status == REKEY_SUCCESS) {

                                        AtakAuthenticationDatabase
                                                .saveCredentials(
                                                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                                                        "com.atakmap.app.v2",
                                                        "atakuser", key,
                                                        false);
                                        Intent i = new Intent(
                                                "com.atakmap.app.QUITAPP");
                                        i.putExtra("FORCE_QUIT", true);
                                        AtakBroadcast.getInstance()
                                                .sendBroadcast(i);
                                        encryptDialog.dismiss();
                                    } else {
                                        ((Activity) context)
                                                .runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (status == REKEY_FAILED) {
                                                            Toast.makeText(
                                                                    context,
                                                                    "the passphrase change failed",
                                                                    Toast.LENGTH_LONG)
                                                                    .show();
                                                        } else {
                                                            Toast.makeText(
                                                                    context,
                                                                    "passphrase entered was already in use",
                                                                    Toast.LENGTH_LONG)
                                                                    .show();
                                                        }
                                                        encryptDialog.dismiss();
                                                    }
                                                });
                                    }
                                }
                            };
                            Thread t = new Thread(r, TAG + "-ChangeKey");
                            t.start();
                        }
                    }
                }).setNegativeButton(R.string.cancel, null);
        ad.show();

    }

    /**
     * Handles the encryption and the re-encryption of an existing database.
     * Migrate over to encrypted database from plaintext.
     * https://discuss.zetetic.net/t/how-to-encrypt-a-plaintext-sqlite-database-to-use-sqlcipher-and-avoid-file-is-encrypted-or-is-not-a-database-errors/868
     * @param ptFile the file name for the unencrypted database
     * @param ctFile the file name for the encrypted database
     */
    public static void encryptDb(final File ptFile, final File ctFile) {
        if (IOProviderFactory.exists(ptFile) && !IOProviderFactory.exists(ctFile)) {

            DatabaseIface ptDb = Databases.openOrCreateDatabase(
                    ptFile.getAbsolutePath());

            // not sure if I need to generate a file
            DatabaseIface ctDb = Databases.openOrCreateDatabase(
                    ctFile.getAbsolutePath());

            AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                            "com.atakmap.app.v2");

            if (credentials != null
                    && !FileSystemUtils.isEmpty(credentials.username)) {
                ctDb.execute("PRAGMA key = '" + credentials.password + "'",
                        null);
            } else {
                ctDb.execute("PRAGMA key = '" + "'", null);
            }

            ctDb.setVersion(ptDb.getVersion());
            ctDb.close();

            if (credentials != null
                    && !FileSystemUtils.isEmpty(credentials.username)) {
                ptDb.execute("ATTACH DATABASE '" + ctFile.getAbsolutePath()
                        + "' AS encrypted KEY '" + credentials.password
                        + "'", null);
            } else {
                ptDb.execute("ATTACH DATABASE '" + ctFile.getAbsolutePath()
                        + "' AS encrypted KEY '" + "'", null);
            }

            ptDb.execute("SELECT sqlcipher_export('encrypted')", null);
            ptDb.execute("DETACH DATABASE encrypted", null);
            ptDb.close();

            IOProviderFactory.delete(ptFile, IOProvider.SECURE_DELETE);

        }

    }

    private static boolean checkKeyAgainstDatabase(final File dbFile) {
        if (IOProviderFactory.exists(dbFile)) {

            final String s = "SQLite format 3";
            final byte[] b = new byte[s.length()];
            try (FileInputStream fis = IOProviderFactory
                    .getInputStream(dbFile)) {
                final int bytesRead = fis.read(b);
                if (bytesRead == s.length() && s
                        .equals(new String(b, FileSystemUtils.UTF8_CHARSET))) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }

            DatabaseIface ctDb = Databases.openOrCreateDatabase(
                    dbFile.getAbsolutePath());
            try {

                AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                                "com.atakmap.app.v2");

                if (credentials != null
                        && !FileSystemUtils.isEmpty(credentials.username)) {
                    ctDb.execute("PRAGMA key = '" + credentials.password + "'",
                            null);
                    CursorIface ci = null;
                    try {
                        ci = ctDb.query("SELECT count(*) FROM sqlite_master",
                                null);
                        long ret = -1;

                        if (ci.moveToNext()) {
                            ret = ci.getLong(0);
                            return true;
                        }
                        if (ret == -1)
                            Log.d(TAG, "database error");

                    } catch (SQLiteException e) {
                        Log.d(TAG, "corrupt database", e);
                    } finally {
                        if (ci != null)
                            ci.close();
                    }
                }
            } finally {
                try {
                    ctDb.close();
                } catch (Exception ignore) {
                }
            }
            return false;
        } else {
            return true;
        }
    }

}
