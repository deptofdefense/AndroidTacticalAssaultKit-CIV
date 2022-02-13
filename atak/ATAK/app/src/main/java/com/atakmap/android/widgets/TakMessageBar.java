
package com.atakmap.android.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atakmap.app.R;

public class TakMessageBar extends LinearLayout {

    private EditText _messageEditText;
    private ImageButton _sendButton;

    public TakMessageBar(Context context) {
        this(context, null);
    }

    public TakMessageBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TakMessageBar(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(new ContextThemeWrapper(context, R.style.AtakMessageBar), attrs,
                defStyleAttr);

        initializeView();
    }

    private void initializeView() {
        setBackgroundResource(R.drawable.message_bar_background);

        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.atak_message_bar, this, true);

        _messageEditText = view.findViewById(R.id.message);
        _sendButton = view.findViewById(R.id.send);
    }

    public void setOnSendClickListener(OnClickListener clickListener) {
        _sendButton.setOnClickListener(clickListener);
    }

    public CharSequence getText() {
        return _messageEditText.getText();
    }

    public void setText(CharSequence text) {
        _messageEditText.setText(text);
    }
}
