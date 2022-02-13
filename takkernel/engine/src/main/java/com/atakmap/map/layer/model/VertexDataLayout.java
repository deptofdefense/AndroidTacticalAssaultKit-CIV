package com.atakmap.map.layer.model;

import com.atakmap.interop.DataType;
import com.atakmap.math.MathUtils;

public final class VertexDataLayout {
    public final static class Array {
        public int offset;
        public int stride;
        public Class<?> dataType;

        public Array() {
            offset = 0;
            stride = 0;
            dataType = null;
        }

        public Array(final Class<?> dataType, final int offset, final int stride) {
            this.dataType = dataType;
            this.offset = offset;
            this.stride = stride;
        }
    }

    public int attributes = 0;
    public final Array position = new Array(Float.TYPE, 0, 12);
    public final Array texCoord0 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord1 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord2 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord3 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord4 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord5 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord6 = new Array(Float.TYPE, 0, 8);
    public final Array texCoord7 = new Array(Float.TYPE, 0, 8);
    public final Array normal = new Array(Float.TYPE, 0, 12);
    public final Array color = new Array(Byte.TYPE, 0, 4);
    public boolean interleaved = false;

    public Array getTexCoordArray(final int texCoordIndex) {
        switch (texCoordIndex) {
            case 0: return texCoord0;
            case 1: return texCoord1;
            case 2: return texCoord2;
            case 3: return texCoord3;
            case 4: return texCoord4;
            case 5: return texCoord5;
            case 6: return texCoord6;
            case 7: return texCoord7;
            default: 
                  throw new IllegalArgumentException("texCoordIndex out of bounds");

        }
    }

