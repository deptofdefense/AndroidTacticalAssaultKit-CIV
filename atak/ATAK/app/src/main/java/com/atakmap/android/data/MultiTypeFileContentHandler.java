
package com.atakmap.android.data;

import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.maps.ILocation;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A handler for a file that has multiple content types
 */
public abstract class MultiTypeFileContentHandler extends FileContentHandler {

    protected MultiTypeFileContentHandler(File file) {
        super(file);
    }

    @Override
    public void deleteContent() {
        for (FileContentHandler h : getHandlers())
            h.deleteContent();
    }

    protected List<FileContentHandler> getHandlers() {
        List<URIContentHandler> handlers = URIContentManager.getInstance()
                .getHandlers(_file);
        List<FileContentHandler> ret = new ArrayList<>(handlers.size());
        for (int i = 0; i < handlers.size(); i++) {
            URIContentHandler h = handlers.get(i);
            if (h != this && h instanceof FileContentHandler)
                ret.add((FileContentHandler) h);
        }
        return ret;
    }

    @Override
    protected boolean setVisibleImpl(boolean visible) {
        boolean ret = false;
        for (FileContentHandler h : getHandlers())
            ret |= h.setVisibleImpl(visible);
        return ret;
    }

    public boolean isVisible() {
        return getVisibility() == Visibility2.VISIBLE;
    }

    public int getVisibility() {
        int vis = 0;
        int total = 0;
        for (FileContentHandler h : getHandlers()) {
            if (h instanceof Visibility2)
                vis += (((Visibility2) h)
                        .getVisibility() == Visibility2.VISIBLE) ? 1 : 0;
            else if (h instanceof Visibility)
                vis += h.isVisible() ? 1 : 0;
            else
                continue;
            total++;
        }
        return (vis == total ? Visibility2.VISIBLE
                : (vis == 0 ? Visibility2.INVISIBLE
                        : Visibility2.SEMI_VISIBLE));
    }

    public GeoPoint getPoint(GeoPoint point) {
        GeoBounds bounds = getBounds(null);
        return bounds != null ? bounds.getCenter(point) : null;
    }

    public GeoBounds getBounds(MutableGeoBounds bounds) {
        GeoBounds.Builder gbb = new GeoBounds.Builder();
        MutableGeoBounds scratch = new MutableGeoBounds();
        for (FileContentHandler item : getHandlers()) {
            if (item instanceof ILocation) {
                ((ILocation) item).getBounds(scratch);
                gbb.add(scratch);
            }
        }
        if (bounds != null)
            bounds.set(gbb.build());
        else
            return gbb.build();
        return bounds;
    }
}
