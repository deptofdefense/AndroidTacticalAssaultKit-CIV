package com.atakmap.map.layer.feature.cursor;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.util.CascadingComparator;

public class MultiplexingFeatureCursor implements FeatureCursor {

    private final static Comparator<FeatureCursor> FID_COMPARATOR = new Comparator<FeatureCursor>() {
        @Override
        public int compare(FeatureCursor arg0, FeatureCursor arg1) {
            if(arg0.getId() < arg1.getId())
                return -1;
            else if(arg0.getId() > arg1.getId())
                return 1;
            else if(arg0.getVersion() < arg1.getVersion())
                return -1;
            else if(arg0.getVersion() > arg1.getVersion())
                return 1;
            else
                return 0;
        }
    };
    
    private final static Comparator<FeatureCursor> FSID_COMPARATOR = new Comparator<FeatureCursor>() {
        @Override
        public int compare(FeatureCursor f0, FeatureCursor f1) {
            final long arg0 = f0.getFsid();
            final long arg1 = f1.getFsid();
            if(arg0 < arg1)
                return -1;
            else if(arg0 > arg1)
                return 1;
            else
                return 0;
        }
    };
    
    private final static Comparator<FeatureCursor> FEATURE_NAME_COMPARATOR = new Comparator<FeatureCursor>() {
        @Override
        public int compare(FeatureCursor arg0, FeatureCursor arg1) {
            if(arg0.getName() == null && arg1.getName() != null)
                return -1;
            else if(arg0.getName() != null && arg1.getName() == null)
                return 1;
            else if(arg0.getName() == null && arg1.getName() == null)
                return 0;
            else
                return arg0.getName().compareToIgnoreCase(arg1.getName());
        }
    };
    
    private Collection<FeatureCursor> cursors;
    private Collection<FeatureCursor> invalid;
    private SortedSet<FeatureCursor> pendingResults;
    private FeatureCursor current;

    public MultiplexingFeatureCursor(Collection<FeatureCursor> cursors, Collection<FeatureDataStore.FeatureQueryParameters.Order> sort) {        
        this.cursors = cursors;
        this.current = null;
        this.invalid = new LinkedList<FeatureCursor>(this.cursors);
        
        Comparator<FeatureCursor> comp;
        if(sort != null) {
            LinkedList<Comparator<FeatureCursor>> comparators = new LinkedList<Comparator<FeatureCursor>>();
            for(FeatureDataStore.FeatureQueryParameters.Order order : sort) {
                comp = orderToComparator(order);
                if(comp != null)
                    comparators.add(comp);
            }
            
            if(comparators.isEmpty())
                comp = FID_COMPARATOR;
            else if(comparators.size() == 1)
                comp = comparators.getFirst();
            else
                comp = new CascadingComparator<FeatureCursor>(comparators);
        } else {
            comp = FID_COMPARATOR;
        }

        this.pendingResults = new TreeSet<FeatureCursor>(comp);        
    }
    
    @Override
    public boolean moveToNext() {
        // update entries for any cursors marked 'invalid'
        for(FeatureCursor cursor : this.invalid) {
            if(cursor.moveToNext())
                this.pendingResults.add(cursor);
        }
        // all cursors are now valid
        this.invalid.clear();
        
        // if there are no pending results, we've exhausted the input
        if(this.pendingResults.isEmpty())
            return false;
        
        // remove the cursor pointing at the frame with the highest
        // resolution (numerically lowest GSD value)
        FeatureCursor next =  this.pendingResults.first();
        this.pendingResults.remove(next);
        // now removed, we want to re-evaluate the next call to 'moveToNext'
        // so place it in the invalid list
        this.invalid.add(next);
        // reset our current pointer
        this.current = next;
        
        return true;
    }

    @Override
    public void close() {
        for(FeatureCursor cursor : this.cursors)
            cursor.close();
        this.cursors.clear();
        
        this.invalid.clear();
        this.pendingResults.clear();
    }

    @Override
    public boolean isClosed() {
        return this.cursors.isEmpty();
    }

    @Override
    public Object getRawGeometry() {
        return this.current.getRawGeometry();
    }

    @Override
    public int getGeomCoding() {
        return this.current.getGeomCoding();
    }

    @Override
    public String getName() {
        return this.current.getName();
    }

    @Override
    public int getStyleCoding() {
        return this.current.getStyleCoding();
    }

    @Override
    public Object getRawStyle() {
        return this.current.getRawStyle();
    }

    @Override
    public AttributeSet getAttributes() {
        return this.current.getAttributes();
    }

    @Override
    public Feature get() {
        return this.current.get();
    }

    @Override
    public long getId() {
        return this.current.getId();
    }

    @Override
    public long getVersion() {
        return this.current.getVersion();
    }

    @Override
    public long getFsid() {
        return this.current.getFsid();
    }

    /**************************************************************************/
    
    private static Comparator<FeatureCursor> orderToComparator(FeatureDataStore.FeatureQueryParameters.Order order) {
        if(order instanceof FeatureDataStore.FeatureQueryParameters.Distance) {
            // XXX - 
            return null;
        } else if(order instanceof FeatureDataStore.FeatureQueryParameters.FeatureId) {
            return FID_COMPARATOR;
        } else if(order instanceof FeatureDataStore.FeatureQueryParameters.FeatureName) {
            return FEATURE_NAME_COMPARATOR;
        } else if(order instanceof FeatureDataStore.FeatureQueryParameters.Resolution) {
            // XXX - 
            return null;
        } else if(order instanceof FeatureDataStore.FeatureQueryParameters.FeatureSet) {
            return FSID_COMPARATOR;
        } else if(order instanceof FeatureDataStore.FeatureQueryParameters.GeometryType) {
            // XXX - 
            return null;
        } else {
            return null;
        }        
    }
}
