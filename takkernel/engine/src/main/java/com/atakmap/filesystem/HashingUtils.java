
package com.atakmap.filesystem;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.util.zip.IoUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Support for MD5 and SHA2-256 hashing
 */
public class HashingUtils {

    private static final String TAG = "HashingUtils";

    public final static String ALGORITHM_MD5 = "MD5";
    public final static String ALGORITHM_SHA256 = "SHA-256";
    public final static String ALGORITHM_SHA1 = "SHA-1";
    /**
     * This constructs an md5sum from the contents of the file provided.  Due to a bug in the
     * original implementation, the leading zero would be dropped from the computed md5sum.
     */
    public static String md5sum(File file) {
        if (file == null || !IOProviderFactory.exists(file))
            return null;

        if (file instanceof ZipVirtualFile) {
            Log.v(TAG,
                    "Computing MD5 for Zip Virtual: " + file.getAbsolutePath());
            try {
                return md5sum(((ZipVirtualFile) file).openStream());
            } catch (IOException e) {
                Log.e(TAG, "Error computing Zip Virtual md5sum", e);
            }
        } else {
            Log.v(TAG, "Computing MD5 for: " + file.getAbsolutePath());
            try {
                return md5sum(IOProviderFactory.getInputStream(file));
            } catch (IOException e) {
                Log.e(TAG, "Error computing md5sum", e);
            }
        }

        return null;
    }

    /**
     * This constructs an md5sum from the contents of the string provided.  Due to a bug in the
     *      * original implementation, the leading zero would be dropped from the computed md5sum.
     */
    public static String md5sum(String content) {
        if (content == null || content.length() < 1)
            return null;

        return md5sum(new ByteArrayInputStream(
                content.getBytes(FileSystemUtils.UTF8_CHARSET)));
    }

    /*
     * Calculate checksum of a File using MD5 algorithm.   Due to a bug in the
     * original implementation, the leading zero would be dropped from the computed md5sum.
     */
    public static String md5sum(InputStream input) {
        String checksum = null;

        if (input == null)
            return checksum;

        try {
            // this is only used to compute a MD5 representation of an inputstream
            // and not for cryptographic use cases.
            MessageDigest md = MessageDigest.getInstance(ALGORITHM_MD5);
            checksum = computeSumFromInputStream(md, input);
            //mimic the previous implementation of the md5sum which used BigInteger
            if (checksum.startsWith("0"))
                checksum = checksum.substring(1);
        } catch (IOException | NoSuchAlgorithmException ex) {
            Log.e(TAG, "Error computing md5sum", ex);
        } finally {
            IoUtils.close(input, TAG, "Error computing md5sum");
        }

        return checksum;
    }

    /**
     * Given a file, compute a sha256 corresponding to it.
     * @param file the file
     * @return the sha256 for the file
     */
    public static String sha256sum(File file) {
        if (file == null || !IOProviderFactory.exists(file))
            return null;

        Log.v(TAG, "Computing SHA256 for: " + file.getAbsolutePath());
        try {
            return sha256sum(IOProviderFactory.getInputStream(file));
        } catch (IOException e) {
            Log.e(TAG, "Error computing sha256sum", e);
        }

        return null;
    }

    /**
     * Given a string, compute a sha256 corresponding to it.
     * @param content the string
     * @return the sha256 for the string
     */
    public static String sha256sum(String content) {
        if (content == null || content.length() < 1)
            return null;

        return sha256sum(new ByteArrayInputStream(
                content.getBytes(FileSystemUtils.UTF8_CHARSET)));

    }

    /**
     * Given a byte array, compute a sha256 corresponding to it.
     * @param content the byte array
     * @return the sha256 for the byte array
     */
    public static String sha256sum(byte[] content) {
        if (content == null || content.length < 1)
            return null;

        return sha256sum(new ByteArrayInputStream(content));
    }

