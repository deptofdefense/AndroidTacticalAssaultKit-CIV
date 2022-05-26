package com.atakmap.map.formats.quantizedmesh;

public class QMESourceManager {

    public static void attachQMESourceLayer(QMESourceLayer source) {
        attachQMESourceLayerNative(source);
    }

    public static void detachQMESourceLayer(QMESourceLayer source) {
        detachQMESourceLayerNative(source);
    }

    private QMESourceManager() {}

    private static native void attachQMESourceLayerNative(QMESourceLayer source);
    private static native void detachQMESourceLayerNative(QMESourceLayer source);
}
