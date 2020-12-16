
package com.atakmap.android.user;

import android.app.Activity;

import android.content.Context;
import android.text.Editable;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.coremap.log.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import android.text.InputType;
import android.text.InputFilter;
import java.util.List;

public class CustomNamingView
        implements View.OnTouchListener, View.OnClickListener {

    private static final String TAG = "CUSTOM_NAMING_VIEW";

    private final EditText _prefixET;
    private final EditText _startNumberET;
    private final ImageButton _resetButton;
    private final ImageButton _swapButton;
    private final MapView _mapView;
    private final LinearLayout _mainView;
    private final int _creatingPallet;

    public final static int DOTMAP = 0;
    public final static int VEHICLE = 1;
    public final static int OVERHEAD = 2;
    public final static int DEFAULT = 3;

    public CustomNamingView(int creatingPallet) {
        _mapView = MapView.getMapView();
        _creatingPallet = creatingPallet;

        LayoutInflater inflater = LayoutInflater.from(_mapView.getContext());
        _mainView = (LinearLayout) inflater.inflate(
                R.layout.custom_naming_view, null, false);
        _prefixET = _mainView.findViewById(R.id.prefix_et);
        _startNumberET = _mainView.findViewById(R.id.startIndex_et);
        _resetButton = _mainView.findViewById(R.id.resetButton);
        _swapButton = _mainView.findViewById(R.id.swapButton);

        //Inline on touch listener so the keyboard doesn't cover the Edit Text fields
        _prefixET.setOnTouchListener(this);
        //Inline on touch listener so the keyboard doesn't cover the Edit text fields
        _startNumberET.setOnTouchListener(this);

        _prefixET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                List<MapItem> list;
                switch (_creatingPallet) {
                    case DOTMAP:
                        list = _mapView
                                .getRootGroup()
                                .deepFindItems("type", "b-m-p-s-m");
                        break;
                    case VEHICLE:
                        list = _mapView
                                .getRootGroup()
                                .deepFindItems("type", "shape_marker");
                        break;
                    case OVERHEAD:
                        list = _mapView
                                .getRootGroup()
                                .deepFindItems("type", "overhead_marker");
                        break;
                    case DEFAULT:
                        list = null;
                        break;
                    default:
                        list = null;
                }

                String string = _prefixET.getText().toString();
                if (list != null) {
                    int count = PlacePointTool.getHighestNumbered(string, list);
                    if (count == 0) {
                        _startNumberET.setText("");
                    } else {
                        _startNumberET.setText(Integer.toString(count));
                    }
                } else {
                    _startNumberET.setText("");
                }
            }
        });

        _resetButton.setOnClickListener(this);
        _swapButton.setOnClickListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Activity a = (Activity) _mapView.getContext();
        a.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        return false;
    }

    @Override
    public void onClick(View v) {
        if (v == _resetButton) {
            _startNumberET.setText("");
            _prefixET.setText("");
            clearEditTextFocus();
        } else if (v == _swapButton) {
            _swapButton.setSelected(!_swapButton.isSelected());

            if (_swapButton.isSelected()) {
                _swapButton.setImageResource(R.drawable.swap_ab);
                _startNumberET
                        .setInputType(
                                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                _startNumberET.setFilters(new InputFilter[] {
                        new InputFilter.AllCaps()
                });
                _startNumberET.setText("");
            } else {
                _swapButton.setImageResource(R.drawable.swap_12);
                _startNumberET.setInputType(InputType.TYPE_CLASS_NUMBER);
                _startNumberET.setFilters(new InputFilter[] {});
                _startNumberET.setText("");
            }
        }
    }

    public LinearLayout getMainView() {
        return _mainView;
    }

    private static String forward(final String s) {
        if (s.length() > 0) {
            char c = s.charAt(s.length() - 1);
            if (c < 'Z') {
                c++;
                return s.substring(0, s.length() - 1) + c;
            } else {
                return forward(s.substring(0, s.length() - 1)) + 'A';
            }
        } else {
            return "A";
        }

    }

    public void incrementStartIndex() {

        if (_swapButton.isSelected()) {
            _startNumberET
                    .setText(forward(_startNumberET.getText().toString()));
        } else {
            int count = 0;
            try {
                count = Integer.parseInt(_startNumberET.getText().toString());
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "Start Index edit text did not contain a number.",
                        nfe);
            }
            count++;
            _startNumberET.setText(Integer.toString(count));
        }
    }

    public String genCallsign() {
        if (_prefixET.getText().toString().equals("")) {
            if (_startNumberET.getText().toString().equals("")) {
                return "";
            } else {
                return _startNumberET.getText().toString();
            }
        } else {
            String prefix = _prefixET.getText().toString();
            String idx = _startNumberET.getText().toString();
            if (idx.equals("") && !_swapButton.isSelected()) {
                idx = "1";
                _startNumberET.setText("1");
            } else if (idx.equals("")) {
                idx = "A";
                _startNumberET.setText("A");
            }
            return prefix + " " + idx;
        }
    }

    public void clearEditTextFocus() {
        Activity a = (Activity) _mapView.getContext();
        InputMethodManager imm = (InputMethodManager) a.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(_mainView.getWindowToken(), 0);
        _startNumberET.clearFocus();
        _startNumberET.setCursorVisible(false);
        _prefixET.clearFocus();
        _prefixET.setCursorVisible(false);
    }
}
