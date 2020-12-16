
package com.atakmap.android.androidtest.util;

import java.util.Random;

public final class RandomUtils {
    static Random r = new Random();

    public static Random rng() {
        return r;
    }

    public static void rng(Random n) {
        r = n;
    }

    public static byte[] randomByteArray(int size) {
        return randomByteArray(size, r);
    }

    public static byte[] randomByteArray(int size, Random rng) {
        final byte[] retval = new byte[size];
        rng.nextBytes(retval);
        return retval;
    }

    public static byte[] randomByteArray(int minSize, int maxSize) {
        return randomByteArray(minSize, maxSize, r);
    }

    public static byte[] randomByteArray(int minSize, int maxSize, Random rng) {
        final int dev = rng.nextInt(maxSize - minSize + 1);
        return randomByteArray(minSize + dev, rng);
    }

    public static short[] randomShortArray(int size) {
        return randomShortArray(size, r);
    }

    public static short[] randomShortArray(int size, Random rng) {
        final short[] retval = new short[size];
        for (int i = 0; i < size; i++)
            retval[i] = (short) rng.nextInt(0x1FFFF);
        return retval;
    }

    public static short[] randomShortArray(int minSize, int maxSize) {
        return randomShortArray(minSize, maxSize, r);
    }

    public static short[] randomShortArray(int minSize, int maxSize,
            Random rng) {
        final int dev = rng.nextInt(maxSize - minSize + 1);
        return randomShortArray(minSize + dev, rng);
    }

    public static int[] randomIntArray(int size) {
        return randomIntArray(size, r);
    }

    public static int[] randomIntArray(int size, Random rng) {
        final int[] retval = new int[size];
        for (int i = 0; i < size; i++)
            retval[i] = rng.nextInt();
        return retval;
    }

    public static int[] randomIntArray(int minSize, int maxSize) {
        return randomIntArray(minSize, maxSize, r);
    }

    public static int[] randomIntArray(int minSize, int maxSize, Random rng) {
        final int dev = rng.nextInt(maxSize - minSize + 1);
        return randomIntArray(minSize + dev, rng);
    }

    public static long[] randomLongArray(int size) {
        return randomLongArray(size, r);
    }

    public static long[] randomLongArray(int size, Random rng) {
        final long[] retval = new long[size];
        for (int i = 0; i < size; i++)
            retval[i] = rng.nextLong();
        return retval;
    }

    public static long[] randomLongArray(int minSize, int maxSize) {
        return randomLongArray(minSize, maxSize, r);
    }

    public static long[] randomLongArray(int minSize, int maxSize, Random rng) {
        final int dev = rng.nextInt(maxSize - minSize + 1);
        return randomLongArray(minSize + dev, rng);
    }

    public static float[] randomFloatArray(int size) {
        return randomFloatArray(size, r);
    }

    public static float[] randomFloatArray(int size, Random rng) {
        final float[] retval = new float[size];
        for (int i = 0; i < size; i++)
            retval[i] = rng.nextFloat();
        return retval;
    }

    public static float[] randomFloatArray(int minSize, int maxSize) {
        return randomFloatArray(minSize, maxSize, r);
    }

    public static float[] randomFloatArray(int minSize, int maxSize,
            Random rng) {
        final int dev = rng.nextInt(maxSize - minSize + 1);
        return randomFloatArray(minSize + dev, rng);
    }

    public static double[] randomDoubleArray(int size) {
        return randomDoubleArray(size, r);
    }

    public static double[] randomDoubleArray(int size, Random rng) {
        final double[] retval = new double[size];
        for (int i = 0; i < size; i++)
            retval[i] = rng.nextDouble();
        return retval;
    }

    public static double[] randomDoubleArray(int minSize, int maxSize) {
        return randomDoubleArray(minSize, maxSize, r);
    }

    public static double[] randomDoubleArray(int minSize, int maxSize,
            Random rng) {
        final int dev = rng.nextInt(maxSize - minSize + 1);
        return randomDoubleArray(minSize + dev, rng);
    }
}
