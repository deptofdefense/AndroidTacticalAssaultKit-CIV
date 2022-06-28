package com.atakmap.map.layer.model.zipcomment;

import com.atakmap.map.layer.model.ModelInfo;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public final class ZipCommentGeoreferencer {
    public static final String TAG = "ZipCommentGeoreferencer";

    public static native boolean isGeoReferenced(String uri);
    public static native void removeGeoReference(String uri);
    public static native boolean locate(ModelInfo model);
}
