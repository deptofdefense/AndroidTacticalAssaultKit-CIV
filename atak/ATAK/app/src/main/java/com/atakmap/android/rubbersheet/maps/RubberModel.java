
package com.atakmap.android.rubbersheet.maps;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.rubbersheet.data.ModelProjection;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.android.rubbersheet.data.RubberSheetUtils;
import com.atakmap.android.rubbersheet.data.create.ModelLoader;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A "rubber" model with rectangle controls
 */
public class RubberModel extends AbstractSheet implements ModelLoader.Callback {

    private static final String TAG = "RubberModel";

    private final ModelProjection _projection;
    private final String _subModelURI;
    private final double[] _scale = new double[] {
            1, 1, 1
    };
    private final double[] _rotation = new double[3];
    private final double[] _dimensions = new double[3];
    private final Set<OnChangedListener> _changeListeners = new HashSet<>();
    private boolean _sharedModel;

    // To be loaded
    private ModelInfo _info;
    private Model _model;

    protected RubberModel(RubberModelData data) {
        super(data);
        _projection = data.projection;
        _subModelURI = data.subModel;
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.ic_model_building));
    }

    private RubberModel(RubberModelData data, ModelInfo info, Model model) {
        this(data);
        _model = model;
        _info = info;
        if (data.dimensions != null && data.dimensions.length == 3)
            System.arraycopy(data.dimensions, 0, _dimensions, 0, 3);
    }

    @Override
    protected LoadState loadImpl() {
        ModelLoader ml = new ModelLoader(getFile(), _subModelURI,
                _projection, this);
        return ml.load() ? LoadState.SUCCESS : LoadState.FAILED;
    }

    @Override
    public boolean isCancelled() {
        return getGroup() == null;
    }

    @Override
    public void onProgress(int progress) {
        setLoadProgress(progress);
    }

    @Override
    public void onLoad(ModelInfo info, Model model) {
        _info = info;
        _model = model;
    }

    /**
     * Get the high-level model metadata
     * @return Model info
     */
    public ModelInfo getInfo() {
        return _info;
    }

    /**
     * Get the model data
     * @return Model data
     */
    public Model getModel() {
        return _model;
    }

    /**
     * Sub-model URI - used for KMZ DAE
     * @return Sub-model URI
     */
    public String getSubModelURI() {
        return _subModelURI;
    }

    /**
     * Get the user-specified model projection (how x-y-z is mapped)
     * @return Model projection
     */
    public ModelProjection getProjection() {
        return _projection;
    }

    /**
     * Get the unscaled model dimensions in meters
     *
     * @param scaled True to multiply the dimensions by the scale factor
     * @return [x (width), y (length), z (height)]
     */
    public double[] getModelDimensions(boolean scaled) {
        double[] ret = new double[3];
        if (_model != null) {
            Envelope e = _model.getAABB();
            _dimensions[0] = Math.abs(e.maxX - e.minX);
            _dimensions[1] = Math.abs(e.maxY - e.minY);
            _dimensions[2] = Math.abs(e.maxZ - e.minZ);
        }
        System.arraycopy(_dimensions, 0, ret, 0, 3);
        if (scaled) {
            for (int i = 0; i < ret.length; i++)
                ret[i] *= _scale[i];
        }
        return ret;
    }

    public double[] getModelDimensions() {
        return getModelDimensions(false);
    }

    /**
     * Get the scale for each model dimension (all 1.0 by default)
     *
     * @return [x-scale, y-scale, z-scale]
     */
    public double[] getModelScale() {
        return Arrays.copyOf(_scale, 3);
    }

    public void setModelScale(double[] scale) {
        if (scale == null || scale.length != 3)
            return;
        System.arraycopy(scale, 0, _scale, 0, 3);
    }

    /**
     * Get the rotation of the model in degrees (true north)
     *
     * @return [tilt, heading, roll] or [pitch, yaw, roll]
     */
    public double[] getModelRotation() {
        return Arrays.copyOf(_rotation, 3);
    }

    public void setModelRotation(double[] rotation) {
        if (rotation == null || rotation.length != 3)
            return;
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            if (Double.compare(_rotation[i], rotation[i]) != 0) {
                _rotation[i] = rotation[i];
                changed = true;
            }
        }
        if (changed) {
            for (OnChangedListener l : getChangeListeners())
                l.onRotationChanged(this, rotation);
        }
    }

    /**
     * Set the model altitude
     *
     * @param altitude Altitude in meters
     * @param ref Altitude reference
     */
    public void setAltitude(double altitude, GeoPoint.AltitudeReference ref) {
        Marker m = getCenterMarker();

        // no center marker
        if (m == null)
            return;

        GeoPoint c = m.getPoint();
        GeoPoint newPoint = new GeoPoint(c.getLatitude(), c.getLongitude(),
                altitude, ref, c.getCE(), c.getLE());
        m.setPoint(GeoPointMetaData.wrap(newPoint)
                .setAltitudeSource(GeoPointMetaData.USER));
        for (OnChangedListener l : getChangeListeners())
            l.onAltitudeChanged(this, altitude, ref);
    }

    /**
     * Set whether the underling Model object is shared across different instances
     * Determines if the model should be disposed on removal
     * @param shared True if shared
     */
    public void setSharedModel(boolean shared) {
        _sharedModel = shared;
    }

    public boolean isSharedModel() {
        return _sharedModel;
    }

    @Override
    protected String getMenuPath() {
        return "menus/rubber_model_menu.xml";
    }

    @Override
    protected void onPointsChanged() {
        // Make sure center still has a valid altitude
        GeoPointMetaData center = getCenter();
        if (!center.get().isAltitudeValid()) {
            RubberSheetUtils.getAltitude(center);
            Marker m = getCenterMarker();
            if (m != null)
                m.setPoint(center);
        }

        // Sync heading and scale
        GeoPointMetaData[] points = getGeoPoints();
        double[] dim = getModelDimensions();
        double scaleX = points[5].get().distanceTo(points[7].get()) / dim[0];
        double scaleY = points[4].get().distanceTo(points[6].get()) / dim[1];
        _scale[0] = _scale[1] = _scale[2] = Math.min(scaleX, scaleY);
        _rotation[1] = getHeading();

        if (_info != null)
            _info.location = center.get();

        super.onPointsChanged();
    }

    protected boolean isModelVisible() {
        return getAlpha() > 0;
    }

    /**
     * @deprecated This was previously used for hit testing but is no longer needed
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setRenderer(GLRubberModel renderer) {
    }

    public synchronized void addChangeListener(OnChangedListener l) {
        _changeListeners.add(l);
    }

    public synchronized void removeChangeListener(OnChangedListener l) {
        _changeListeners.remove(l);
    }

    protected synchronized List<OnChangedListener> getChangeListeners() {
        return new ArrayList<>(_changeListeners);
    }

    public interface OnChangedListener {
        /**
         * Model pitch or roll has been changed
         * Note that this is not called for heading, listen for onPointsChanged
         * instead
         * @param model Model instance
         * @param rotation The new rotation array
         */
        void onRotationChanged(RubberModel model, double[] rotation);

        /**
         * Center point altitude has been changed (by itself)
         * Note that this is not called when the point lat/lng has changed
         *
         * @param model Model instance
         * @param altitude New altitude in meters
         * @param reference Altitude reference
         */
        void onAltitudeChanged(RubberModel model, double altitude,
                GeoPoint.AltitudeReference reference);
    }

    // To be called on an async thread
    public static RubberModel create(RubberModelData data,
            ModelInfo info, Model model) {
        if (data == null || data.points == null)
            return null;

        // Resolve center point
        GeoPointMetaData center = data.center;
        if (center == null && info != null)
            center = GeoPointMetaData.wrap(info.location);
        if (center == null)
            center = GeoPointMetaData.wrap(GeoCalculations.computeAverage(
                    data.points));
        if (!center.get().isAltitudeValid())
            RubberSheetUtils.getAltitude(center);
        if (info != null)
            info.location = center.get();

        if (data.rotation != null && data.scale != null
                && data.dimensions != null) {
            // Instead of using the provided rectangle points, build our own
            // based on the scale, rotation, and center point
            double heading = data.rotation[1];
            double width = data.dimensions[0] * data.scale[0];
            double length = data.dimensions[1] * data.scale[1];
            data.points = RubberSheetUtils.computeCorners(center.get(), length,
                    width, heading);
        }

        RubberModel rm = new RubberModel(data, info, model);
        Marker cm = rm.getCenterMarker();
        if (cm != null)
            cm.setPoint(center);
        if (!FileSystemUtils.isEmpty(data.label))
            rm.setTitle(data.label);
        rm.setModelScale(data.scale);
        rm.setModelRotation(data.rotation);
        return rm;
    }
}
