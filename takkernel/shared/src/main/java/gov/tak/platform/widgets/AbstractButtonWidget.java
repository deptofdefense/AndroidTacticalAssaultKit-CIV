
package gov.tak.platform.widgets;

import gov.tak.api.util.Visitor;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.io.UriFactory;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.ui.MotionEvent;

import gov.tak.api.widgets.IAbstractButtonWidget;
import gov.tak.api.widgets.IWidgetBackground;

import gov.tak.platform.config.ConfigEnvironment;


import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractButtonWidget extends MapWidget implements IAbstractButtonWidget {

    private boolean _selectable;
    private String _text;
    private int _state;
    private IIcon _icon;
    private IWidgetBackground _bg;
    private final ConcurrentLinkedQueue<OnIconChangedListener> _iconChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnBackgroundChangedListener> _bgChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStateChangedListener> _stateChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTextChangedListener> _textChanged = new ConcurrentLinkedQueue<>();

    public static final String TAG = "AbstractButtonWidget";

    public static final int STATE_PRESSED = 1;
    public static final int STATE_SELECTED = 1 << 1;
    public static final int STATE_DISABLED = 1 << 2;
    public static final int STATE_HOVERED = 1 << 3;



    @Deprecated
    public static class Factory extends MapWidget.Factory {

        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            return null;
        }
    }

    @Override
    protected void applyPropertyChange(String propertyName, Object newValue) {
        if (PROPERTY_STATE.canAssignValue(propertyName, newValue) ||
            PROPERTY_ATTRS.canAssignValue(propertyName, newValue)) {
            StateFlags stateFlags = (StateFlags)newValue;
            if (stateFlags == null)
                this.setState(0);
            else
                this.setState(stateFlags.getValue());
        } else if (PROPERTY_SELECTABLE.canAssignValue(propertyName, newValue)) {
            this.setSelectable((Boolean) newValue);
        } else if (PROPERTY_TEXT.canAssignValue(propertyName, newValue)) {
            this.setText(newValue != null ? newValue.toString() : null);
        } else if (PROPERTY_ICON.canAssignValue(propertyName, newValue)) {
            this.setWidgetIcon((IIcon)newValue);
        } else {
            super.applyPropertyChange(propertyName, newValue);
        }
    }

    @Override
    public Object getPropertyValue(String propertyName) {
        if (PROPERTY_STATE.hasName(propertyName) || PROPERTY_ATTRS.hasName(propertyName)) {
            return new StateFlags(this.getState());
        } else if (PROPERTY_SELECTABLE.hasName(propertyName)) {
            return _selectable;
        } else if (PROPERTY_TEXT.hasName(propertyName)) {
            return this.getText();
        } else if (PROPERTY_ICON.hasName(propertyName)) {
            return this.getWidgetIcon();
        } else {
            return super.getPropertyValue(propertyName);
        }
    }

    @Override
    public void visitPropertyInfos(Visitor<PropertyInfo> visitor) {
        super.visitPropertyInfos(visitor);
        visitor.visit(PROPERTY_STATE);
        visitor.visit(PROPERTY_ATTRS); // same as state
        visitor.visit(PROPERTY_SELECTABLE);
        visitor.visit(PROPERTY_TEXT);
        visitor.visit(PROPERTY_ICON);
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
    public void onHover(MotionEvent event) {
        super.onHover(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER:
                setState(getState() | STATE_HOVERED);
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                setState(getState() & ~STATE_HOVERED);
                break;
        }
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

    @Override
    public IIcon getWidgetIcon() {
        return _icon;
    }

    @Override
    public void setWidgetBackground(IWidgetBackground bg) {
        if (_bg != bg) {
            _bg = bg;
            onBackgroundChanged();
        }
    }

    @Override
    public IWidgetBackground getWidgetBackground() {
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

    public void onIconChanged() {
        for (OnIconChangedListener l : _iconChanged) {
            l.onButtonIconChanged(this);
        }
    }

    public void onBackgroundChanged() {
        for (OnBackgroundChangedListener l : _bgChanged) {
            l.onButtonBackgroundChanged(this);
        }
    }

    public void onStateChanged() {
        for (OnStateChangedListener l : _stateChanged) {
            l.onButtonStateChanged(this);
        }
    }

    public void onTextChanged() {
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