    /*
     * Calculate checksum of a stream using SHA256 algorithm
     */
    public static String sha256sum(InputStream input) {
        String checksum = null;
        if (input == null)
            return checksum;

        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA256);
            checksum = computeSumFromInputStream(md, input);
        } catch (IOException | NoSuchAlgorithmException ex) {
            Log.e(TAG, "Error computing sha256sum", ex);
        } finally {
            IoUtils.close(input, TAG, "Error computing sha256sum");
        }

        return checksum;
    }

    /**
     * Given a file, produce a sha1
     * @param file the file
     * @return the corresponding sha1
     */
    public static String sha1sum(final File file) {
        if (file == null || !IOProviderFactory.exists(file))
            return null;

        Log.v(TAG, "Computing SHA1 for: " + file.getAbsolutePath());
        try {
            return sha1sum(IOProviderFactory.getInputStream(file));
        } catch (IOException e) {
            Log.e(TAG, "Error computing sha1sum", e);
        }

        return null;
    }

    /**
     * Given a string, compute the sha1 for the string
     * @param content the string
     * @return the corresponding sha1 for the string.
     */
    public static String sha1sum(final String content) {
        if (content == null || content.length() < 1)
            return null;

        return sha1sum(new ByteArrayInputStream(
                content.getBytes(FileSystemUtils.UTF8_CHARSET)));

    }

    /**
     * Generate a sha1 sum based on a provided byte array
     * @param content the byte array
     * @return the sha1 sum
     */
    public static String sha1sum(byte[] content) {
        if (content == null || content.length < 1)
            return null;

        return sha1sum(new ByteArrayInputStream(content));
    }

    /*
     * Calculate checksum of a stream using SHA1 algorithm
     * @param input the input stream
     * @return the sha1 sum
     */
    public static String sha1sum(InputStream input) {
        String checksum = null;
        if (input == null)
            return checksum;

        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA1);
            checksum = computeSumFromInputStream(md, input);

        } catch (IOException | NoSuchAlgorithmException ex) {
            Log.e(TAG, "Error computing sha1sum", ex);
        } finally {
            IoUtils.close(input, TAG, "Error computing sha1sum");
        }

        return checksum;
    }


    public static String toHexString(byte[] arr) {
        // convert to hex
        StringBuilder hexString = new StringBuilder();
        for (byte b : arr) {
            hexString.append(String.format("%02x", 0xFF & b));
        }

        return hexString.toString();
    }

    public static Map<String, byte[]> computeHashes(Set<String> algorithms,
            InputStream input)
            throws IOException, NoSuchAlgorithmException {
        return computeHashes(algorithms, input, true);
    }

    private static Map<String, byte[]> computeHashes(Set<String> algorithms,
            InputStream input,
            boolean failFast) throws IOException, NoSuchAlgorithmException {
        MessageDigest[] digests = new MessageDigest[algorithms.size()];
        int numDigests = 0;
        for (String algorithm : algorithms) {
            try {
                digests[numDigests++] = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException ex) {
                if (failFast)
                    throw ex;
                else
                    Log.e(TAG, "No such algorithm: " + algorithm);
            }
        }

        Map<String, byte[]> retval = new HashMap<String, byte[]>();
        try {
            // Using MessageDigest update() method to provide input
            final byte[] buffer = new byte[8192];
            int numOfBytesRead;
            while ((numOfBytesRead = input.read(buffer)) > 0) {
                for (int i = 0; i < numDigests; i++)
                    digests[i].update(buffer, 0, numOfBytesRead);
            }
            for (int i = 0; i < numDigests; i++) {
                retval.put(digests[i].getAlgorithm(), digests[i].digest());
                digests[i].reset();
            }
        } catch (IOException ex) {
            if (failFast)
                throw ex;
            else
                Log.e(TAG, "I/O error computing hash", ex);
        }

        for (String algorithm : algorithms) {
            if (!retval.containsKey(algorithm))
                retval.put(algorithm, null);
        }

        return retval;
    }

    public static Map<String, String> computeHashHexStrings(
            Set<String> algorithms, File file) {
        InputStream input = null;
        try {
            input = new BufferedInputStream(IOProviderFactory.getInputStream(file));
        } catch (IOException ignored) {
        }

        return computeHashHexStrings(algorithms, input);
    }

    public static Map<String, String> computeHashHexStrings(
            Set<String> algorithms,
            InputStream inputStream) {
        Map<String, byte[]> hashes;
        if (inputStream != null) {
            try {
                hashes = computeHashes(algorithms, inputStream, false);
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            } finally {
                IoUtils.close(inputStream);
            }
        } else {
            hashes = Collections.emptyMap();
        }

        Map<String, String> retval = new HashMap<>();
        byte[] hash;
        for (String algorithm : algorithms) {
            hash = hashes.get(algorithm);
            if (hash == null)
                retval.put(algorithm, null);
            else
                retval.put(algorithm, toHexString(hash));
        }

        return retval;
    }

    /**
     * Verify that file has the specified size and hash
     *
     * @param file the file
     * @param sizeToMatch the size of the file
     * @param sha256ToMatch the sha256 to verify against
     * @return true if the file matches the sha256
     */
    public static boolean verify(File file, long sizeToMatch, String sha256ToMatch) {
        if (!FileSystemUtils.isFile(file))
        {
            Log.w(TAG, "Cannot verify empty file");
            return false;
        }

        // check file size
        if (IOProviderFactory.length(file) != sizeToMatch)
        {
            Log.w(TAG, String.format(LocaleUtil.getCurrent(), "Size mismatch: %d vs %d", IOProviderFactory.length(file),
                    sizeToMatch));
            return false;
        }

        // attempt to verify SHA256
        if (FileSystemUtils.isEmpty(sha256ToMatch)) {
            Log.w(TAG, "Unable to verify SHA256");
            return false;
        }

        Log.d(TAG, "Verifying SHA256...");
        String compareSHA256 = HashingUtils.sha256sum(file);
        if (FileSystemUtils.isEmpty(compareSHA256)) {
            Log.w(TAG, "Cannot compare empty SHA256");
            return false;
        } else if (compareSHA256.equalsIgnoreCase(sha256ToMatch)) {
            return true;
        } else {
            Log.w(TAG, String.format("SHA256 mismatch: %s vs %s", sha256ToMatch,
                    compareSHA256));
            return false;
        }
    }

    private static String computeSumFromInputStream(final MessageDigest md, final InputStream is) throws IOException {
        // Using MessageDigest update() method to provide input
        final byte[] buffer = new byte[8192];
        int numOfBytesRead;
        while ((numOfBytesRead = is.read(buffer)) > 0) {
            md.update(buffer, 0, numOfBytesRead);
        }
        byte[] hash = md.digest();
        md.reset();

        // convert to hex
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", 0xFF & b));
        }

        return hexString.toString();
    }

}
