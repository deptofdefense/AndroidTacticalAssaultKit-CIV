
package com.atakmap.android.gui;

import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.app.AlertDialog;
import android.text.Editable;
import android.widget.TextView;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.math.MathUtils;

/**
 * Implementation of an EditText.OnClickListener that does not allow for an 
 * EditText to be empty.
 */
public class NonEmptyEditTextDialog implements EditText.OnClickListener {

    @Override
    public void onClick(View v) {
        if (!(v instanceof EditText))
            throw new IllegalArgumentException("View is not an EditText");

        final EditText src = (EditText) v;

        int inputFlags = src.getInputType();
        boolean singleLine = !MathUtils.hasBits(inputFlags,
                EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);

        final EditText et = new EditText(src.getContext());
        et.setText(src.getText());
        et.setHint(src.getHint());
        et.setContentDescription(src.getContentDescription());
        et.setMaxEms(src.getMaxEms());
        et.setInputType(inputFlags);
        et.setSelection(src.getText().length());
        et.setSingleLine(singleLine);

        CharSequence hint = src.getHint();
        CharSequence contentDescription = src.getContentDescription();

        String title = null;
        if (hint != null && !hint.toString().trim().equals(""))
            title = hint.toString();
        else if (contentDescription != null)
            title = contentDescription.toString();

        // Now create the Dialog itself.
        AlertDialog.Builder b = new AlertDialog.Builder(src.getContext());
        if (title != null)
            b.setTitle(title);
        b.setView(et);
        b.setPositiveButton(R.string.done, null);
        b.setNegativeButton(R.string.cancel, null);
        final AlertDialog dialog = b.create();

        // Show the keyboard when the dialog is opened
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        dialog.show();

        // Default enabled state
        final Button doneBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        doneBtn.setEnabled(et.getText().toString().trim().length() > 0);

        // Prevent the "Done" button from closing the dialog if the text is empty
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = et.getText().toString();
                if (FileSystemUtils.isEmpty(text.trim()))
                    return;
                src.setText(et.getText());
                dialog.dismiss();
            }
        });

        // Update the state of the "Done" button based on the amount of text
        et.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                doneBtn.setEnabled(editable.toString().length() > 0);
            }
        });

        // Close the dialog when the "Done" button on the soft keyboard is clicked
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int act, KeyEvent evt) {
                if (act == EditorInfo.IME_ACTION_DONE)
                    doneBtn.performClick();
                return false;
            }
        });

        et.requestFocus();
    }

}
