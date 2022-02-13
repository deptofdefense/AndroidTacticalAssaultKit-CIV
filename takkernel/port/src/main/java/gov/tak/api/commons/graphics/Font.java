package gov.tak.api.commons.graphics;

public final class Font {
    public enum Style {
        Normal,
        Bold,
        Italic,
        BoldItalic,
    }

    private final float _size;
    private final Style _style;
    private final String _name;

    public Font(String familyName, Style style, float size) {
        _name = familyName;
        _style = style;
        _size = size;
    }

    public float getSize() {
        return _size;
    }

    public String getFamilyName() {
        return _name;
    }

    public Style getStyle() {
        return _style;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Font))  return false;
        final Font other = (Font)obj;
        return _size == other._size &&
               _style == other._style &&
                ((_name == null && other._name == null) ||
                    ((_name != null && other._name != null) &&
                        _name.equals(other._name)));
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(_size) |
                ((_style != null) ? _style.ordinal() : 0) |
                ((_name != null) ? _name.hashCode() : 0);
    }
}
