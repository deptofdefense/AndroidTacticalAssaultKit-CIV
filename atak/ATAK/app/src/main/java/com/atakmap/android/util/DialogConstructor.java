
package com.atakmap.android.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.gui.EditTextWithKeyPadDismissEvent;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

public class DialogConstructor {

    public static void buildDialog(final Context _context, final EditText et,
            final String metaValueToSet, String title, int inputType,
            final Boolean storeAsString, final PointMapItem target) {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(title);
        final EditTextWithKeyPadDismissEvent o = new EditTextWithKeyPadDismissEvent(
                _context);
        o.setInputType(inputType);
        o.setSingleLine(true);
        o.setFilters(et.getFilters());
        o.setText(et.getText().toString());
        o.selectAll();
        b.setView(o);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);

        final AlertDialog d = b.create();
        Window w = d.getWindow();
        if (w != null)
            w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        d.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String txt = o.getText().toString();
                        if (metaValueToSet != null) {
                            if (storeAsString) {
                                target.setMetaString(metaValueToSet, txt);
                            } else {
                                if (FileSystemUtils.isEmpty(txt)) {
                                    target.removeMetaData(metaValueToSet);
                                } else {
                                    try {
                                        int v = Integer.parseInt(txt);
                                        target.setMetaInteger(metaValueToSet,
                                                v);
                                    } catch (Exception e) {
                                        Toast.makeText(_context,
                                                R.string.invalid_number,
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                }
                            }
                        }
                        et.setText(txt);
                        d.dismiss();
                    }
                });
        o.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    InputMethodManager imm = (InputMethodManager) _context
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null)
                        imm.hideSoftInputFromWindow(o.getWindowToken(), 0);
                    o.clearFocus();
                    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                    return true;
                } else
                    return false;
            }
        });
        o.requestFocus();
    }
}
