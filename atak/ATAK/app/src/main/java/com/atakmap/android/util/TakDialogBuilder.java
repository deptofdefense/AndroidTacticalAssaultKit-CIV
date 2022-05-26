
package com.atakmap.android.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.atakmap.android.maps.MapItem;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

public class TakDialogBuilder
        implements MappingAdapterEventReceiver<MappingVM> {

    private final Dialog _takDialog;
    private final TakDialogAdapter _takDialogAdapter;
    private List<MappingVM> _models;
    private final boolean _isMultiSelect;
    private ArrayList<Integer> _selections;

    public TakDialogBuilder(Context context, final @StringRes int title,
            boolean isMultiSelect, final TakDialogSelectionListener listener) {
        _takDialogAdapter = new TakDialogAdapter(context);
        _takDialogAdapter.setEventReceiver(this);

        this._isMultiSelect = isMultiSelect;

        LayoutInflater _layoutInflater = LayoutInflater.from(context);
        View dialogView = _layoutInflater.inflate(R.layout.dialog_details,
                null);
        TextView titleView = dialogView.findViewById(R.id.title);
        titleView.setText(title);

        ListView listview = dialogView.findViewById(R.id.list);
        listview.setAdapter(_takDialogAdapter);

        final AlertDialog.Builder ab = new AlertDialog.Builder(context);
        ab.setView(dialogView);
        _takDialog = ab.create();

        ImageButton _closeButton = dialogView.findViewById(R.id.closeButton);
        _closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _takDialog.hide();
            }
        });

        Button _confirmButton = dialogView.findViewById(R.id.confirm);
        _confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.dialogCompletedWithResult(_takDialog, _selections);
                _takDialog.hide();
            }
        });
    }

    public Dialog getDialog() {
        return _takDialog;
    }

    public void update(final List<?> items) {
        _models = new ArrayList<>();
        _selections = new ArrayList<>();

        for (Object item : items) {
            if (item instanceof MapItem) {
                MapItem mapItem = (MapItem) item;
                _models.add(new TakDialogMapItemVM(mapItem, false));
            }
        }
        _takDialogAdapter.replaceItems(_models);
    }

    @Override
    public void eventReceived(MappingVM vm) {
        int index = _models.indexOf(vm);
        if (!_isMultiSelect) {
            _selections = new ArrayList<>();
        }

        if (_selections.contains(index)) {
            _selections.remove(index);
        } else {
            _selections.add(index);
        }

        for (MappingVM item : _models) {
            if (item instanceof TakDialogMapItemVM) {
                TakDialogMapItemVM mapVm = (TakDialogMapItemVM) item;
                mapVm.setSelected(_selections.contains(_models.indexOf(mapVm)));
            }
        }

        _takDialogAdapter.replaceItems(_models);
    }

    private void confirmSelected() {

    }
}
