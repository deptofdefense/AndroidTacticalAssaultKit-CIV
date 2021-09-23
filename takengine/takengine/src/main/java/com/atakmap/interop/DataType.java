package com.atakmap.interop;

public final class DataType {
    public final static int TEDT_UInt8 = getTEDT_UInt8();
    public final static int TEDT_Int8 = getTEDT_Int8();
    public final static int TEDT_UInt16 = getTEDT_UInt16();
    public final static int TEDT_Int16 = getTEDT_Int16();
    public final static int TEDT_UInt32 = getTEDT_UInt32();
    public final static int TEDT_Int32 = getTEDT_Int32();
    public final static int TEDT_Float32 = getTEDT_Float32();
    public final static int TEDT_Float64 = getTEDT_Float64();

    private DataType() {}

    public static int convert(Class<?> type, boolean signed) {
        if(type == null)
            throw new NullPointerException();
        if(type == Integer.TYPE)
            return signed ? TEDT_Int32 : TEDT_UInt32;
        else if(type == Short.TYPE)
            return signed ? TEDT_Int16 : TEDT_UInt16;
        else if(type == Byte.TYPE)
            return signed ? TEDT_Int8 : TEDT_UInt8;
        else if(type == Float.TYPE)
            return TEDT_Float32;
        else if(type == Double.TYPE)
            return TEDT_Float64;
        else
            throw new IllegalArgumentException();
    }

    public static Class<?> convert(int type) {
        if(type == TEDT_Int8 || type == TEDT_UInt8)
            return Byte.TYPE;
        else if(type == TEDT_Int16 || type == TEDT_UInt16)
            return Short.TYPE;
        else if(type == TEDT_Int32 || type == TEDT_UInt32)
            return Integer.TYPE;
        else if(type == TEDT_Float32)
            return Float.TYPE;
        else if(type == TEDT_Float64)
            return Double.TYPE;
        else
            throw new IllegalArgumentException();
    }

    public static boolean isSigned(int type) {
        return (type == TEDT_Int8 ||
                type == TEDT_Int16 ||
                type == TEDT_Int32 ||
                type == TEDT_Float32 ||
                type == TEDT_Float64);
    }

    private static native int getTEDT_UInt8();
    private static native int getTEDT_Int8();
    private static native int getTEDT_UInt16();
    private static native int getTEDT_Int16();
    private static native int getTEDT_UInt32();
    private static native int getTEDT_Int32();
    private static native int getTEDT_Float32();
    private static native int getTEDT_Float64();
}
