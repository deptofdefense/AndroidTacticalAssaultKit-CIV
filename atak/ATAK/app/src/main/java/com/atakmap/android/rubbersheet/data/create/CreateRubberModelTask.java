
package com.atakmap.android.rubbersheet.data.create;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.android.rubbersheet.data.RubberSheetUtils;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.LoadState;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.rubbersheet.maps.RubberSheetMapGroup;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Task for creating an editable 3D model from a file
 */
public class CreateRubberModelTask extends AbstractCreationTask
        implements ModelLoader.Callback {

    private static final String TAG = "CreateRubberModelTask";

    private final RubberSheetMapGroup _group;
    private final RubberModelData _data;
    private final List<RubberModel> _models = new ArrayList<>();

    public CreateRubberModelTask(MapView mapView, RubberModelData model,
            boolean background, Callback callback) {
        super(mapView, model, background, callback);
        _data = model;
        _group = (RubberSheetMapGroup) mapView.getRootGroup()
                .deepFindMapGroup(RubberSheetMapGroup.GROUP_NAME);
    }

    @Override
    protected String getProgressMessage() {
        return _context.getString(R.string.creating_rubber_model,
                _data.file.getName());
    }

    @Override
    public String getFailMessage() {
        return _context.getString(R.string.failed_to_read_model,
                _data.file.getName());
    }

    @Override
    protected int getProgressStages() {
        return 1;
    }

    @Override
    protected void onCancelled() {
        toast(R.string.read_model_cancelled, _data.file.getName());
    }

    @Override
    public void onProgress(int progress) {
        if (!isBackground())
            publishProgress(progress);
    }

    @Override
    public void onLoad(ModelInfo info, Model model) {
        RubberModelData data = new RubberModelData(_data.file);
        data.projection = _data.projection;
        data.subModel = info.uri;
        if (data.subModel != null)
            data.label = new File(data.subModel).getName();

        if (info.rotation != null) {
            data.rotation = new double[] {
                    info.rotation.x, info.rotation.y, info.rotation.z
            };
        }

        if (info.scale != null) {
            data.scale = new double[] {
                    info.scale.x, info.scale.y, info.scale.z
            };
        }

        // Vertex units are interpreted as meters
        Envelope e = model.getAABB();
        double width = Math.abs(e.maxX - e.minX);
        double length = Math.abs(e.maxY - e.minY);
        double height = Math.abs(e.maxZ - e.minZ);

        data.dimensions = new double[] {
                width, length, height
        };

        // Apply model scale to rectangle
        if (data.scale != null && data.scale.length == 3) {
            width *= data.scale[0];
            length *= data.scale[1];
        }

        // Compute rectangle dimensions
        GeoPoint c = info.location;
        if (c == null)
            info.location = c = _mapView.getPointWithElevation().get();
        data.center = GeoPointMetaData.wrap(c);
        RubberSheetUtils.getAltitude(data.center);
        data.points = RubberSheetUtils.computeCorners(c, length, width, 0);

        RubberModel rm = RubberModel.create(data, info, model);
        if (rm != null)
            rm.setLoadState(LoadState.SUCCESS);

        if (_group != null)
            _group.add(rm);

        _models.add(rm);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        if (super.doInBackground(params) != Boolean.TRUE)
            return false;
        ModelLoader ml = new ModelLoader(_data, this);
        return ml.load();
    }

    @Override
    protected void onPostExecute(Object ret) {
        super.onPostExecute(ret);
        if (_callback != null)
            _callback.onFinished(this, new ArrayList<AbstractSheet>(_models));
    }
}
