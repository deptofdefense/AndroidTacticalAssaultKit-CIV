
package com.atakmap.android.toolbar.widgets;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;

import com.atakmap.android.gui.HintDialogHelper;

import android.view.LayoutInflater;
import android.view.View;

/**
 * Text container to show prompts / information on the screen.
 */
public class TextContainer implements MapWidget.OnClickListener, Runnable,
        MapWidget2.OnWidgetSizeChangedListener {

    public static final String TAG = "TextContainer";

    private static TextContainer _instance;

    private static final int xIconAnchor = 0;
    private static final int yIconAnchor = 0;
    private static final int xSize = 32; // Icon.SIZE_DEFAULT;
    private static final int ySize = 32; // Icon.SIZE_DEFAULT;

    private final MapView _mapView;

    private static final int DEFAULT_COLOR = Color.GREEN;
    protected MapTextFormat DEFAULT_FORMAT;

    protected TextWidget _text;
    protected final MarkerIconWidget _widget;
    private String _prompt = "";

    protected final Icon icon_lit;
    private final Icon icon_unlit;
    private final Icon icon_blank;

    protected MapTextFormat textFormat = MapView.getDefaultTextFormat();

    private int color = DEFAULT_COLOR;

    boolean displaying = false;
    private final SharedPreferences _prefs;

    private final View hint_view;

    protected TextContainer() {
        _mapView = MapView.getMapView();

        DEFAULT_FORMAT = MapView.getTextFormat(Typeface.DEFAULT_BOLD, +4);

        hint_view = LayoutInflater.from(_mapView.getContext()).inflate(
                R.layout.instruction_hint, null);

        // Create text container here instead of in initialize or a onCreateWidgets so that it gets
        // added *last*
        // That way it will be on top since widgets don't have a concept of z-order and are just
        // drawn
        // in the order they were added
        //layoutWidget.setBackingColor(0xff);

        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        LinearLayoutWidget topRight = root
                .getLayout(RootLayoutWidget.TOP_RIGHT);
        LinearLayoutWidget topEdge = root.getLayout(RootLayoutWidget.TOP_EDGE);
        LinearLayoutWidget teLayout = new LinearLayoutWidget(
                LinearLayoutWidget.MATCH_PARENT,
                LinearLayoutWidget.WRAP_CONTENT,
                LinearLayoutWidget.VERTICAL);
        teLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        _prefs = PreferenceManager.getDefaultSharedPreferences(
                _mapView.getContext());

        _text = new TextWidget("", textFormat);
        _text.setName("Tooltip Text");
        _text.setMargins(0f, 16f, 0f, 0f);
        _text.setVisible(false);
        teLayout.addWidget(_text);
        topEdge.addWidget(teLayout);
        topEdge.addOnWidgetSizeChangedListener(this);

        _widget = new MarkerIconWidget();
        _widget.setName("Tooltip Toggle");
        _widget.setMargins(0f, 32f, 32f, 0f);
        _widget.setVisible(false);
        topRight.addWidget(_widget);

        icon_lit = createIcon(ATAKUtilities.getResourceUri(
                R.drawable.hint_lit));
        icon_unlit = createIcon(ATAKUtilities.getResourceUri(
                R.drawable.hint));
        icon_blank = createIcon(ATAKUtilities.getResourceUri(
                R.drawable.blank));

        _widget.addOnClickListener(this);
        _instance = this;
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (_widget.getIcon() == icon_blank)
            return;
        if (_widget.getIcon() == icon_unlit) {
            _text.setVisible(true);
            _widget.setIcon(icon_lit);
            _prefs.edit().putBoolean("textContainer.textShowing", true).apply();
        } else {
            _text.setVisible(false);
            _widget.setIcon(icon_unlit);
            _prefs.edit().putBoolean("textContainer.textShowing", false)
                    .apply();
        }
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        if (displaying)
            updatePromptText();
    }

    private Icon createIcon(final String imageUri) {
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(xIconAnchor, yIconAnchor);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);

        builder.setSize(xSize, ySize);

        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

        return builder.build();
    }

    /**
     * Returns a TextContainer that displays at the top of the screen.
     * 
     * @return
     */
    synchronized public static TextContainer getInstance() {
        if (_instance == null)
            _instance = TextContainerCompat.createInstance();
        return _instance;
    }

    /**
     * Return a TextContainer that displays at the top of the screen.
     * 
     * @return
     */
    public static TextContainer getTopInstance() {
        return getInstance();
    }

    public void dispose() {
        if (_widget != null)
            _widget.removeOnClickListener(this);
        _instance = null;
    }

    private void setTextFormat(MapTextFormat textFormat) {
        this.textFormat = textFormat;
        _text.setTextFormat(textFormat);
    }

    public void displayPrompt(int textId) {
        this.displayPrompt(_mapView.getContext().getString(textId));
    }

    public void displayPrompt(final String prompt) {
        this.displayPrompt(prompt, DEFAULT_FORMAT, DEFAULT_COLOR);
    }

    public void displayPrompt(final CharSequence prompt) {
        this.displayPrompt(prompt, DEFAULT_FORMAT, DEFAULT_COLOR);
    }

    public void displayPrompt(final String prompt,
            final MapTextFormat textFormat) {
        this.displayPrompt(prompt, textFormat, DEFAULT_COLOR);
    }

    public void displayPrompt(final CharSequence prompt,
            final MapTextFormat textFormat) {
        this.displayPrompt(prompt, textFormat, DEFAULT_COLOR);
    }

    public void displayPrompt(final String prompt,
            final MapTextFormat textFormat, final int color) {
        setTextFormat(textFormat);
        this.color = color;
        displayPromptAtTop(prompt, true);
    }

    public void displayPrompt(final CharSequence prompt,
            final MapTextFormat textFormat, final int color) {
        setTextFormat(textFormat);
        this.color = color;
        displayPromptAtTop(prompt, true);
    }

    /** 
     * Allows a user to display a prompt, but force it independently from 
     * the user descision to show or hide the prompt.
     */
    public void displayPromptForceShow(final String prompt) {
        setTextFormat(DEFAULT_FORMAT);
        this.color = DEFAULT_COLOR;
        displayPromptAtTop(prompt, false);
        if (_widget.getIcon() != icon_lit) {
            _text.setVisible(true);
            _widget.setIcon(icon_lit);
        }
    }

    /**
     * Line-wrap a string based on its text format and available width
     * @param prompt Text prompt
     * @param fmt Text format (used for width calculations)
     * @param maxWidth Max width available
     * @return Line-wrapped string
     */
    protected static String wrap(String prompt, MapTextFormat fmt,
            float maxWidth) {
        // First check if the string even has to be wrapped
        float width = fmt.measureTextWidth(prompt);
        if (width <= maxWidth)
            return prompt;

        // String is already wrapped but not well-enough...
        // so we'll have to re-wrap it
        if (prompt.contains("\n"))
            prompt = prompt.replace("\n", " ");
        prompt = prompt.trim() + " ";

        width = 0;
        int lastWrap = 0, lastSpace = 0, lastSentence = 0;
        float sentenceWidth = 0;
        char lastChar = '\0';
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prompt.length(); i++) {
            char c = prompt.charAt(i);
            if (c == ' ') {
                // At each space check if the line needs to be wrapped
                float wordWidth = fmt.measureTextWidth(prompt.substring(
                        lastSpace, i));
                width += wordWidth;
                if (width > maxWidth) {
                    // Line exceeds max width - break line by space or period
                    String line;
                    if (lastSentence > lastWrap && lastSentence < lastSpace
                            && sentenceWidth > maxWidth / 4) {
                        // Break at last sentence instead of last word
                        lastSpace = lastSentence;
                        wordWidth = width - sentenceWidth;
                    }
                    line = prompt.substring(lastWrap, lastSpace);
                    sb.append(line);
                    sb.append("\n");
                    if (i == prompt.length() - 1)
                        sb.append(prompt, lastSpace + 1, i);
                    lastWrap = lastSpace + 1;
                    width = wordWidth;
                } else if (i == prompt.length() - 1)
                    sb.append(prompt, lastWrap, i);
                lastSpace = i;
                if (lastChar == '.') {
                    // Remember location and size of last sentence
                    sentenceWidth = width;
                    lastSentence = i;
                }
            }
            lastChar = c;
        }

        return sb.toString().trim();
    }

    /***
     * Displays a textual prompt in a standardized way. Should be used in particular to provide
     * feedback about the fact that a tool is currently active and about what the user can now do
     * and how they can do it.
     * 
     * @param prompt prompt to display
     * @param blink True to blink the widget icon when text is hidden
     */
    private synchronized void displayPromptAtTop(String prompt, boolean blink) {
        displaying = true;
        _prompt = prompt;
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (hint_view.getParent() == null) {
                    HintDialogHelper.showHint(_mapView.getContext(),
                            "On Screen Hints",
                            hint_view, "textContainer.osd");
                }
            }
        });

        Icon ico = getIcon();
        boolean showText = ico == icon_lit;
        _widget.setIcon(ico);
        _widget.setVisible(true);

        // Wrap text
        _text.setTextFormat(textFormat);
        _text.setColor(color);
        updatePromptText();
        _text.setVisible(showText);

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            b.putString("prompt", prompt);
            MetricsApi.record("hint", b);
        }

        // the user previously requested the text to show, so we will continue 
        // to show the text
        if (blink && !showText) {
            // only blink if the instructions are not in a showing state.
            Thread t = new Thread(this, TAG + "-Blink");
            t.start();
        }
    }

    protected synchronized void displayPromptAtTop(CharSequence prompt,
            boolean blink) {
        this.displayPromptAtTop(prompt == null ? "" : prompt.toString(), blink);
    }

    /**
     * Blink thread.
     */
    @Override
    public void run() {
        for (int i = 0; i < 3; ++i) {
            if (!displaying)
                break;
            try {
                Thread.sleep(175);
            } catch (InterruptedException ignore) {
            }
            _widget.setIcon(_widget.getIcon() != icon_blank ? icon_blank
                    : getIcon());
        }
        _widget.setIcon(getIcon());
    }

    protected Icon getIcon() {
        return _prefs.getBoolean("textContainer.textShowing", true)
                ? icon_lit
                : icon_unlit;
    }

    /***
     * Closes the prompt if it's currently displayed.
     * @param text Only close if this text is showing (null to ignore);
     */
    synchronized public void closePrompt(String text) {
        if (text == null || FileSystemUtils.isEquals(text, _prompt)) {
            displaying = false;
            _text.setVisible(false);
            _widget.setVisible(false);
        }
    }

    public void closePrompt() {
        closePrompt(null);
    }

    private void updatePromptText() {
        String wrapped = wrap(_prompt, textFormat,
                _text.getParent().getWidth() - 16f);
        _text.setText(wrapped);
    }
}
