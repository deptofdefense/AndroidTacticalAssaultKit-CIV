
package com.atakmap.android.widgets;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.platform.ui.MotionEvent;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IAbstractButtonWidget;
import gov.tak.api.widgets.IButtonWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IWidgetBackground;

@Deprecated
@DeprecatedApi(since = "4.4")
public abstract class AbstractButtonWidget extends MapWidget
        implements IAbstractButtonWidget {

    private boolean _selectable;
    private String _text;
    private int _state;
    private IIcon _icon;
    private IWidgetBackground _bg;
    private final ConcurrentLinkedQueue<IButtonWidget.OnIconChangedListener> _iconChanged = new ConcurrentLinkedQueue<>();
    private final Map<AbstractButtonWidget.OnIconChangedListener, IButtonWidget.OnIconChangedListener> _iconChangedForwarders = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<IButtonWidget.OnBackgroundChangedListener> _bgChanged = new ConcurrentLinkedQueue<>();
    private final Map<AbstractButtonWidget.OnBackgroundChangedListener, IButtonWidget.OnBackgroundChangedListener> _bgChangedForwarders = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<IButtonWidget.OnStateChangedListener> _stateChanged = new ConcurrentLinkedQueue<>();
    private final Map<AbstractButtonWidget.OnStateChangedListener, IButtonWidget.OnStateChangedListener> _stateChangedForwarders = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<IButtonWidget.OnTextChangedListener> _textChanged = new ConcurrentLinkedQueue<>();
    private final Map<AbstractButtonWidget.OnTextChangedListener, IButtonWidget.OnTextChangedListener> _textChangedForwarders = new IdentityHashMap<>();

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
        public IMapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            return null;
        }

        protected void configAttributes(IAbstractButtonWidget widget,
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
                    widget.setWidgetIcon(icon);
                } catch (Exception ex) {
                    Log.e(TAG, "error: ", ex);
                }
            }
        }
    }

    @Override
    public void onPress(MotionEvent event) {
        super.onPress(event);
        setState(getState() | STATE_PRESSED);
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

    @Override
    public void setWidgetIcon(IIcon icon) {
        if (_icon != icon) {
            _icon = icon;
            onIconChanged();
        }
    }

    public void setIcon(WidgetIcon icon) {
        setWidgetIcon(icon);
    }

    @Override
    public IIcon getWidgetIcon() {
        return _icon;
    }

    public WidgetIcon getIcon() {
        IIcon icon = getWidgetIcon();
        if (icon instanceof WidgetIcon)
            return (WidgetIcon) icon;
        return null;
    }

    @Override
    public void setWidgetBackground(IWidgetBackground bg) {
        if (_bg != bg) {
            _bg = bg;
            onBackgroundChanged();
        }
    }

    public void setBackground(IWidgetBackground bg) {
        setWidgetBackground(bg);
    }

    @Override
    public IWidgetBackground getWidgetBackground() {
        return _bg;
    }

    public WidgetBackground getBackground() {
        IWidgetBackground bg = getWidgetBackground();
        if (bg instanceof WidgetBackground)
            return (WidgetBackground) bg;
        return null;
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

    @Override
    public final void addOnIconChangedListener(
            IButtonWidget.OnIconChangedListener l) {
        _iconChanged.add(l);
    }

    public void addOnIconChangedListener(
            AbstractButtonWidget.OnIconChangedListener l) {
        registerForwardedListener(_iconChanged, _iconChangedForwarders, l,
                new IconChangedForwarder(l));
    }

    @Override
    public final void removeOnIconChangedListener(
            IButtonWidget.OnIconChangedListener l) {
        _iconChanged.remove(l);
    }

    public void removeOnIconChangedListener(
            AbstractButtonWidget.OnIconChangedListener l) {
        unregisterForwardedListener(_iconChanged, _iconChangedForwarders, l);
    }

    @Override
    public final void addOnBackgroundChangedListener(
            IButtonWidget.OnBackgroundChangedListener l) {
        _bgChanged.add(l);
    }

    public void addOnBackgroundChangedListener(
            AbstractButtonWidget.OnBackgroundChangedListener l) {
        registerForwardedListener(_bgChanged, _bgChangedForwarders, l,
                new BackgroundChangedForwarder(l));
    }

    @Override
    public final void removeOnBackgroundChangedListener(
            IButtonWidget.OnBackgroundChangedListener l) {
        _bgChanged.remove(l);
    }

    public void removeOnBackgroundChangedListener(
            AbstractButtonWidget.OnBackgroundChangedListener l) {
        unregisterForwardedListener(_bgChanged, _bgChangedForwarders, l);
    }

    @Override
    public final void addOnTextChangedListener(
            IButtonWidget.OnTextChangedListener l) {
        _textChanged.add(l);
    }

    public void addOnTextChangedListener(
            AbstractButtonWidget.OnTextChangedListener l) {
        registerForwardedListener(_textChanged, _textChangedForwarders, l,
                new TextChangedForwarder(l));
    }

    @Override
    public final void removeOnTextChangedListener(
            IButtonWidget.OnTextChangedListener l) {
        _textChanged.remove(l);
    }

    public void removeOnTextChangedListener(
            AbstractButtonWidget.OnTextChangedListener l) {
        unregisterForwardedListener(_textChanged, _textChangedForwarders, l);
    }

    @Override
    public final void addOnStateChangedListener(
            IButtonWidget.OnStateChangedListener l) {
        _stateChanged.add(l);
    }

    public void addOnStateChangedListener(
            AbstractButtonWidget.OnStateChangedListener l) {
        registerForwardedListener(_stateChanged, _stateChangedForwarders, l,
                new StateChangedForwarder(l));
    }

    @Override
    public final void removeOnStateChangedListener(
            IButtonWidget.OnStateChangedListener l) {
        _stateChanged.remove(l);
    }

    public void removeOnStateChangedListener(
            AbstractButtonWidget.OnStateChangedListener l) {
        unregisterForwardedListener(_stateChanged, _stateChangedForwarders, l);
    }

    public void onIconChanged() {
        for (IButtonWidget.OnIconChangedListener l : _iconChanged) {
            l.onButtonIconChanged(this);
        }
    }

    public void onBackgroundChanged() {
        for (IButtonWidget.OnBackgroundChangedListener l : _bgChanged) {
            l.onButtonBackgroundChanged(this);
        }
    }

    public void onStateChanged() {
        for (IButtonWidget.OnStateChangedListener l : _stateChanged) {
            l.onButtonStateChanged(this);
        }
    }

    public void onTextChanged() {
        for (IButtonWidget.OnTextChangedListener l : _textChanged) {
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

    private final static class IconChangedForwarder
            implements IAbstractButtonWidget.OnIconChangedListener {
        final AbstractButtonWidget.OnIconChangedListener _cb;

        IconChangedForwarder(AbstractButtonWidget.OnIconChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onButtonIconChanged(IAbstractButtonWidget button) {
            if (button instanceof AbstractButtonWidget)
                _cb.onButtonIconChanged((AbstractButtonWidget) button);
        }
    }

    private final static class TextChangedForwarder
            implements IAbstractButtonWidget.OnTextChangedListener {
        final AbstractButtonWidget.OnTextChangedListener _cb;

        TextChangedForwarder(AbstractButtonWidget.OnTextChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onButtonTextChanged(IAbstractButtonWidget button) {
            if (button instanceof AbstractButtonWidget)
                _cb.onButtonTextChanged((AbstractButtonWidget) button);
        }
    }

    private final static class BackgroundChangedForwarder
            implements IAbstractButtonWidget.OnBackgroundChangedListener {
        final AbstractButtonWidget.OnBackgroundChangedListener _cb;

        BackgroundChangedForwarder(
                AbstractButtonWidget.OnBackgroundChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onButtonBackgroundChanged(IAbstractButtonWidget button) {
            if (button instanceof AbstractButtonWidget)
                _cb.onButtonBackgroundChanged((AbstractButtonWidget) button);
        }
    }

    private final static class StateChangedForwarder
            implements IAbstractButtonWidget.OnStateChangedListener {
        final AbstractButtonWidget.OnStateChangedListener _cb;

        StateChangedForwarder(AbstractButtonWidget.OnStateChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onButtonStateChanged(IAbstractButtonWidget button) {
            if (button instanceof AbstractButtonWidget)
                _cb.onButtonStateChanged((AbstractButtonWidget) button);
        }
    }
}
