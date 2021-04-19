
package com.atakmap.android.maps.tilesets.mobac;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.atakmap.android.contentservices.Service;
import com.atakmap.android.contentservices.ServiceType;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.android.maps.tilesets.mobac.QueryLayers.Style;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.mobac.MobacMapSourceFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class to represent a map service layer. Layers have a name and a title; name is the programmatic name
 * while title is the human-readable name. If a layer is not displayable, it will not have a
 * name. isDisplayable() may also be called to determine if a layer is displayable. Layers may
 * contain children, forming a recursive tree structure. Branch nodes in this tree may be
 * displayable as layers in their own right, or they may be merely logical containers. SRIDs and
 * Styles are inheritable from parent Layers. This inheritance is all handled by this class, so
 * that a call to getStyles() or getSRIDs() will return all inherited items as well as the ones
 * directly specified by the map server for this layer.
 */
public abstract class WebMapLayer {
    private final static String TAG = "WebMapLayer";

    protected final QueryLayers queryLayer;

    // human readable name for this layer
    protected final String title;

    // programmatic name; null if layer is not displayble.
    protected final String name;

    // bounds of this layer; does not contain any inherited bounds.
    // Call getBounds() if you need inherited bounds.
    protected final GeoBounds bounds;

    // this contains ONLY this layer's SRIDs, it does NOT contain
    // inherited SRIDs. Call getSRIDs() if you need inherited SRIDs.
    protected final Set<Integer> srids;

    // this contains ONLY this layer's styles, it does NOT contain
    // inherited styles. Call getStyles() if you need inherited styles.
    protected final Collection<Style> styles;

    protected WebMapLayer parent;
    protected final List<WebMapLayer> children;

    /**
     * Create a new Layer.
     */
    public WebMapLayer(String name, String title,
            QueryLayers queryLayer,
            GeoBounds bounds,
            Set<Integer> srids,
            List<WebMapLayer> children,
            Collection<Style> styles) {
        this.name = name;
        this.title = title;
        this.queryLayer = queryLayer;
        this.bounds = bounds;

        this.srids = (srids != null) ? srids : Collections.<Integer> emptySet();

        this.children = (children != null)
                ? Collections.unmodifiableList(children)
                : Collections.<WebMapLayer> emptyList();

        this.styles = (styles != null) ? styles
                : Collections
                        .<Style> emptyList();
    }

    public void setParent(WebMapLayer parent) {
        this.parent = parent;
    }

    /**
     * Return the list of child Layers.
     */
    public List<WebMapLayer> getChildren() {
        return children;
    }

    /**
     * Returns this layer's bounds. Bounds that were directly specified for this layer will be
     * returned if present; otherwise the parent layer is queried for its bounds.
     */
    public GeoBounds getBounds() {
        if (bounds != null)
            return bounds;
        else if (parent == null)
            throw new IllegalStateException("Layer bounds not provided");
        else
            return parent.getBounds();
    }

    /**
     * Return the list of styles this layer supports. Styles may be inherited from parent
     * layers; any inherited styles will be returned in this collection. An empty collection may
     * be returned if no styles were defined. (Note the WMS spec requires a style to be
     * specified in GetMap calls, so a style name of "default" should be used in the case no
     * styles are defined.)
     */
    public Collection<Style> getStyles() {
        // Handle inheritance of styles. Though the spec says child layers
        // shouldn't redefine styles of the same name, it seems to happen
        // in practice.

        HashMap<String, Style> allStyles = new HashMap<>();
        if (parent != null) {
            Collection<Style> parentStyles = parent.getStyles();
            for (Style style : parentStyles)
                allStyles.put(style.getName(), style);
        }

        for (Style style : styles)
            allStyles.put(style.getName(), style);

        return allStyles.values();
    }

    /**
     * Return the list of SRIDs this layer supports. SRIDs may be inherited from parent layers;
     * any inherited styles will be returned in this set.
     */
    public Set<Integer> getSRIDs() {
        HashSet<Integer> allSRIDs = new HashSet<>(srids);
        if (parent != null)
            allSRIDs.addAll(parent.getSRIDs());

        return allSRIDs;
    }

    /**
     * Return the human-readable name of this layer.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Return the machine-usable name for this layer. This is the name that must be provided in
     * tile queries. Returns null if this layer is not displayable.
     */
    public String getName() {
        return name;
    }

    /**
     * Indicates whether this layer is displayable in its own right. If this method returns
     * false, the layer is intended to be used as a logical container rather than a true layer.
     */
    public boolean isDisplayable() {
        return (name != null);
    }