    public static VertexDataLayout createDefaultInterleaved(final int attrs) {
        VertexDataLayout retval = new VertexDataLayout();
        int off = 0;
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_POSITION)) {
            retval.position.dataType = Float.TYPE;
            retval.position.offset = off;
            off += 12;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_0)) {
            retval.texCoord0.dataType = Float.TYPE;
            retval.texCoord0.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_1)) {
            retval.texCoord1.dataType = Float.TYPE;
            retval.texCoord1.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_2)) {
            retval.texCoord2.dataType = Float.TYPE;
            retval.texCoord2.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_3)) {
            retval.texCoord3.dataType = Float.TYPE;
            retval.texCoord3.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_4)) {
            retval.texCoord4.dataType = Float.TYPE;
            retval.texCoord4.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_5)) {
            retval.texCoord5.dataType = Float.TYPE;
            retval.texCoord5.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_6)) {
            retval.texCoord6.dataType = Float.TYPE;
            retval.texCoord6.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_TEXCOORD_7)) {
            retval.texCoord7.dataType = Float.TYPE;
            retval.texCoord7.offset = off;
            off += 8;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_NORMAL)) {
            retval.normal.dataType = Float.TYPE;
            retval.normal.offset = off;
            off += 12;
        }
        if (MathUtils.hasBits(attrs, Model.VERTEX_ATTR_COLOR)) {
            retval.color.dataType = Byte.TYPE;
            retval.color.offset = off;
            off += 4;
        }

        retval.attributes = attrs;
        retval.position.stride = off;
        retval.texCoord0.stride = off;
        retval.texCoord1.stride = off;
        retval.texCoord2.stride = off;
        retval.texCoord3.stride = off;
        retval.texCoord4.stride = off;
        retval.texCoord5.stride = off;
        retval.texCoord6.stride = off;
        retval.texCoord7.stride = off;
        retval.normal.offset = off;
        retval.color.offset = off;
        retval.interleaved = true;
        return retval;
    }

    public static int requiredInterleavedDataSize(VertexDataLayout layout,
                                                  int numVertices) {
        final int cattrs = getNativeAttributes(layout.attributes);
        return requiredInterleavedDataSize(
                cattrs,
                layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                layout.texCoord0.dataType != null ? DataType.convert(layout.texCoord0.dataType, false) : DataType.TEDT_Float32, layout.texCoord0.offset, layout.texCoord0.stride,
                layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                layout.interleaved,
                numVertices);
    }

    static void fromNative(long clayout, VertexDataLayout mlayout) {
        final int teva = VertexDataLayout.getAttributes(clayout);
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_Position())) {
            mlayout.attributes |= Model.VERTEX_ATTR_POSITION;
            mlayout.position.dataType = DataType.convert(VertexDataLayout.getPositionDataType(clayout));
            mlayout.position.offset = VertexDataLayout.getPositionOffset(clayout);
            mlayout.position.stride = VertexDataLayout.getPositionStride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord0())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_0;
            mlayout.texCoord0.dataType = DataType.convert(VertexDataLayout.getTexCoord0DataType(clayout));
            mlayout.texCoord0.offset = VertexDataLayout.getTexCoord0Offset(clayout);
            mlayout.texCoord0.stride = VertexDataLayout.getTexCoord0Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord1())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_1;
            mlayout.texCoord1.dataType = DataType.convert(VertexDataLayout.getTexCoord1DataType(clayout));
            mlayout.texCoord1.offset = VertexDataLayout.getTexCoord1Offset(clayout);
            mlayout.texCoord1.stride = VertexDataLayout.getTexCoord1Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord2())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_2;
            mlayout.texCoord2.dataType = DataType.convert(VertexDataLayout.getTexCoord2DataType(clayout));
            mlayout.texCoord2.offset = VertexDataLayout.getTexCoord2Offset(clayout);
            mlayout.texCoord2.stride = VertexDataLayout.getTexCoord2Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord3())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_3;
            mlayout.texCoord3.dataType = DataType.convert(VertexDataLayout.getTexCoord3DataType(clayout));
            mlayout.texCoord3.offset = VertexDataLayout.getTexCoord3Offset(clayout);
            mlayout.texCoord3.stride = VertexDataLayout.getTexCoord3Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord4())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_4;
            mlayout.texCoord4.dataType = DataType.convert(VertexDataLayout.getTexCoord4DataType(clayout));
            mlayout.texCoord4.offset = VertexDataLayout.getTexCoord4Offset(clayout);
            mlayout.texCoord4.stride = VertexDataLayout.getTexCoord4Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord5())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_5;
            mlayout.texCoord5.dataType = DataType.convert(VertexDataLayout.getTexCoord5DataType(clayout));
            mlayout.texCoord5.offset = VertexDataLayout.getTexCoord5Offset(clayout);
            mlayout.texCoord5.stride = VertexDataLayout.getTexCoord5Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord6())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_6;
            mlayout.texCoord6.dataType = DataType.convert(VertexDataLayout.getTexCoord6DataType(clayout));
            mlayout.texCoord6.offset = VertexDataLayout.getTexCoord6Offset(clayout);
            mlayout.texCoord6.stride = VertexDataLayout.getTexCoord6Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_TexCoord7())) {
            mlayout.attributes |= Model.VERTEX_ATTR_TEXCOORD_7;
            mlayout.texCoord7.dataType = DataType.convert(VertexDataLayout.getTexCoord7DataType(clayout));
            mlayout.texCoord7.offset = VertexDataLayout.getTexCoord7Offset(clayout);
            mlayout.texCoord7.stride = VertexDataLayout.getTexCoord7Stride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_Normal())) {
            mlayout.attributes |= Model.VERTEX_ATTR_NORMAL;
            mlayout.normal.dataType = DataType.convert(VertexDataLayout.getNormalDataType(clayout));
            mlayout.normal.offset = VertexDataLayout.getNormalOffset(clayout);
            mlayout.normal.stride = VertexDataLayout.getNormalStride(clayout);
        }
        if(MathUtils.hasBits(teva, NativeMesh.getTEVA_Color())) {
            mlayout.attributes |= Model.VERTEX_ATTR_COLOR;
            mlayout.color.dataType = DataType.convert(VertexDataLayout.getColorDataType(clayout));
            mlayout.color.offset = VertexDataLayout.getColorOffset(clayout);
            mlayout.color.stride = VertexDataLayout.getColorStride(clayout);
        }
        mlayout.interleaved = VertexDataLayout.getInterleaved(clayout);
    }

    static native int getAttributes(long pointer);
    static native int getPositionDataType(long pointer);
    static native int getPositionOffset(long pointer);
    static native int getPositionStride(long pointer);
    static native int getTexCoord0DataType(long pointer);
    static native int getTexCoord0Offset(long pointer);
    static native int getTexCoord0Stride(long pointer);
    static native int getTexCoord1DataType(long pointer);
    static native int getTexCoord1Offset(long pointer);
    static native int getTexCoord1Stride(long pointer);
    static native int getTexCoord2DataType(long pointer);
    static native int getTexCoord2Offset(long pointer);
    static native int getTexCoord2Stride(long pointer);
    static native int getTexCoord3DataType(long pointer);
    static native int getTexCoord3Offset(long pointer);
    static native int getTexCoord3Stride(long pointer);
    static native int getTexCoord4DataType(long pointer);
    static native int getTexCoord4Offset(long pointer);
    static native int getTexCoord4Stride(long pointer);
    static native int getTexCoord5DataType(long pointer);
    static native int getTexCoord5Offset(long pointer);
    static native int getTexCoord5Stride(long pointer);
    static native int getTexCoord6DataType(long pointer);
    static native int getTexCoord6Offset(long pointer);
    static native int getTexCoord6Stride(long pointer);
    static native int getTexCoord7DataType(long pointer);
    static native int getTexCoord7Offset(long pointer);
    static native int getTexCoord7Stride(long pointer);
    static native int getNormalDataType(long pointer);
    static native int getNormalOffset(long pointer);
    static native int getNormalStride(long pointer);
    static native int getColorDataType(long pointer);
    static native int getColorOffset(long pointer);
    static native int getColorStride(long pointer);
    static native boolean getInterleaved(long pointer);

    static native int requiredInterleavedDataSize(int attrs,
                                                  int posType, int posOff, int posStride,
                                                  int texCoordType, int texCoordOff, int texCoordStride,
                                                  int normalType, int normalOff, int normalStride,
                                                  int colorType, int colorOff, int colorStride,
                                                  boolean interleaved,
                                                  int numVertices);

    static int getManagedAttributes(final int cattr) {
        int mattr = 0;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_Position()))
            mattr |= Model.VERTEX_ATTR_POSITION;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord0()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_0;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord1()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_1;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord2()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_2;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord3()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_3;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord4()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_4;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord5()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_5;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord6()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_6;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_TexCoord7()))
            mattr |= Model.VERTEX_ATTR_TEXCOORD_7;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_Normal()))
            mattr |= Model.VERTEX_ATTR_NORMAL;
        if(MathUtils.hasBits(cattr, NativeMesh.getTEVA_Color()))
            mattr |= Model.VERTEX_ATTR_COLOR;
        return mattr;
    }
    static int getNativeAttributes(final int mattr) {
        return NativeMesh.getNativeAttributes(mattr);

    }

    public static int texCoordAttribute(final int i) {
        if (i < 0 && i < 7) {
            throw new IllegalArgumentException("texCoordAtttribute out of bounds");
        }
        switch (i) {
            case 0: return Model.VERTEX_ATTR_TEXCOORD_0;
            default: return Model.VERTEX_ATTR_TEXCOORD_1 << i;
        }
    }
}
