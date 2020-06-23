
package com.atakmap.android.rubbersheet.data;

import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.Models.OnTransformProgressListener;

/**
 * Model transform progress listener that properly tracks progress across multiple meshes
 * TODO: Add this capability to core?
 */
public class ModelTransformListener implements OnTransformProgressListener {

    private final OnTransformProgressListener _callback;
    private final int _trProgressMax;
    private int _trProgressStage;
    private int _trLastProgress;

    public ModelTransformListener(Model model, OnTransformProgressListener cb) {
        _trProgressStage = _trLastProgress = 0;
        _trProgressMax = model.getNumMeshes() * 100;
        _callback = cb;
    }

    @Override
    public void onTransformProgress(int progress) {
        if (_trLastProgress > progress)
            _trProgressStage += 100;
        _trLastProgress = progress;
        progress = Math.round(((float) (_trProgressStage + progress)
                / _trProgressMax) * 100);
        _callback.onTransformProgress(progress);
    }
}
