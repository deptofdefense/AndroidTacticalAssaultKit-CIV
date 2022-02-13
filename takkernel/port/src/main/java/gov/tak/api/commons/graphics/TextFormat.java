package gov.tak.api.commons.graphics;

public final class TextFormat {
    public final static int STYLE_UNDERLINE = 0x01;
    public final static int STYLE_STRIKETHROUGH = 0x02;
    public final static int STYLE_OUTLINED = 0x04;

    private static TextFormat defaultFormat = null;

    final Font _font;
    final int _style;

    public TextFormat(Font font, int style) {
        if(font == null)    throw new IllegalArgumentException();
        _font = font;
        _style = style;
    }

    public boolean isUnderline() {
        return ((_style&STYLE_UNDERLINE)==STYLE_UNDERLINE);
    }

    public boolean isStrikethrough() {
        return ((_style&STYLE_STRIKETHROUGH)==STYLE_STRIKETHROUGH);
    }

    public boolean isOutlined() {
        return ((_style&STYLE_OUTLINED)==STYLE_OUTLINED);
    }

    public Font getFont() {
        return _font;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof TextFormat))    return false;
        final TextFormat other = (TextFormat)obj;
        return _font.equals(other._font) && _style == other._style;
    }

    @Override
    public int hashCode() {
        return _font.hashCode() ^ _style;
    }
}
