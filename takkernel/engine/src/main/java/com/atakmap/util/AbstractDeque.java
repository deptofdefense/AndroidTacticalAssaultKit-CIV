package com.atakmap.util;

import java.util.AbstractCollection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class AbstractDeque<V> extends AbstractCollection<V> implements Deque<V> {

    /**
     * Returns <code>true</code> if the {@link Deque} has enough capacity to add
     * at least one more element, <code>false</code> otherwise.
     * 
     * <P>The default implementation always returns <code>true</code>.
     */
    protected boolean checkCapacity() {
        return true;
    }

    @Override
    public final boolean add(V arg0) {
        this.addLast(arg0);
        return true;
    }

    @Override
    public abstract void addFirst(V arg0);

    @Override
    public abstract void addLast(V arg0);

    @Override
    public V element() {
        return this.getFirst();
    }

    @Override
    public V getFirst() {
        if(this.isEmpty())
            throw new NoSuchElementException();
        else
            return this.peekFirst();
    }

    @Override
    public V getLast() {
        if(this.isEmpty())
            throw new NoSuchElementException();
        else
            return this.peekLast();
    }

    @Override
    public abstract Iterator<V> iterator();

    @Override
    public boolean offer(V arg0) {
        return this.offerLast(arg0);
    }

    @Override
    public boolean offerFirst(V arg0) {
        if(!this.checkCapacity())
            return false;
        this.addFirst(arg0);
        return true;
    }

    @Override
    public final boolean offerLast(V arg0) {
        if(!this.checkCapacity())
            return false;
        this.addLast(arg0);
        return true;
    }

    @Override
    public final V peek() {
        return this.peekFirst();
    }

    @Override
    public V peekFirst() {
        if(this.isEmpty())
            return null;
        return this.iterator().next();
    }

    @Override
    public V peekLast() {
        if(this.isEmpty())
            return null;
        return this.descendingIterator().next();
    }

    @Override
    public final V poll() {
        return this.pollFirst();
    }

    @Override
    public V pollFirst() {
        if(this.isEmpty())
            return null;
        Iterator<V> iter = this.iterator();
        final V retval = iter.next();
        iter.remove();
        return retval;
    }

    @Override
    public V pollLast() {
        if(this.isEmpty())
            return null;
        Iterator<V> iter = this.descendingIterator();
        final V retval = iter.next();
        iter.remove();
        return retval;
    }

    @Override
    public final V pop() {
        return this.removeFirst();
    }

    @Override
    public final void push(V arg0) {
        this.addFirst(arg0);
    }

    @Override
    public final V remove() {
        return this.removeFirst();
    }

    @Override
    public final boolean remove(Object arg0) {
        return this.removeFirstOccurrence(arg0);
    }

    @Override
    public V removeFirst() {
        Iterator<V> iter = this.iterator();
        final V retval = iter.next();
        iter.remove();
        return retval;
    }

    @Override
    public boolean removeFirstOccurrence(Object arg0) {
        Iterator<V> iter = this.iterator();
        if(arg0 == null) {
            while(iter.hasNext()) {
                if(iter.next() == null) {
                    iter.remove();
                    return true;
                }
            }
        } else {
            while(iter.hasNext()) {
                if(arg0.equals(iter.next())) {
                    iter.remove();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public V removeLast() {
        Iterator<V> iter = this.descendingIterator();
        final V retval = iter.next();
        iter.remove();
        return retval;
    }

    @Override
    public boolean removeLastOccurrence(Object arg0) {
        Iterator<V> iter = this.descendingIterator();
        if(arg0 == null) {
            while(iter.hasNext()) {
                if(iter.next() == null) {
                    iter.remove();
                    return true;
                }
            }
        } else {
            while(iter.hasNext()) {
                if(arg0.equals(iter.next())) {
                    iter.remove();
                    return true;
                }
            }
        }
        return false;
    }
}
