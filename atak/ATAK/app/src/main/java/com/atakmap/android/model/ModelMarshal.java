
package com.atakmap.android.model;

import android.net.Uri;

import com.atakmap.android.importexport.AbstractMarshal;
import com.atakmap.android.importexport.Marshal;
import com.atakmap.map.layer.model.ModelInfoFactory;

import java.io.IOException;
import java.io.InputStream;

final class ModelMarshal extends AbstractMarshal {
    public final static Marshal INSTANCE = new ModelMarshal();

    ModelMarshal() {
        super(ModelImporter.CONTENT_TYPE);
    }

    @Override
    public String marshal(InputStream inputStream, int probeSize)
            throws IOException {
        return null;
    }

    @Override
    public String marshal(Uri uri) throws IOException {
        return ModelInfoFactory.isSupported(uri)
                ? "application/octet-stream"
                : null;
    }

    @Override
    public int getPriorityLevel() {
        return 1;
    }
}
