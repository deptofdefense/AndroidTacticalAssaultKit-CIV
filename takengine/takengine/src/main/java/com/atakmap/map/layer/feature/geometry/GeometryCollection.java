package com.atakmap.map.layer.feature.geometry;

import com.atakmap.interop.Pointer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public final class GeometryCollection extends Geometry {

    private Children geometries;

    public GeometryCollection(int dimension) {
        this(GeometryCollection_create(dimension));
    }

    GeometryCollection(Pointer pointer) {
        super(pointer);

        this.geometries = new Children();
    }
    public Geometry addGeometry(Geometry geometry) {
        return this.geometries.addImpl(geometry);
    }
    
    public void removeGeometry(Geometry geometry) {
        this.geometries.remove(geometry);
    }

    public Collection<Geometry> getGeometries() {
        return this.geometries;
    }

    private class Children implements Collection<Geometry> {

        private Map<Pointer, WeakReference<Geometry>> cache = new HashMap<Pointer, WeakReference<Geometry>>();

        @Override
        public boolean add(Geometry object) {
            return (addImpl(object) != object);
        }

        Geometry addImpl(Geometry object) {
            // already contains
            if(!this.cache.containsKey(object.pointer)) {
                GeometryCollection.this.rwlock.acquireRead();
                try {
                    Pointer ptr = GeometryCollection_add(GeometryCollection.this.pointer, object.pointer);
                    object = create(ptr, this);
                    object.owner = GeometryCollection.this;

                    // cache a reference
                    this.cache.put(object.pointer, new WeakReference<Geometry>(object));
                } finally {
                    GeometryCollection.this.rwlock.releaseRead();
                }
            }
            return object;
        }

        @Override
        public boolean addAll(Collection<? extends Geometry> collection) {
            // XXX - more efficient implementation;
            boolean retval = false;
            for(Geometry g : collection)
                retval |= this.add(g);
            return retval;
        }

        @Override
        public void clear() {
            GeometryCollection.this.rwlock.acquireRead();
            try {
                GeometryCollection_clear(GeometryCollection.this.pointer);
                for(WeakReference<Geometry> ref : cache.values()) {
                    Geometry g = ref.get();
                    if(g != null)
                        g.orphan();
                }
                cache.clear();
            } finally {
                GeometryCollection.this.rwlock.releaseRead();
            }
        }

        @Override
        public boolean contains(Object object) {
            if(object == null)
                return false;
            if(!(object instanceof Geometry))
                return false;
            final Geometry g = (Geometry)object;
            return this.cache.containsKey(g.pointer);
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
        public java.util.Iterator<Geometry> iterator() {
            return new Iterator();
        }

        @Override
        public boolean remove(Object object) {
            if(object == null || !(object instanceof Geometry))
                return false;

            Geometry g = (Geometry)object;
            if(!GeometryCollection_remove(GeometryCollection.this.pointer, g.pointer))
                return false;

            g.orphan();
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
            java.util.Iterator<Geometry> iter = this.iterator();
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
            GeometryCollection.this.rwlock.acquireRead();
            try {
                return GeometryCollection_getNumChildren(GeometryCollection.this.pointer);
            } finally {
                GeometryCollection.this.rwlock.releaseRead();
            }
        }

        @Override
        public Object[] toArray() {
            return this.toArray(new Object[0]);
        }

        @Override
        public <T> T[] toArray(T[] array) {
            GeometryCollection.this.rwlock.acquireRead();
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
                GeometryCollection.this.rwlock.releaseRead();
            }
        }

        class Iterator implements java.util.Iterator<Geometry> {

            int idx = 0;
            int limit = Children.this.size();
            Geometry row;

            @Override
            public boolean hasNext() {
                return (this.idx < this.limit);
            }

            @Override
            public Geometry next() {
                if(idx == limit)
                    throw new NoSuchElementException();
                Pointer ptr;
                GeometryCollection.this.rwlock.acquireRead();
                try {
                    ptr = GeometryCollection_getChild(GeometryCollection.this.pointer, idx++);
                } finally {
                    GeometryCollection.this.rwlock.releaseRead();
                }
                WeakReference<Geometry> ref = Children.this.cache.get(ptr);
                this.row = dereference(ref);
                if(this.row == null) {
                    this.row = create(ptr, GeometryCollection.this);

                    this.row.owner = GeometryCollection.this;
                    Children.this.cache.put(ptr, new WeakReference<Geometry>(this.row));
                }
                return this.row;
            }

            @Override
            public void remove() {
                Children.this.remove(this.row);
            }
        }
    }

    static <T extends Geometry> T dereference(WeakReference<T> ref) {
        if(ref == null)
            return null;
        return ref.get();
    }
}
