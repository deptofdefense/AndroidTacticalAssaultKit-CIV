
package com.atakmap.android.importfiles.resource;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * List of RemoteResource objects Supports reading/wrting to/from XML
 * 
 * 
 */
@Root
public class RemoteResources {

    private static final String TAG = "RemoteResources";

    @ElementList(entry = "RemoteResource", inline = true, required = false)
    private List<RemoteResource> _resources;

    @Element(name = "version")
    private static final int VERSION = 1;

    public RemoteResources() {
        _resources = new ArrayList<>();
    }

    public List<RemoteResource> getResources() {
        if (_resources == null)
            _resources = new ArrayList<>();

        return _resources;
    }

    public void add(RemoteResource r) {
        if (_resources == null)
            _resources = new ArrayList<>();

        _resources.add(r);
    }

    public void setResources(List<RemoteResource> resources) {
        _resources = resources;
    }

    /**
     * Restores a remove resource from a serialized file.
     * @param file The source of the serialized data
     * @param serializer The serializer to use.
     * @return
     */
    public static RemoteResources load(File file, Serializer serializer) {
        RemoteResources resources = null;
        try (InputStream fis = IOProviderFactory.getInputStream(file)) {
            resources = serializer.read(RemoteResources.class, fis);
            Log.d(TAG,
                    "Loaded " + resources.getResources().size()
                            + " resources from: "
                            + file.getAbsolutePath());
            // Hook child up to parent
            for (RemoteResource r : resources.getResources()) {
                if (r.hasChildren()) {
                    for (RemoteResource child : r.getChildren())
                        child.setParent(r);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load resources: " + file.getAbsolutePath(),
                    e);
        }

        return resources;
    }

    /**
     * Allows for serialization of a remote resource.
     * @param file The destination file used for the serialized data
     * @param serializer The serializer to be used when producing the file.
     * @return boolean true is serialization was successful.
     */
    public boolean save(File file, Serializer serializer) {
        if (!IOProviderFactory.exists(file)
                && !IOProviderFactory.exists(file.getParentFile())
                && !IOProviderFactory.mkdirs(file.getParentFile())) {
            Log.w(TAG, "Failed to create " + file.getAbsolutePath());
            return false;
        }
        try (FileOutputStream fos = IOProviderFactory.getOutputStream(file)) {
            serializer.write(this, fos);
            Log.d(TAG, "save " + getResources().size() + " resources to: "
                    + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to save resources: " + file.getAbsolutePath(),
                    e);
        }

        return false;
    }
}
