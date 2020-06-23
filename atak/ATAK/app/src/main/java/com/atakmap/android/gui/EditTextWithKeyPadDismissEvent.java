
package com.atakmap.android.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

public class EditTextWithKeyPadDismissEvent extends EditText {

    private KeyPadDismissListener _keyPadDismissListener = null;

    public interface KeyPadDismissListener {
        void onKeyPadDismissed(EditText who);
    }

    public EditTextWithKeyPadDismissEvent(Context context) {
        super(context);
    }

    public EditTextWithKeyPadDismissEvent(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextWithKeyPadDismissEvent(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                dispatchKeyEvent(event);
                if (_keyPadDismissListener != null) {
                    _keyPadDismissListener.onKeyPadDismissed(this);
                }
                return false;
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    public void set_keyPadDismissListener(KeyPadDismissListener listener) {
        _keyPadDismissListener = listener;
    }
}
