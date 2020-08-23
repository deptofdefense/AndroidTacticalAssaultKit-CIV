
package com.atakmap.android.util;

import androidx.annotation.NonNull;

import com.atakmap.lang.Objects;
import com.atakmap.util.AbstractDeque;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public final class LinkedHashMap2<K, V> implements Map<K, V> {

    private final HashMap<K, BidirectionalNode> nodes;
    private BidirectionalNode head;
    private BidirectionalNode tail;

    private final boolean accessOrder;

    public LinkedHashMap2() {
        this(16, 0.75f, false);
    }

    public LinkedHashMap2(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    public LinkedHashMap2(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, false);
    }

    public LinkedHashMap2(int initialCapacity, float loadFactor,
            boolean accessOrder) {
        this.nodes = new HashMap<>(initialCapacity,
                loadFactor);

        this.accessOrder = accessOrder;
    }

    public LinkedHashMap2(Map<? extends K, ? extends V> m) {
        this(m.size(), 0.75f, false);

        this.putAll(m);
    }

    public K firstKey() {
        if (this.size() < 1)
            throw new NoSuchElementException();
        return this.head.key;
    }

    public K lastKey() {
        if (this.size() < 1)
            throw new NoSuchElementException();
        return this.tail.key;
    }

    public K nextKey(K key) {
        BidirectionalNode node = this.getImpl(key);
        if (node == null)
            throw new NoSuchElementException();
        if (node.next == null)
            return null;
        return node.next.key;
    }

    public K previousKey(K key) {
        BidirectionalNode node = this.getImpl(key);
        if (node == null)
            throw new NoSuchElementException();
        if (node.previous == null)
            return null;
        return node.previous.key;
    }

    public Set<K> descendingKeySet() {
        return new KeySet(false);
    }

    public Set<Map.Entry<K, V>> descendingEntrySet() {
        return new EntrySet(false);
    }

    public Collection<V> descendingValues() {
        return new Values(false);
    }

    /**************************************************************************/
    // Map

    @Override
    public void clear() {
        this.nodes.clear();
        this.head = null;
        this.tail = null;
    }

    @Override
    public boolean containsKey(Object arg0) {
        return this.nodes.containsKey(arg0);
    }

    @Override
    public boolean containsValue(Object arg0) {
        BidirectionalNode iter = this.head;
        while (iter != null) {
            if (Objects.equals(iter.value, arg0))
                return true;
            iter = iter.next;
        }
        return false;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet(true);
    }

    @Override
    public V get(Object arg0) {
        BidirectionalNode retval = this.getImpl(arg0);
        if (retval == null)
            return null;
        if (this.accessOrder && this.tail != retval) {
            retval.unlink();
            if (this.head == retval)
                this.head = retval.next;
            retval.link(this.tail, null);
            this.tail = retval;
        }
        return retval.value;
    }

    BidirectionalNode getImpl(Object arg) {
        return this.nodes.get(arg);
    }

    @Override
    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return new KeySet(true);
    }

    @Override
    public V put(K arg0, V arg1) {
        final V retval = this.remove(arg0);

        BidirectionalNode node = new BidirectionalNode(this.tail, arg0, arg1);
        this.nodes.put(arg0, node);
        if (this.head == null)
            this.head = node;
        this.tail = node;
        return retval;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> arg0) {
        for (Map.Entry<? extends K, ? extends V> entry : arg0.entrySet())
            this.put(entry.getKey(), entry.getValue());
    }

    @Override
    public V remove(Object arg0) {
        BidirectionalNode node = this.removeImpl(arg0);
        if (node == null)
            return null;
        return node.value;
    }

    BidirectionalNode removeImpl(Object arg0) {
        BidirectionalNode node = this.nodes.remove(arg0);
        if (node == null)
            return null;
        node.unlink();
        if (node == this.head) {
            this.head = node.next;
        } else if (node == this.tail) {
            this.tail = node.previous;
        }
        return node;
    }

    @Override
    public int size() {
        return this.nodes.size();
    }

    @Override
    public Collection<V> values() {
        return new Values(true);
    }

    /**************************************************************************/

    private class BidirectionalNode {
        public final K key;
        public final V value;
        public BidirectionalNode previous;
        public BidirectionalNode next;

        BidirectionalNode(BidirectionalNode previous, K key, V value) {
            this(previous, null, key, value);
        }

        BidirectionalNode(BidirectionalNode previous, BidirectionalNode next,
                K key, V value) {
            this.key = key;
            this.value = value;

            this.link(previous, next);
        }

        void unlink() {
            if (this.previous != null)
                this.previous.next = this.next;
            if (this.next != null)
                this.next.previous = this.previous;
        }

        void link(BidirectionalNode previous, BidirectionalNode next) {
            this.previous = previous;
            this.next = next;

            if (this.previous != null)
                this.previous.next = this;
            if (this.next != null)
                this.next.previous = this;
        }
    }

    private abstract class NodeIterator implements Iterator<BidirectionalNode> {

        BidirectionalNode impl;
        final BidirectionalNode start;
        K lastKey;

        NodeIterator(BidirectionalNode start) {
            this.impl = start;
            this.start = start;
            this.lastKey = null;
        }

        @Override
        public final boolean hasNext() {
            return this.impl != null;
        }

        @Override
        public final BidirectionalNode next() {
            if (this.impl == null)
                throw new NoSuchElementException();
            final BidirectionalNode retval = this.impl;
            this.lastKey = this.impl.key;
            this.impl = this.nextImpl();
            return retval;
        }

        protected abstract BidirectionalNode nextImpl();

        @Override
        public final void remove() {
            if (this.impl == this.start)
                throw new NoSuchElementException();
            LinkedHashMap2.this.remove(this.lastKey);
        }
    }

    final class AscNodeIterator extends NodeIterator {
        AscNodeIterator() {
            super(LinkedHashMap2.this.head);
        }

        @Override
        public BidirectionalNode nextImpl() {
            return this.impl.next;
        }
    }

    final class DescNodeIterator extends NodeIterator {
        DescNodeIterator() {
            super(LinkedHashMap2.this.tail);
        }

        @Override
        public BidirectionalNode nextImpl() {
            return this.impl.previous;
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        final boolean ascending;

        EntrySet(boolean asc) {
            this.ascending = asc;
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K, V> entry = (Map.Entry<K, V>) o;
            V val = LinkedHashMap2.this.get(entry.getKey());
            if (val == null) {
                if (entry.getValue() != null)
                    return false;
            } else {
                if (!val.equals(entry.getValue()))
                    return false;
            }

            return (LinkedHashMap2.this.remove(o) != null);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean retval = false;
            for (Object o : c)
                retval |= this.remove(o);
            return retval;
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K, V> entry = (Map.Entry<K, V>) o;
            V val = LinkedHashMap2.this.get(entry.getKey());
            if (val == null) {
                if (entry.getValue() != null)
                    return false;
            } else {
                if (!val.equals(entry.getValue()))
                    return false;
            }

            return true;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntriesIterator(this.ascending);
        }

        @Override
        public int size() {
            return LinkedHashMap2.this.size();
        }

        @Override
        public void clear() {
            LinkedHashMap2.this.clear();
        }
    }

    final class KeysIterator implements Iterator<K> {
        final NodeIterator impl;

        KeysIterator(boolean asc) {
            if (asc)
                this.impl = new AscNodeIterator();
            else
                this.impl = new DescNodeIterator();
        }

        @Override
        public boolean hasNext() {
            return this.impl.hasNext();
        }

        @Override
        public K next() {
            return this.impl.next().key;
        }

        @Override
        public void remove() {
            this.impl.remove();
        }
    }

    final class KeySet extends AbstractSet<K> {

        final boolean ascending;

        KeySet(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public boolean remove(Object o) {
            return (LinkedHashMap2.this.remove(o) != null);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean retval = false;
            for (Object o : c)
                retval |= this.remove(o);
            return retval;
        }

        @Override
        public boolean contains(Object o) {
            return LinkedHashMap2.this.containsKey(o);
        }

        @Override
        public Iterator<K> iterator() {
            return new KeysIterator(this.ascending);
        }

        @Override
        public int size() {
            return LinkedHashMap2.this.size();
        }

        @Override
        public void clear() {
            LinkedHashMap2.this.clear();
        }
    }

    final class EntriesIterator implements Iterator<Map.Entry<K, V>> {
        final NodeIterator impl;

        EntriesIterator(boolean asc) {
            if (asc)
                this.impl = new AscNodeIterator();
            else
                this.impl = new DescNodeIterator();
        }

        @Override
        public boolean hasNext() {
            return this.impl.hasNext();
        }

        @Override
        public Map.Entry<K, V> next() {
            BidirectionalNode node = this.impl.next();
            return new AbstractMap.SimpleEntry<>(node.key, node.value);
        }

        @Override
        public void remove() {
            this.impl.remove();
        }
    }

    private class Values extends AbstractDeque<V> {

        final boolean ascending;

        Values(boolean asc) {
            this.ascending = asc;
        }

        @NonNull
        @Override
        public Iterator<V> descendingIterator() {
            return new ValuesIterator(!this.ascending);
        }

        @Override
        public void addFirst(V arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addLast(V arg0) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Iterator<V> iterator() {
            return new ValuesIterator(this.ascending);
        }

        @Override
        public int size() {
            return LinkedHashMap2.this.size();
        }
    }

    final class ValuesIterator implements Iterator<V> {

        final NodeIterator impl;

        ValuesIterator(boolean asc) {
            if (asc)
                this.impl = new AscNodeIterator();
            else
                this.impl = new DescNodeIterator();
        }

        @Override
        public boolean hasNext() {
            return this.impl.hasNext();
        }

        @Override
        public V next() {
            return this.impl.next().value;
        }

        @Override
        public void remove() {
            this.impl.remove();
        }
    }
}
