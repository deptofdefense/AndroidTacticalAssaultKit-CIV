
package com.atakmap.spatial.file.export;

import android.util.Pair;

import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.StyleSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for exporting KML folder, and attachments as KMZ
 * 
 * 
 */
public class KMZFolder extends Folder {

    /**
     * Map URI to KMZ path
     */
    private final List<Pair<String, String>> _files;

    public KMZFolder() {
        _files = new ArrayList<>();
        setFeatureList(new ArrayList<Feature>());
        setStyleSelector(new ArrayList<StyleSelector>());
    }

    public KMZFolder(Folder f) {
        _files = new ArrayList<>();

        setName(f.getName());
        setFeatureList(f.getFeatureList());
        setStyleSelector(f.getStyleSelector());
    }

    public boolean hasFiles() {
        return _files != null && _files.size() > 0;
    }

    public List<Pair<String, String>> getFiles() {
        return _files;
    }

    /**
     * Empty if folder contains no features
     * @return true if the feature list is empty.
     */
    public boolean isEmpty() {
        return getFeatureList() == null || getFeatureList().size() == 0;
    }
}
