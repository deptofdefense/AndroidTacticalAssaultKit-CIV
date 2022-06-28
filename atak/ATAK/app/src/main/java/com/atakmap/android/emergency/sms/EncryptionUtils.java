
package com.atakmap.android.emergency.sms;

import com.atakmap.coremap.log.Log;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A utility class that performs encryption and decryption.
 * Its methods assume that the symmetric key has already been agreed upon. When I say "symmetric key"
 * in this context, I am referring to some plaintext value, like "1234", that will be converted into
 * a proper AES key via the encodeKey() method.
 * Encryption algorithm: AES
 * Block cipher mode: CBC (Cipher block chaining)
 * Padding mode: PKCS5
 * Initial vector (IV): produced by a SHA-1 pseudo-random number generator.
 *  I've read that this essentially uses the SHA-1 hash, a seed, and a counter. It's considered
 *  secure and fast, but may not be as secure as using the dev/urandom.
 *  One advantage of using an initial vector is that the same message will be encrypted differently each time,
 *  assuming the initial vectors are unique.
 */
// Source: http://stackoverflow.com/questions/4551263/how-can-i-convert-a-string-to-a-secretkey/8828196#8828196
class EncryptionUtils {
    private static final String TAG = "EncryptionUtils";
    private static final String cipherOptions = "AES/GCM/PKCS5Padding";
    private static final String symKeyAlgorithm = "AES";
    private static final String prng = "SHA1PRNG";

    /**
     * Given a symetric key, encode a message
     * @param symKeyData the symetric key
     * @param encodedMessage the message to encode
     * @return the byte array representing the encoded message
     */
    public static byte[] encrypt(byte[] symKeyData, byte[] encodedMessage)
            throws Exception {
        final Cipher cipher = Cipher.getInstance(cipherOptions);
        final int blockSize = cipher.getBlockSize();

        SecretKeySpec symKey = new SecretKeySpec(symKeyData, symKeyAlgorithm);

        // generate random IV
        final byte[] ivData = setInitialVector(blockSize);
        final IvParameterSpec iv = new IvParameterSpec(ivData);

        cipher.init(Cipher.ENCRYPT_MODE, symKey, iv);

        final byte[] encryptedMessage = cipher.doFinal(encodedMessage);

        // concatenate IV and encrypted message
        final byte[] ivAndEncryptedMessage = new byte[blockSize
                + encryptedMessage.length];
        System.arraycopy(ivData, 0, ivAndEncryptedMessage, 0, blockSize);
        System.arraycopy(encryptedMessage, 0, ivAndEncryptedMessage,
                blockSize, encryptedMessage.length);

        return ivAndEncryptedMessage;
    }

    /**
     * Given an encrypted message and a symetric key, decrypt the message
     * @param symKeyData the symetric key
     * @param ivAndEncryptedMessage the encrypted message
     * @return the descrpted message as a byte array
     */
    public static byte[] decrypt(byte[] symKeyData,
            byte[] ivAndEncryptedMessage) {
        try {
            final Cipher cipher = Cipher.getInstance(cipherOptions);
            final int blockSize = cipher.getBlockSize();

            // create the key
            /*final SecretKeySpec symKey = (usedKeyFile) ? new SecretKeySpec(symKeyData, symKeyAlgorithm):
                new SecretKeySpec(encodeKey(symKeyData), symKeyAlgorithm);*/
            SecretKeySpec symKey = new SecretKeySpec(symKeyData,
                    symKeyAlgorithm);

            // retrieve random IV from start of the received message
            final byte[] ivData = new byte[blockSize];
            System.arraycopy(ivAndEncryptedMessage, 0, ivData, 0, blockSize);
            final IvParameterSpec iv = new IvParameterSpec(ivData);

            // retrieve the encrypted message itself
            final byte[] encryptedMessage = new byte[ivAndEncryptedMessage.length
                    - blockSize];
            System.arraycopy(ivAndEncryptedMessage, blockSize,
                    encryptedMessage, 0, encryptedMessage.length);

            cipher.init(Cipher.DECRYPT_MODE, symKey, iv);

            return cipher.doFinal(encryptedMessage);
        } catch (Exception e) {
            Log.e(TAG, "Exception during decryption: ", e);
            return null;
        }
    }

    private static byte[] setInitialVector(int blockSize)
            throws NoSuchAlgorithmException {
        final byte[] ivData = new byte[blockSize];
        final SecureRandom rnd = SecureRandom.getInstance(prng);
        rnd.nextBytes(ivData);
        return ivData;
    }
}
