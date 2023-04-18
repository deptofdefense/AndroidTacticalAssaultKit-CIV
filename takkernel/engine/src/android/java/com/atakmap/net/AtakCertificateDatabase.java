package com.atakmap.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.filesystem.HashingUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


/**
 *
 */
public class AtakCertificateDatabase extends AtakCertificateDatabaseBase {

    /**
     * Migrates legacy certificates to sqlcipher
     *
     * @param context the context to use for getting the resources
     */
    public static void migrateCertificatesToSqlcipher(Context context) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int id = context.getResources().getIdentifier("defaultTrustStorePassword", "string", context.getPackageName());
        String defaultPassword = context.getString(id);

        if (importCertificate(
                prefs.getString("caLocation", "(built-in)"),
                null,
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    prefs.getString("caPassword", defaultPassword),
                    AtakAuthenticationCredentials.TYPE_caPassword,
                    null);

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("caPassword").apply();
        }

        if (importCertificate(
                prefs.getString("certificateLocation", "(built-in)"),
                null,
                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                true) != null) {

            AtakCertificateDatabase.saveCertificatePassword(
                    prefs.getString("clientPassword", defaultPassword),
                    AtakAuthenticationCredentials.TYPE_clientPassword,
                    null);

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("clientPassword").apply();
        }
    }
}