    /**
     * Adds this map server layer to the LayersDatabase. This will write out an XML file (see
     * writeMobacXML()) to persist the layer across invocations of atak, and then add it to the
     * LayersDatabase so it may be used for the current invocation. The 'style' argument defines
     * which style is used to generate this layer.
     * 
     * @param style the style with which this layer should be displayed
     * @throws IOException in case of error
     */
    public void addToLayersDatabase(Style style, final Context context)
            throws IOException {
        File f = writeMobacXML(style);

        //check for errors
        try {
            if (MobacMapSourceFactory.create(f) == null) {
                FileSystemUtils.deleteFile(f);
                throw new IOException();
            }
        } catch (IOException ioe) {
            FileSystemUtils.deleteFile(f);
            throw ioe;
        }

        Intent loadIntent = new Intent();
        loadIntent.setAction(ImportExportMapComponent.ACTION_IMPORT_DATA);
        loadIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                LayersMapComponent.IMPORTER_CONTENT_TYPE);
        loadIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                LayersMapComponent.IMPORTER_DEFAULT_MIME_TYPE);
        loadIntent.putExtra(ImportReceiver.EXTRA_URI, Uri.fromFile(f)
                .toString());
        loadIntent.putExtra(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS, true);

        AtakBroadcast.getInstance().sendBroadcast(loadIntent);
    }

    public abstract File writeMobacXML(Style style) throws IOException;

    protected static File getDefaultXmlFile(String title, ServiceType type) {
        // generate a filename and get rid of non-standard characters
        String baseFile = title;
        baseFile = baseFile.replaceAll("\\W+", "-");

        // handle the possibility that the generated filename already exists
        File f;
        int index = 0;
        String suffix = "";
        while (true) {
            String filename = baseFile + suffix +
                    ".xml";
            String subdir = null;
            switch (type) {
                case Feature:
                    subdir = "wfs";
                    break;
                case Imagery:
                    subdir = "imagery/mobile/mapsources";
                    break;
                case Terrain:
                    subdir = "terrain";
                case SurfaceMesh:
                    subdir = "meshes";
                    break;
                default:
                    throw new IllegalArgumentException(
                            type.name() + " service not currently supported.");
            }
            f = new File(FileSystemUtils
                    .getItem(subdir),
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(filename));
            if (IOProviderFactory.exists(f)) {
                suffix = String.valueOf(index++);

                // give up after a while
                if (index >= 100)
                    throw new IllegalStateException(
                            "Could not generate filename for map server layer output");
            } else {
                if (!IOProviderFactory.exists(f.getParentFile())
                        && !IOProviderFactory.mkdirs(f.getParentFile()))
                    Log.w(TAG,
                            "Failed to create output directory for service definition");
                break;
            }
        }

        return f;
    }

    /**
     * Adds this map server layer to the LayersDatabase. This will write out an XML file (see
     * writeMobacXML()) to persist the layer across invocations of atak, and then add it to the
     * LayersDatabase so it may be used for the current invocation. The 'style' argument defines
     * which style is used to generate this layer.
     *
     * @throws IOException in case of error
     */
    public static void addToLayersDatabase(Service service,
            final Context ignored)
            throws IOException {

        File f = null;
        try {
            f = getDefaultXmlFile(service.getName(), service.getType());

        } catch (Throwable t) {
            if (f != null)
                FileSystemUtils.delete(f);
        }

        try (FileOutputStream stream = IOProviderFactory.getOutputStream(f)) {
            service.generateConfigFile(stream);
        }

        Intent loadIntent = new Intent();
        loadIntent.setAction(ImportExportMapComponent.ACTION_IMPORT_DATA);
        loadIntent.putExtra(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS, true);
        loadIntent.putExtra(ImportReceiver.EXTRA_URI, Uri.fromFile(f)
                .toString());

        // specialize for imagery import
        if (service.getType() == ServiceType.Imagery) {
            //check for errors   
            try {
                if (!DatasetDescriptorFactory2.isSupported(f)) {
                    FileSystemUtils.deleteFile(f);
                    throw new IOException();
                }
            } catch (IOException ioe) {
                FileSystemUtils.deleteFile(f);
                throw ioe;
            }

            loadIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    LayersMapComponent.IMPORTER_CONTENT_TYPE);
            loadIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    LayersMapComponent.IMPORTER_DEFAULT_MIME_TYPE);
        }

        AtakBroadcast.getInstance().sendBroadcast(loadIntent);
    }
}
