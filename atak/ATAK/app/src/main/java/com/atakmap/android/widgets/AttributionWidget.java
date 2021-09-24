
package com.atakmap.android.widgets;

import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import android.util.Pair;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.AttributionControl;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.util.Visitor;

public final class AttributionWidget extends TextWidget implements
        MapRenderer.OnControlsChangedListener,
        AttributionControl.OnAttributionUpdatedListener,
        Layer.OnLayerVisibleChangedListener {

    private final Map<AttributionControl, Set<Pair<String, String>>> ctrlToAttributions;
    private final Map<AttributionControl, Layer2> ctrlToLayer;
    private final SortedMap<String, SortedSet<String>> attributionText;
    private boolean expanded;

    public AttributionWidget(MapRenderer contentRenderer) {
        super("", MapView.getDefaultTextFormat());

        this.ctrlToAttributions = new IdentityHashMap<>();
        this.ctrlToLayer = new IdentityHashMap<>();
        this.attributionText = new TreeMap<>();

        contentRenderer.addOnControlsChangedListener(this);

        // visit on all existing controls
        contentRenderer
                .visitControls(
                        new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                            @Override
                            public void visit(
                                    Iterator<Map.Entry<Layer2, Collection<MapControl>>> iter) {
                                while (iter.hasNext()) {
                                    Map.Entry<Layer2, Collection<MapControl>> entry = iter
                                            .next();
                                    for (MapControl ctrl : entry.getValue()) {
                                        if (ctrl instanceof AttributionControl)
                                            onControlRegistered(entry.getKey(),
                                                    ctrl);
                                    }
                                }
                            }
                        });

        final MapTextFormat defaultFmt = AtakMapView.getDefaultTextFormat();
        this.setTextFormat(new MapTextFormat(defaultFmt.getTypeface(), true,
                defaultFmt.getFontSize()));
        this.setVisible(true);
        this.setColor(0x77FFFFFF);
        this.setBackground(TextWidget.TRANSLUCENT);
        this.expanded = false;
    }

    public synchronized boolean isExpanded() {
        return this.expanded;
    }

    public synchronized void setExpanded(boolean expanded) {
        this.expanded = expanded;
        this.rebuildAttributionListNoSync();
    }

    @Override
    public void onControlRegistered(Layer2 layer, MapControl ctrl) {
        if (!(ctrl instanceof AttributionControl))
            return;
        AttributionControl attrCtrl = (AttributionControl) ctrl;
        attrCtrl.addOnAttributionUpdatedListener(this);

        if (layer != null)
            layer.addOnLayerVisibleChangedListener(this);

        synchronized (this) {
            this.ctrlToLayer.put(attrCtrl, layer);
            this.onAttributionUpdated(attrCtrl);
        }
    }

    @Override
    public void onControlUnregistered(final Layer2 layer,
            final MapControl ctrl) {
        if (!(ctrl instanceof AttributionControl))
            return;
        AttributionControl attrCtrl = (AttributionControl) ctrl;
        attrCtrl.removeOnAttributionUpdatedListener(this);

        if (layer != null)
            layer.removeOnLayerVisibleChangedListener(this);

        synchronized (this) {
            this.ctrlToAttributions.remove(ctrl);
            this.ctrlToLayer.remove(ctrl);
            this.rebuildAttributionListNoSync();
        }
    }

    private void rebuildAttributionListNoSync() {
        // rebuild attribution list
        this.attributionText.clear();
        for (Map.Entry<AttributionControl, Set<Pair<String, String>>> ctrlAttrEntry : this.ctrlToAttributions
                .entrySet()) {
            Layer2 layer = this.ctrlToLayer.get(ctrlAttrEntry.getKey());
            if (!layer.isVisible())
                continue;
            for (Pair<String, String> attr : ctrlAttrEntry.getValue()) {
                SortedSet<String> texts = this.attributionText.get(attr.second);
                if (texts == null)
                    this.attributionText.put(attr.second,
                            texts = new TreeSet<>());
                texts.add(attr.first);
            }
        }

        // rebuild text
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, SortedSet<String>> entry : this.attributionText
                .entrySet()) {

            Iterator<String> iter = entry.getValue().iterator();
            while (iter.hasNext()) {
                text.append(iter.next());
                if (iter.hasNext())
                    text.append(", ");
            }
            text.append(' ');
            text.append(entry.getKey());
            text.append('\n');
        }

        // update text
        final String s = wrapText(this.getTextFormat(), text.toString(),
                MapView.getMapView().getWidth() / 2, expanded ? 0 : 3,
                "...Click for More");
        this.setText(s);
        this.setSize(this.getTextFormat().measureTextWidth(s), this
                .getTextFormat().measureTextHeight(s));
        this.setPoint(MapView.getMapView().getWidth() / 4f, MapView.getMapView()
                .getHeight() - this.getHeight());
        this.setBackground(this.expanded ? TextWidget.TRANSLUCENT_BLACK
                : TextWidget.TRANSLUCENT);
    }

    @Override
    public synchronized void onAttributionUpdated(AttributionControl control) {
        this.ctrlToAttributions.put(control, new HashSet<>(
                control.getContentAttribution()));

        this.rebuildAttributionListNoSync();
    }

    private static String wrapText(MapTextFormat fmt, String src, int width,
            int maxLines, String onTruncated) {
        int numLines = 0;
        boolean truncated = false;

        // XXX - consider alignment modes (left, right, center)

        // XXX - implementation could be much better in terms of whitespace
        //       handling
        StringBuilder retval = new StringBuilder();
        StringBuilder word = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (c == '\n' || c == ' ' || c == '\t') {
                if (word.length() > 0) {
                    if (line.length() == 0) {
                        StringBuilder swap = line;
                        line = word;
                        word = swap;
                    } else if (fmt
                            .measureTextWidth(line + " " + word) > width) {
                        if (line.length() > 0) {
                            retval.append(line);
                            line.delete(0, line.length());
                            numLines++;
                            if (numLines == maxLines) {
                                truncated = i < src.length() - 1;
                                break;
                            }
                            retval.append('\n');
                        }

                        StringBuilder swap = line;
                        line = word;
                        word = swap;
                    } else {
                        // the word will fit the current line, append it
                        line.append(' ');
                        line.append(word);
                        word.delete(0, word.length());
                    }
                }

                // a newline has occurred, flush content
                if (c == '\n') {
                    if (line.length() > 0) {
                        retval.append(line);
                        line.delete(0, line.length());
                        numLines++;
                        if (numLines == maxLines) {
                            truncated = i < src.length() - 1;
                            break;
                        }
                    }
                    retval.append('\n');
                }
            } else {
                word.append(c);
            }
        }

        if (maxLines == 0 || numLines < maxLines) {
            final int lineLength = line.length();

            // if there's anything in the line buffer, append it
            if (lineLength > 0)
                retval.append(line);

            // if there's anything in the word buffer append it
            do {
                if (word.length() > 0) {
                    // if there was something in the line buffer, append either a space
                    // or newline as appropriate
                    if (lineLength > 0) {
                        if (fmt.measureTextWidth(line + " " + word) > width) {
                            numLines++;
                            if (numLines == maxLines) {
                                truncated = true;
                                break;
                            }
                            retval.append('\n');
                        } else {
                            retval.append(' ');
                        }
                    }

                    retval.append(word);
                }
            } while (false);
        }

        if (truncated) {
            final int lastNewlineIndex = retval.lastIndexOf("\n");
            if (lastNewlineIndex > 0)
                retval.delete(lastNewlineIndex, retval.length());
            retval.append('\n');
            retval.append(onTruncated);
        }

        return retval.toString();
    }

    @Override
    public void onLayerVisibleChanged(Layer layer) {
        this.rebuildAttributionListNoSync();
    }
}
