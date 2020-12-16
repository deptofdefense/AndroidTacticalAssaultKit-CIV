
package com.atakmap.android.rubbersheet.data.create;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.AbstractSheetData;
import com.atakmap.android.rubbersheet.data.ProgressTask;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Task for creating an editable 3D model from a file
 */
public abstract class AbstractCreationTask extends ProgressTask {

    private static final String TAG = "AbstractCreationTask";

    protected final MapView _mapView;
    protected final AbstractSheetData _data;
    protected final boolean _background;
    protected final Callback _callback;

    public AbstractCreationTask(MapView mapView, AbstractSheetData sheet,
            boolean background, Callback callback) {
        super(mapView);
        _mapView = mapView;
        _data = sheet;
        _background = background;
        _callback = callback;
    }

    public boolean isBackground() {
        return _background;
    }

    @Override
    protected void onPreExecute() {
        if (_background)
            return;
        super.onPreExecute();
    }

    @Override
    protected Object doInBackground(Void... params) {
        File f = _data.file;
        if (!IOProviderFactory.exists(f) || !IOProviderFactory.isFile(f)) {
            Log.d(TAG, "File does not exist: " + f);
            if (!_background)
                toast(R.string.file_does_not_exist, f.getName());
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Object ret) {
        super.onPostExecute(ret);
        if (_callback != null && ret instanceof AbstractSheet)
            _callback.onFinished(this, Collections.singletonList(
                    (AbstractSheet) ret));
    }

    public interface Callback {
        void onFinished(AbstractCreationTask task, List<AbstractSheet> sheet);
    }

    public abstract String getFailMessage();
}
