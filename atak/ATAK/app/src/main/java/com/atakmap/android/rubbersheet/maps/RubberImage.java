
package com.atakmap.android.rubbersheet.maps;

import android.graphics.BitmapFactory;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.rubbersheet.data.BitmapPyramid;
import com.atakmap.android.rubbersheet.data.RubberImageData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.AtakMapView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * The "rubber sheet" image with rectangle controls
 */
public class RubberImage extends AbstractSheet {

    private static final String TAG = "RubberImage";

    private BitmapPyramid _image;
    private final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);
    private final Vector2D[] _quad = new Vector2D[4];

    private RubberImage(RubberImageData data) {
        super(data);
        setImage(data.file);
        setMenu("menus/rubber_image_menu.xml");
        refreshBounds();
    }

    @Override
    public void onRemoved(MapGroup parent) {
        super.onRemoved(parent);
        if (_image != null)
            _image.dispose();
        _image = null;
    }

    @Override
    protected LoadState loadImpl() {
        // No pre-loading necessary - bitmap pyramids are calculated as
        // the map view is panned
        return LoadState.SUCCESS;
    }

    public void setImage(File f) {
        if (_image == null || _image.getFile() != f) {
            if (_image != null)
                _image.dispose();
            _image = new BitmapPyramid(f);
        }
    }

    public BitmapPyramid getImage() {
        return _image;
    }

    @Override
    protected void onPointsChanged() {
        super.onPointsChanged();
        refreshBounds();
    }

    private void refreshBounds() {
        getBounds(_bounds);
        for (int i = 0; i < 4; i++) {
            PointMapItem pmi = getPointAt(i);
            if (pmi != null)
                _quad[i] = ATAKUtilities.geo2Vector(pmi.getPoint());
        }
    }

    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        // Touch on the line
        if (super.testOrthoHit(xpos, ypos, point, view))
            return true;

        // Touch within the imagery
        if (_bounds.contains(point) && Vector2D.polygonContainsPoint(
                ATAKUtilities.geo2Vector(point), _quad)) {
            setMetaString("menu_point", point.toStringRepresentation());
            setTouchPoint(point);
            return true;
        }

        return false;
    }

    public static RubberImage create(MapView mv, RubberImageData data) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (FileInputStream fis = IOProviderFactory
                .getInputStream(data.file)) {
            BitmapFactory.decodeStream(fis, null, opts);
        } catch (IOException ignored) {
        }
        float width = opts.outWidth, height = opts.outHeight;
        if (width <= 0 || height <= 0)
            return null;

        if (data.points == null) {
            // Calculate default extents if none are specified
            float mWidth = mv.getWidth();
            float mHeight = mv.getHeight();
            float mRot = mv.getRotation();
            float minDim = Math.min(mWidth, mHeight);
            double aspect = width / height;
            if (Double.isNaN(aspect) || Double.isInfinite(aspect))
                aspect = mWidth / mHeight;

            height = minDim * 0.9f;
            width = (float) (height * aspect);

            float left = (mWidth - width) / 2;
            float right = left + width;
            float top = (mHeight - height) / 2;
            float bottom = top + height;

            mv.getMapController().rotateTo(0, false);
            data.points = new GeoPoint[] {
                    mv.inverse(left, top, AtakMapView.InverseMode.RayCast)
                            .get(),
                    mv.inverse(right, top, AtakMapView.InverseMode.RayCast)
                            .get(),
                    mv.inverse(right, bottom, AtakMapView.InverseMode.RayCast)
                            .get(),
                    mv.inverse(left, bottom, AtakMapView.InverseMode.RayCast)
                            .get()
            };
            mv.getMapController().rotateTo(mRot, false);
        }
        return new RubberImage(data);
    }
}
