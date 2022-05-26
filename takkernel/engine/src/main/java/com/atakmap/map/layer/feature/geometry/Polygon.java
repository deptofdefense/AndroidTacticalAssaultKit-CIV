package com.atakmap.map.layer.feature.geometry;

import com.atakmap.interop.Pointer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public final class Polygon extends Geometry {
    
    LineString exteriorRing;
    private InteriorRings interiorRings;
    
    public Polygon(int dimension) {
        this(Polygon_create(dimension));
    }

    public Polygon(LineString exteriorRing) {
        this(Polygon_create(exteriorRing.getDimension()));
        this.addRing(exteriorRing);
    }
    
    public Polygon(LineString exteriorRing, Collection<LineString> interiorRings) {
        this(exteriorRing);

        for(LineString ring : interiorRings)
            this.addRing(ring);
    }

    Polygon(Pointer pointer) {
        super(pointer);

        this.interiorRings = new InteriorRings();
    }
    
    public void addRing(LineString ring) {
        if(ring == null)
            throw new NullPointerException();

        if(this.exteriorRing == null) {
            // there's no interior ring
            Pointer ringPtr;
            this.rwlock.acquireRead();
            try {
                // copy the specified to the exterior ring content and capture
                // the shared_ptr
                ringPtr = Polygon_setExteriorRing(this.pointer, ring.pointer);
            } finally {
                this.rwlock.releaseRead();
            }
            // update the data for the ring
            ring.reset(this, ringPtr, false);
            // assign the instance
            this.exteriorRing = ring;
        } else if(ring.pointer.equals(this.exteriorRing.pointer)) {
            // XXX - updated above logic for pointer equals check due to
            //       running into situation where 'LineString.equals' was
            //       getting tripped on large PFPS ingest. Need to narrow
            //       down input and investigate further in the future.
            throw new IllegalArgumentException();
        } else {
            this.interiorRings.add(ring);
        }
    }
    
    public void clear() {
        if (this.exteriorRing != null) {
            this.exteriorRing.orphan();
            this.exteriorRing = null;
        }
        this.rwlock.acquireRead();
        try {
            Polygon_clear(this.pointer);
            this.interiorRings.cache.clear();
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public LineString getExteriorRing() {
        return this.exteriorRing;
    }

    public Collection<LineString> getInteriorRings() {
        return this.interiorRings;
    }

    private class InteriorRings implements Collection<LineString> {

        private Map<Pointer, WeakReference<LineString>> cache = new HashMap<Pointer, WeakReference<LineString>>();

        @Override
        public boolean add(LineString object) {
            // already contains
            if(this.cache.containsKey(object.pointer))
                return false;
            if(object.owner != null)
                throw new IllegalStateException();

            Polygon.this.rwlock.acquireRead();
            try {
                Pointer ptr = Polygon_addInteriorRing(Polygon.this.pointer, object.pointer);

                // update the data for the ring
                object.reset(Polygon.this, ptr, false);

                // cache a reference
                this.cache.put(object.pointer, new WeakReference<LineString>(object));
            } finally {
                Polygon.this.rwlock.releaseRead();
            }
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends LineString> collection) {
            // XXX - more efficient implementation;
            boolean retval = false;
            for(LineString ring : collection)
                retval |= this.add(ring);
            return retval;
        }

        @Override
        public void clear() {
            Polygon.this.rwlock.acquireRead();
            try {
                Polygon_clearInteriorRings(Polygon.this.pointer);
                for(WeakReference<LineString> ringRef : cache.values()) {
                    LineString ring = ringRef.get();
                    if(ring != null)
                        ring.orphan();
                }
                cache.clear();
            } finally {
                Polygon.this.rwlock.releaseRead();
            }
        }

        @Override
        public boolean contains(Object object) {
            if(object == null)
                return false;
            if(!(object instanceof LineString))
                return false;
            final LineString ring = (LineString)object;
            return this.cache.containsKey(ring.pointer);
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            boolean retval = true;
            for(Object o : collection)
                retval &= this.contains(o);
            return retval;
        }

        @Override
        public boolean isEmpty() {
            return (this.size() == 0);
        }

        @Override
        public java.util.Iterator<LineString> iterator() {
            return new Iterator();
        }

        @Override
        public boolean remove(Object object) {
            if(object == null || !(object instanceof LineString))
                return false;

            LineString ring = (LineString)object;
            if(!Polygon_removeInteriorRing(Polygon.this.pointer, ring.pointer))
                return false;

            ring.orphan();
            return true;
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            // XXX - more efficient implementation
            boolean retval = false;
            for(Object o : collection)
                retval |= this.remove(o);
            return retval;
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            // XXX - more efficient implementation
            boolean retval = false;
            java.util.Iterator<LineString> iter = this.iterator();
            while(iter.hasNext()) {
                if(!collection.contains(iter.next())) {
                    iter.remove();
                    retval = true;
                }
            }
            return retval;
        }

        @Override
        public int size() {
            Polygon.this.rwlock.acquireRead();
            try {
                return Polygon_getNumInteriorRings(Polygon.this.pointer);
            } finally {
                Polygon.this.rwlock.releaseRead();
            }
        }

        @Override
        public Object[] toArray() {
            return this.toArray(new Object[0]);
        }

        @Override
        public <T> T[] toArray(T[] array) {
            Polygon.this.rwlock.acquireRead();
            try {
                final int size = this.size();
                if(array.length < size)
                    array = (T[]) Array.newInstance(array.getClass().getComponentType(), size);

                Iterator it = new Iterator();
                int i = 0;
                for(i = 0; i < size; i++)
                    array[i] = (T)it.next();
                for( ; i < array.length; i++)
                    array[i] = null;
                return array;
            } finally {
                Polygon.this.rwlock.releaseRead();
            }
        }

        class Iterator implements java.util.Iterator<LineString> {

            int idx = 0;
            int limit = InteriorRings.this.size();
            LineString row;

            @Override
            public boolean hasNext() {
                return (this.idx < this.limit);
            }

            @Override
            public LineString next() {
                if(idx == limit)
                    throw new NoSuchElementException();
                Pointer ptr;
                Polygon.this.rwlock.acquireRead();
                try {
                    ptr = Polygon_getInteriorRing(Polygon.this.pointer, idx++);
                } finally {
                    Polygon.this.rwlock.releaseRead();
                }
                WeakReference<LineString> ref = InteriorRings.this.cache.get(ptr);
                this.row = dereference(ref);
                if(this.row == null) {
                    this.row = new LineString(ptr);
                    this.row.owner = Polygon.this;
                    InteriorRings.this.cache.put(ptr, new WeakReference<LineString>(this.row));
                }
                return this.row;
            }

            @Override
            public void remove() {
                InteriorRings.this.remove(this.row);
            }
        }
    }

    static <T extends Geometry> T dereference(WeakReference<T> ref) {
        if(ref == null)
            return null;
        return ref.get();
    }
}
