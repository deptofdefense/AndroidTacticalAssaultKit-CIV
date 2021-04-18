
package com.atakmap.android.vehicle.model.icon;

import android.graphics.Bitmap;

import com.atakmap.android.imagecapture.opengl.GLOffscreenCaptureParams;
import com.atakmap.android.model.opengl.GLModelCaptureRequest;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelInfo;

/**
 * Render a vehicle model to a bitmap
 */
public class VehicleModelCaptureRequest extends GLModelCaptureRequest {

    protected final VehicleModelInfo _vehicle;
    protected boolean _failed;

    public VehicleModelCaptureRequest(VehicleModelInfo vehicle) {
        super(vehicle.name);
        _vehicle = vehicle;
    }

    @Override
    public void onStart() {
        VehicleModelCache.getInstance().registerUsage(_vehicle, _uid);
        _model = _vehicle.getModel();
        super.onStart();
    }

    @Override
    public void onDraw(GLOffscreenCaptureParams params) {
        if (_model == null)
            _failed = true;
        super.onDraw(params);
        VehicleModelCache.getInstance().unregisterUsage(_vehicle, _uid);
    }

    @Override
    public void onFinished(Bitmap bmp) {
        if (_failed) {
            _outFile = null;
            bmp = null;
        }
        super.onFinished(bmp);
    }
}
