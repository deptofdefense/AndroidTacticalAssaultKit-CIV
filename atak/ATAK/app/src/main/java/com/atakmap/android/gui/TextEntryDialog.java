
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * Wrapper class for an AlertDialog which captures text input from the user
 * and emits it to a listener.
 *
 * Use <code>setValidator(Predicate\<String\>)</code> to provide a validating
 * function, which will allow the dialog to call <code>setError</code>
 * on the underlying text view if the entered text is invalid. The default
 * validator returns <code>true</code> for every non-null string.
 *
 * Use <code>setInputType(int)</code> with Android's InputType constants
 * to dictate what type of soft keyboard gets shown when the text view gains focus.
 *
 * Use <code>subscribe(TextEntryEventListener)</code> to listen for the
 * event emitted by this dialog.
 *
 * 
 */
public class TextEntryDialog {

    /**
     * A Predicate can determine a true or false value for any input of its
     * parameterized type. For example, a {@code RegexPredicate} might implement
     * {@code Predicate<String>}, and return true for any String that matches its
     * given regular expression.
     * <p/>
     * <p/>
     * Implementors of Predicate which may cause side effects upon evaluation are
     * strongly encouraged to state this fact clearly in their API documentation.
     */
    public interface Predicate<T> {
        boolean apply(T t);
    }

    public interface TextEntryEventListener {
        void onEvent(TextEntryEvent event);
    }

    private final Context _context;
    private final AlertDialog.Builder _builder;
    private final Resources _resources;
    private TextEntryEventListener _observer;
    private Predicate<String> _validator;
    private EditText _entry;
    private TextView _messages;
    private String _errorMessage;

    public TextEntryDialog(@Nullable String initialValue) {
        _context = MapView.getMapView().getContext();
        _builder = new AlertDialog.Builder(_context);
        _resources = MapView.getMapView()
                .getContext()
                .getResources();
        _validator = new Predicate<String>() {
            @Override
            public boolean apply(String s) {
                return s != null;
            }
        };
        _errorMessage = "Error: Invalid Text!";

        _builder.setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String text = _entry.getText().toString();
                        if (_validator.apply(text)) {
                            if (_observer != null) {
                                _observer.onEvent(new TextEntryEvent(text));
                            }
                            dialog.dismiss();
                        }
                    }
                });

        _builder.setNegativeButton(
                android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        buildView(initialValue);
    }

    public TextEntryDialog setTitle(@Nullable String title) {
        if (title != null) {
            _builder.setTitle(title);
        }
        return this;
    }

    public TextEntryDialog setMessage(@Nullable String message) {
        if (message != null && message.length() > 0) {
            if (!_messages.isShown()) {
                _messages.setVisibility(View.VISIBLE);
            }

            _messages.setText(message);
        } else {
            if (_messages.isShown()) {
                _messages.setVisibility(View.GONE);
            }
        }

        return this;
    }

    public TextEntryDialog setErrorMessage(@Nullable String message) {
        _errorMessage = message != null ? message : _errorMessage;
        return this;
    }

    public TextEntryDialog setHint(@Nullable String hint) {
        _entry.setHint(hint != null ? hint : "Enter Text");
        return this;
    }

    public TextEntryDialog setValidator(@NonNull Predicate<String> validator) {
        _validator = validator;
        return this;
    }

    public TextEntryDialog subscribe(@NonNull TextEntryEventListener observer) {
        _observer = observer;
        return this;
    }

    public TextEntryDialog setInputType(int inputType) {
        _entry.setInputType(inputType);
        return this;
    }

    public TextEntryDialog setInputFilter(@NonNull InputFilter[] filters) {
        _entry.setFilters(filters);
        return this;
    }

    public void show() {
        _builder.show();
    }

    private void buildView(String initialValue) {
        int pad = (int) _resources.getDimension(R.dimen.auto_margin);

        RelativeLayout root = new RelativeLayout(_context);
        root.setLayoutParams(
                new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        root.setPadding(pad, pad, pad, pad);

        // messages text
        int id = View.generateViewId();
        RelativeLayout.LayoutParams textViewLayout = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        _messages = new TextView(_context);
        _messages.setId(id);
        _messages.setLayoutParams(textViewLayout);
        _messages.setVisibility(View.GONE);
        _messages.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(_messages);

        // edit text
        RelativeLayout.LayoutParams editTextLayout = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        editTextLayout.setMargins(0, pad, 0, 0);
        editTextLayout.addRule(RelativeLayout.BELOW, id);
        _entry = new EditText(_context);
        _entry.setLayoutParams(editTextLayout);
        if (initialValue != null && initialValue.length() > 0) {
            _entry.setText(initialValue);
            _entry.setSelection(initialValue.length());
        }
        _entry.setInputType(InputType.TYPE_CLASS_TEXT);
        _entry.setImeOptions(
                EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        _entry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                if (_validator != null && !_validator.apply(s.toString())) {
                    _entry.setError(_errorMessage);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        root.addView(_entry);
        _builder.setView(root);
    }

    public static class TextEntryEvent {

        private final String _text;

        TextEntryEvent(@Nullable String text) {
            _text = text;
        }

        @Nullable
        public String getText() {
            return _text;
        }
    }
}
