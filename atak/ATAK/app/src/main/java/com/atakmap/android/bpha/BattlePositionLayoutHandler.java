
package com.atakmap.android.bpha;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class BattlePositionLayoutHandler implements View.OnClickListener {

    public static final String TAG = "BattlePositionSelectorFragment";
    private LinearLayout _RootView = null;
    private final ViewGroup _AnchorGroup;
    private EditText _RowsET = null;
    private EditText _ColumnsET = null;
    private View _FixedGridSelector = null;
    private View _FixedGridSelector2 = null;
    private View _CustomGridSelector = null;
    private BPHAGridView _2x2 = null;
    private BPHAGridView _2x3 = null;
    private BPHAGridView _3x2 = null;
    private View _Custom = null;
    private BattlePositionHoldingArea _BPHA = null;
    private TextView _CustomButtonFooter = null;

    private final BattlePositionSelectorEventHandler handler;

    public BattlePositionLayoutHandler(LayoutInflater inflater,
            ViewGroup container,
            BattlePositionSelectorEventHandler bpHandler) {
        _AnchorGroup = container;
        handler = bpHandler;
        createView(inflater);

    }

    /**
     * The Selection Event Handler
     */
    public interface BattlePositionSelectorEventHandler {
        /**
         * The callback for the the specific grid is selected
         * @param bpha the selected battle position holding area
         */
        void onGridSelected(BattlePositionHoldingArea bpha);
    }

    synchronized private void createView(LayoutInflater inflater) {
        if (_RootView != null)
            return;

        _RootView = inflater.inflate(
                R.layout.battle_position_selector_view, _AnchorGroup,
                true).findViewById(R.id.GridSelectionHolder);

        _RowsET = _RootView.findViewById(R.id.rowsET);
        _RowsET.setSelectAllOnFocus(true);

        _ColumnsET = _RootView.findViewById(R.id.columnsET);
        _ColumnsET.setSelectAllOnFocus(true);

        _FixedGridSelector = _RootView.findViewById(R.id.fixedGridsSelector);
        _FixedGridSelector2 = _RootView.findViewById(R.id.fixedGridsSelector2);
        _CustomGridSelector = _RootView.findViewById(R.id.customGridsSelector);
        _2x2 = _RootView.findViewById(R.id.gridView2x2);
        _2x3 = _RootView.findViewById(R.id.gridView2x3);
        _3x2 = _RootView.findViewById(R.id.gridView3x2);
        _Custom = _RootView.findViewById(R.id.buttonCustom);
        _CustomButtonFooter = _RootView
                .findViewById(R.id.textViewCustomValue);

        ((View) _2x2.getParent()).setOnClickListener(this);
        ((View) _2x3.getParent()).setOnClickListener(this);
        ((View) _3x2.getParent()).setOnClickListener(this);

        _Custom.setOnClickListener(this);

        _FixedGridSelector.setVisibility(View.VISIBLE);
        _FixedGridSelector2.setVisibility(View.VISIBLE);
        _CustomGridSelector.setVisibility(View.GONE);

        _2x2.set_Columns(2);
        _2x2.set_Rows(2);

        _2x3.set_Columns(3);
        _2x3.set_Rows(2);

        _3x2.set_Columns(2);
        _3x2.set_Rows(3);

        int row = 2;
        int col = 2;
        try {
            SharedPreferences sp = PreferenceManager
                    .getDefaultSharedPreferences(_RootView.getContext());
            row = sp.getInt("bpha_custom_row", 2);
            col = sp.getInt("bpha_custom_col", 2);
            _RowsET.setText(String.valueOf(row));
            _ColumnsET.setText(String.valueOf(col));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read BPHA preferences", e);
        }
        if (_BPHA == null) {
            _BPHA = new BattlePositionHoldingArea(row, col);
            if (handler != null) {
                handler.onGridSelected(_BPHA);
            }
        }

        Button customOKButton = _RootView
                .findViewById(R.id.customOK_button);
        customOKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _FixedGridSelector.setVisibility(View.VISIBLE);
                _FixedGridSelector2.setVisibility(View.VISIBLE);
                _CustomGridSelector.setVisibility(View.GONE);

                ((View) _2x2.getParent()).setSelected(false);
                ((View) _2x3.getParent()).setSelected(false);
                ((View) _3x2.getParent()).setSelected(false);
                _Custom.setSelected(true);
            }
        });

        setSelected();

        _RowsET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!_RowsET.getText().toString().equals("")) {
                    _BPHA.setRows(Integer
                            .parseInt(_RowsET.getText().toString()));
                    setGridSizeText();
                    if (handler != null) {
                        handler.onGridSelected(_BPHA);
                    }
                    try {
                        SharedPreferences sp = PreferenceManager
                                .getDefaultSharedPreferences(
                                        _RootView.getContext());
                        sp.edit().putInt("bpha_custom_row", _BPHA.getRows())
                                .apply();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        _ColumnsET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!_ColumnsET.getText().toString().equals("")) {
                    _BPHA.setColumns(Integer.parseInt(_ColumnsET.getText()
                            .toString()));
                    setGridSizeText();
                    if (handler != null) {
                        handler.onGridSelected(_BPHA);
                    }
                    try {
                        SharedPreferences sp = PreferenceManager
                                .getDefaultSharedPreferences(
                                        _RootView.getContext());
                        sp.edit().putInt("bpha_custom_col", _BPHA.getColumns())
                                .apply();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void setSelected() {
        setGridSizeText();
        if (_BPHA.getRows() == 2 && _BPHA.getColumns() == 2) {
            ((View) _2x2.getParent()).setSelected(true);
            ((View) _2x3.getParent()).setSelected(false);
            ((View) _3x2.getParent()).setSelected(false);
            _Custom.setSelected(false);
        } else if (_BPHA.getRows() == 2 && _BPHA.getColumns() == 3) {
            ((View) _2x2.getParent()).setSelected(false);
            ((View) _2x3.getParent()).setSelected(true);
            ((View) _3x2.getParent()).setSelected(false);
            _Custom.setSelected(false);
        } else if (_BPHA.getRows() == 3 && _BPHA.getColumns() == 2) {
            ((View) _2x2.getParent()).setSelected(false);
            ((View) _2x3.getParent()).setSelected(false);
            ((View) _3x2.getParent()).setSelected(true);
            _Custom.setSelected(false);
        } else {
            ((View) _2x2.getParent()).setSelected(false);
            ((View) _2x3.getParent()).setSelected(false);
            ((View) _3x2.getParent()).setSelected(false);
            _Custom.setSelected(true);
        }
    }

    private void setGridSizeText() {
        int rows = 1, columns = 1;
        try {
            rows = Integer.parseInt(_RowsET.getText().toString());
        } catch (NumberFormatException nfe) {
            _RowsET.setText("1");
        }
        try {
            columns = Integer.parseInt(_ColumnsET.getText().toString());
        } catch (NumberFormatException nfe) {
            _ColumnsET.setText("1");
        }

        final String value = columns + " x " + rows;
        _CustomButtonFooter.setText(value);
    }

    @Override
    public void onClick(View view) {
        if (view == _2x2.getParent()) {
            _BPHA.setRows(2);
            _BPHA.setColumns(2);
        } else if (view == _2x3.getParent()) {
            _BPHA.setRows(2);
            _BPHA.setColumns(3);
        } else if (view == _3x2.getParent()) {
            _BPHA.setRows(3);
            _BPHA.setColumns(2);
        } else if (view == _Custom) {
            _FixedGridSelector.setVisibility(View.GONE);
            _FixedGridSelector2.setVisibility(View.GONE);
            _CustomGridSelector.setVisibility(View.VISIBLE);

            try {
                _BPHA.setRows(Integer.parseInt(_RowsET.getText().toString()));
            } catch (NumberFormatException nfe) {
                _BPHA.setRows(1);
            }
            try {
                _BPHA.setColumns(
                        Integer.parseInt(_ColumnsET.getText().toString()));
            } catch (NumberFormatException nfe) {
                _BPHA.setColumns(1);
            }
        }
        setSelected();
        if (handler != null) {
            handler.onGridSelected(_BPHA);
        }
    }

}
