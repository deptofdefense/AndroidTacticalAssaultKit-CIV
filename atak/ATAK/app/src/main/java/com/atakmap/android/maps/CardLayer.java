
package com.atakmap.android.maps;

import java.util.LinkedList;
import java.util.List;

import com.atakmap.android.util.LinkedHashMap2;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.ProxyLayer;

/**
 * Implementation of a set of cards that can be cycled.
 */
public class CardLayer extends ProxyLayer {

    protected final LinkedHashMap2<String, Layer> cards = new LinkedHashMap2<>();

    public CardLayer(final String name) {
        super(name);
    }

    /**
     * Add a layer to the current deck of cards.
     * @param layer the layer.
     */
    public synchronized void add(final Layer layer) {
        this.cards.put(layer.getName(), layer);
        if (this.cards.size() == 1)
            this.set(layer);
    }

    /**
     * Remove a layer from the current deck of cards.
     * @param layer the layer.
     */
    public synchronized void remove(final Layer layer) {
        if (this.cards.values().remove(layer)) {
            if (this.get() == layer)
                this.set(null);
        }
    }

    /**
     * Move to the first card in the deck.
     */
    public synchronized void first() {
        if (this.cards.size() < 1)
            return;
        this.show(this.cards.firstKey());
    }

    /**
     * Move to the last card in the deck.
     */
    public synchronized void last() {
        if (this.cards.size() < 1)
            return;
        this.show(this.cards.lastKey());
    }

    /**
     * Move to the previous card in the deck.
     */
    public synchronized void previous() {
        if (this.cards.size() < 1)
            return;
        Layer current = this.get();
        String previous = null;
        if (current != null)
            previous = this.cards.previousKey(current.getName());
        if (previous == null)
            previous = this.cards.lastKey();
        this.show(previous);
    }

    /**
     * Move to the next card in the deck.
     */
    public synchronized void next() {
        if (this.cards.size() < 1)
            return;
        Layer current = this.get();
        String next = null;
        if (current != null)
            next = this.cards.nextKey(current.getName());
        if (next == null)
            next = this.cards.firstKey();
        this.show(next);
    }

    /**
     * Show a specific card via the name.
     * @param name the name to show, if the name is not valid, no change is made.
     */
    public synchronized void show(final String name) {
        Layer card = this.cards.get(name);
        if (card == null || card == this.subject)
            return;
        this.set(card);
    }

    /**
     * Returns a copied list of all of the cards.
     */
    public synchronized List<Layer> getLayers() {
        return new LinkedList<>(this.cards.values());
    }
}
