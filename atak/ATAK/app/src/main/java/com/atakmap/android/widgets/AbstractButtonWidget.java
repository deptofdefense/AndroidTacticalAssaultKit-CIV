
package com.atakmap.android.widgets;

import android.view.MotionEvent;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractButtonWidget extends MapWidget {

    private boolean _selectable;
    private String _text;
    private int _state;
    private WidgetIcon _icon;
    private WidgetBackground _bg;
    private final ConcurrentLinkedQueue<OnIconChangedListener> _iconChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnBackgroundChangedListener> _bgChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStateChangedListener> _stateChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTextChangedListener> _textChanged = new ConcurrentLinkedQueue<>();

    public static final String TAG = "AbstractButtonWidget";

    public static final int STATE_PRESSED = 1;
    public static final int STATE_SELECTED = 1 << 1;
    public static final int STATE_DISABLED = 1 << 2;

    /**
     * Listener for the state of the icon.    Will be fired when the icon is changed.
     */
    public interface OnIconChangedListener {
        void onButtonIconChanged(AbstractButtonWidget button);
    }

    /**
     * Listener for the state of the background.    Will be fired when the background is changed.
     */
    public interface OnBackgroundChangedListener {
        void onButtonBackgroundChanged(AbstractButtonWidget button);
    }

    /**
     * Listener for the state of the button.    Will be fired when the button state is changed.
     */
    public interface OnStateChangedListener {
        void onButtonStateChanged(AbstractButtonWidget button);
    }

    /**
     * Listener for the state of the text.    Will be fired when the text is changed.
     */
    public interface OnTextChangedListener {
        void onButtonTextChanged(AbstractButtonWidget button);
    }

    public static class Factory extends MapWidget.Factory {

        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            return null;
        }

        protected void configAttributes(AbstractButtonWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);

            // state
            FlagsParser.Parameters stateFlags = new FlagsParser.Parameters();
            stateFlags.setFlagBits("disabled", STATE_DISABLED);
            stateFlags.setFlagBits("selected", STATE_SELECTED);
            stateFlags.setFlagBits("pressed", STATE_PRESSED);
            int state = FlagsParser.parseFlagsText(stateFlags,
                    attrs.getNamedItem("attrs"));
            widget.setState(state);

            // selectable
            String selectableValue = DataParser.parseStringText(
                    attrs.getNamedItem("selectable"),
                    "");
            if (config.getPhraseParserParameters() != null) {
                selectableValue = PhraseParser.expandPhrase(selectableValue,
                        config.getPhraseParserParameters());
            }
            boolean selectable = DataParser.parseBoolean(selectableValue, true);
            widget.setSelectable(selectable);

            // text
            String text = DataParser.parseStringText(
                    attrs.getNamedItem("text"), "");
            if (config.getPhraseParserParameters() != null) {
                text = PhraseParser.expandPhrase(text,
                        config.getPhraseParserParameters());
            }
            widget.setText(text);

            // background
            // String bgUri = DataParser.parseStringText(attrs.getNamedItem("background"), null);
            // if (bgUri != null) {
            // WidgetBackground bg;
            // try {
            // ConfigEnvironment config2 =
            // config.buildUpon().setFlagsParameters(stateFlags).build();
            // bg = WidgetBackground.resolveWidgetBackground(config2, bgUri);
            // }
            // catch (Exception ex) {
            // Log.e(TAG, "error: ", ex);
            // }
            // }

            // icon
            String iconUri = DataParser.parseStringText(
                    attrs.getNamedItem("icon"), null);
            if (iconUri != null) {
                if (config.getPhraseParserParameters() != null) {
                    iconUri = PhraseParser.expandPhrase(iconUri,
                            config.getPhraseParserParameters());
                }
                WidgetIcon icon;
                try {
                    ConfigEnvironment config2 = config.buildUpon()
                            .setFlagsParameters(stateFlags)
                            .build();
                    icon = WidgetIcon.resolveWidgetIcon(config2, iconUri);
                    widget.setIcon(icon);
                } catch (Exception ex) {
                    Log.e(TAG, "error: ", ex);
                }
            }
        }
    }

    @Override
    public void onPress(MotionEvent event) {
        super.onPress(event);
        setState(_state | STATE_PRESSED);
    }

    @Override
    public void onUnpress(MotionEvent event) {
        super.onUnpress(event);
        setState(getState() & ~STATE_PRESSED);
    }

    @Override
    public void onClick(MotionEvent event) {
        if ((getState() & STATE_DISABLED) == 0) {
            if (_selectable) {
                if ((getState() & STATE_SELECTED) == 0) {
                    setState(getState() | STATE_SELECTED);
                } else {
                    setState(getState() & ~STATE_SELECTED);
                }
            }
            super.onClick(event);
        }
    }

    public void setSelectable(boolean selectable) {
        _selectable = selectable;
    }

    public void setIcon(WidgetIcon icon) {
        if (_icon != icon) {
            _icon = icon;
            onIconChanged();
        }
    }

    public WidgetIcon getIcon() {
        return _icon;
    }

    public void setBackground(WidgetBackground bg) {
        if (_bg != bg) {
            _bg = bg;
            onBackgroundChanged();
        }
    }

    public WidgetBackground getBackground() {
        return _bg;
    }

    public void setState(int state) {
        if (_state != state) {
            _state = state;
            onStateChanged();
        }
    }

    public int getState() {
        return _state;
    }

    /**
     * Set the text for the abstract button widget.
     * @param text the text to set for the abstract button widget.
     */
    public void setText(final String text) {
        if (_text == null || !_text.equals(text)) {
            _text = text;
            onTextChanged();
        }
    }

    public String getText() {
        return _text;
    }

    public void addOnIconChangedListener(OnIconChangedListener l) {
        _iconChanged.add(l);
    }

    public void removeOnIconChangedListener(OnIconChangedListener l) {
        _iconChanged.remove(l);
    }

    public void addOnBackgroundChangedListener(OnBackgroundChangedListener l) {
        _bgChanged.add(l);
    }

    public void removeOnBackgroundChangedListener(
            OnBackgroundChangedListener l) {
        _bgChanged.remove(l);
    }

    public void addOnTextChangedListener(OnTextChangedListener l) {
        _textChanged.add(l);
    }

    public void removeOnTextChangedListener(OnTextChangedListener l) {
        _textChanged.remove(l);
    }

    public void addOnStateChangedListener(OnStateChangedListener l) {
        _stateChanged.add(l);
    }

    public void removeOnStateChangedListener(OnStateChangedListener l) {
        _stateChanged.remove(l);
    }

    protected void onIconChanged() {
        for (OnIconChangedListener l : _iconChanged) {
            l.onButtonIconChanged(this);
        }
    }

    protected void onBackgroundChanged() {
        for (OnBackgroundChangedListener l : _bgChanged) {
            l.onButtonBackgroundChanged(this);
        }
    }

    protected void onStateChanged() {
        for (OnStateChangedListener l : _stateChanged) {
            l.onButtonStateChanged(this);
        }
    }

    protected void onTextChanged() {
        for (OnTextChangedListener l : _textChanged) {
            l.onButtonTextChanged(this);
        }
    }

    @SuppressWarnings("unused")
    private static String _getStringAttr(NamedNodeMap attrs, String name,
            String fallback) {
        String r = fallback;
        try {
            r = attrs.getNamedItem(name).getNodeValue();
        } catch (Exception ex) {
            // nothing
        }
        return r;
    }

}
