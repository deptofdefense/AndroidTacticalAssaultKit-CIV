package com.atakmap.util;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;

public class Quadtree<T> {

    private final static int DEFAULT_MAX_DEPTH = 19;

    private final Quadtree<T> parent;
    private final Quadtree<T> root;

    /** ordered UL, UR, LR, LL */
    private final Quadtree<T>[] children;
    
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;
    private final double centerX;
    private final double centerY;

    private final Function<T> function;
    
    private final Collection<T> objects;

    private final int limit;
    
    private final Map<T, Quadtree<T>> objectToNode;
    
    private int descendants;
    
    private final int maxDepth;
    private final int depth;
    
    private int numChildren;
    
    public Quadtree(Function<T> function, double minX, double minY, double maxX, double maxY) {
        this(function, 0, minX, minY, maxX, maxY, DEFAULT_MAX_DEPTH);
    }

    public Quadtree(Function<T> function, double minX, double minY, double maxX, double maxY, int maxDepth) {
        this(function, 0, minX, minY, maxX, maxY, maxDepth);
    }

    public Quadtree(Function<T> function, int nodeLimit, double minX, double minY, double maxX, double maxY) {
        this(null, function, nodeLimit, minX, minY, maxX, maxY, DEFAULT_MAX_DEPTH);
    }
    
    public Quadtree(Function<T> function, int nodeLimit, double minX, double minY, double maxX, double maxY, int maxDepth) {
        this(null, function, nodeLimit, minX, minY, maxX, maxY, maxDepth);
    }
    
    @SuppressWarnings("unchecked")
    private Quadtree(Quadtree<T> parent, Function<T> function, int nodeLimit, double minX, double minY, double maxX, double maxY, int maxDepth) {
        this.parent = parent;
        this.function = function;
        this.limit = nodeLimit;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        
        this.centerX = (this.minX+this.maxX)/2d;
        this.centerY = (this.minY+this.maxY)/2d;
        
        this.objects = new LinkedHashSet<T>();

        if(this.parent == null) {
            this.root = this;
            this.objectToNode = new IdentityHashMap<T, Quadtree<T>>();
        } else {
            this.root = this.parent.root;
            this.objectToNode = null;
            this.parent.numChildren++;
        }
        
        this.children = new Quadtree[4];
        this.descendants = 0;
        
        this.maxDepth = maxDepth;
        
        this.depth = (this.parent != null) ? this.parent.depth+1 : 0;
        
        this.numChildren = 0;
    }

    private void getImpl(double minX, double minY, double maxX, double maxY, Collection<T> retval, PointD objMin, PointD objMax) {
        for(T object : this.objects) {
           this.function.getBounds(object, objMin, objMax);
           if(Rectangle.intersects(minX, minY, maxX, maxY,
                                   objMin.x, objMin.y, objMax.x, objMax.y)) {

               retval.add(object);
           }
        }
        for(int i = 0; i < 4; i++) {
            if(this.children[i] == null)
                continue;
            if(Rectangle.intersects(minX, minY,
                                    maxX, maxY,
                                    this.children[i].minX, this.children[i].minY,
                                    this.children[i].maxX, this.children[i].maxY)) {

                this.children[i].getImpl(minX, minY, maxX, maxY, retval, objMin, objMax);
            }
        }
    }

    public void get(double minX, double minY, double maxX, double maxY, Collection<T> retval) {
        this.getImpl(minX, minY, maxX, maxY, retval, new PointD(0,0), new PointD(0,0));
    }
    
    private int sizeImpl(double minX, double minY, double maxX, double maxY, PointD objMin, PointD objMax) {
        int retval = 0;
        for(T object : this.objects) {
           this.function.getBounds(object, objMin, objMax);
           if(Rectangle.intersects(minX, minY, maxX, maxY,
                                   objMin.x, objMin.y, objMax.x, objMax.y)) {

               retval++;
           }
        }
        for(int i = 0; i < 4; i++) {
            if(this.children[i] == null)
                continue;
            if(this.children[i].minX >= minX &&
               this.children[i].minY >= minY &&
               this.children[i].maxX <= maxX &&
               this.children[i].maxY <= maxY) {
                
                // if the ROI contains the child, add its contents plus all of
                // its descendants
                retval += this.children[i].objects.size() + this.children[i].descendants;
            } else if(Rectangle.intersects(minX, minY,
                                           maxX, maxY,
                                           this.children[i].minX, this.children[i].minY,
                                           this.children[i].maxX, this.children[i].maxY)) {

                // if the ROI intersects the child, process the child
                retval += this.children[i].sizeImpl(minX, minY, maxX, maxY, objMin, objMax);
            }
        }
        return retval;
    }

    public int size(double minX, double minY, double maxX, double maxY) {
        if(minX <= this.minX && minY <= this.minY && maxX >= this.maxX && maxY >= this.maxY) {
            return this.objects.size()+this.descendants;
        } else {
            return this.sizeImpl(minX, minY, maxX, maxY, new PointD(0,0), new PointD(0,0));
        }
    }
    
    public int size() {
        return this.size(this.minX, this.minY, this.maxX, this.maxY);
    }
    
    private void divide() {
        if(this.objects.isEmpty())
            return;
        
        final double halfWidth = (this.maxX-this.minX)/2d;
        final double halfHeight = (this.maxY-this.minY)/2d;

        PointD min = new PointD(0, 0);
        PointD max = new PointD(0, 0);
        
        Iterator<T> iter = this.objects.iterator();
        while(iter.hasNext()) {
            T o = iter.next();
            this.function.getBounds(o, min, max);
            
            if((max.x-min.x) > halfWidth || (max.y-min.y) > halfHeight)
                continue;
            
            // check if any child contains the object
            for(int i = 0; i < 4; i++) {
                final double cnx = this.minX+((i%2)*halfWidth);
                final double cxx = this.centerX+((i%2)*halfWidth);
                final double cny = this.minY+((i/2f)*halfHeight);
                final double cxy = this.centerY+((i/2f)*halfHeight);
                
                // if a child contains the object, add it to the child
                if(cnx <= min.x &&
                   cny <= min.y &&
                   cxx >= max.x &&
                   cxy >= max.y) {

                    // create the child if necessary
                    if(this.children[i] == null)
                        this.children[i] = new Quadtree<T>(this, this.function, this.limit, cnx, cny, cxx, cxy, this.maxDepth);
                    // add the object
                    this.children[i].objects.add(o);
                    // remove from the parent list
                    iter.remove();
                    break;
                }
            }
        }
    }
    
    private void aggregate() {
        for(int i = 0; i < 4; i++) {
            if(this.children[i] == null)
                continue;
            this.objects.addAll(this.children[i].objects);
            this.children[i] = null;
        }
    }

    public void add(T object) {
        PointD min = new PointD(0, 0);
        PointD max = new PointD(0, 0);
        
        this.function.getBounds(object, min, max);
        this.add(object, min.x, min.y, max.x, max.y);        
    }
    
    private final void add(T object, double minX, double minY, double maxX, double maxY) {
        final double halfWidth = (this.maxX-this.minX)/2d;
        final double halfHeight = (this.maxY-this.minY)/2d;
        
        final boolean preferChild = ((this.objects.size()>=this.limit) || (this.numChildren > 0));

        if(preferChild && this.depth < this.maxDepth) {
            double cnx;
            double cny;
            double cxx;
            double cxy;
            for(int i = 0; i < 4; i++) {
                cnx = this.minX+((i%2)*halfWidth);
                cxx = this.centerX+((i%2)*halfWidth);
                cny = this.minY+((i/2f)*halfHeight);
                cxy = this.centerY+((i/2f)*halfHeight);
                
                // if a child contains the object, add it to the child
                if(cnx <= minX &&
                   cny <= minY &&
                   cxx >= maxX &&
                   cxy >= maxY) {
    
                    if(preferChild || this.children[i] != null) {
                        if(this.children[i] == null)
                            this.children[i] = new Quadtree<T>(this, this.function, this.limit, cnx, cny, cxx, cxy, this.maxDepth);
                        this.children[i].add(object, minX, minY, maxX, maxY);
                        return;
                    }
                }
            }
        }
        this.objects.add(object);
        this.root.objectToNode.put(object, this);
        Quadtree<T> ancestor = this.parent;
        while(ancestor != null) {
            ancestor.descendants++;
            ancestor = ancestor.parent;
        }
    }
    
    public boolean remove(T object) {
        final Quadtree<T> node = this.root.objectToNode.remove(object);
        if(node == null)
            return false;
        return node.removeImpl(object, true);
    }

    private boolean removeImpl(T object, boolean cullable) {
        if(!this.objects.remove(object))
            return false;
        if(this.parent == null)
            return true;
        
        Quadtree<T> ancestor = this.parent;
        while(ancestor != null) {
            ancestor.descendants--;
            ancestor = ancestor.parent;
        }

        // remove node from parent on empty
        if(cullable && this.numChildren == 0 && this.objects.isEmpty()) {
            for(int i = 0; i < 4; i++) {
                if(this.parent.children[i] == this) {
                    this.parent.children[i] = null;
                    this.parent.numChildren--;
                    break;
                }
            }
        }
        
        // check for node aggregation
        if(this.parent.numChildren > 0 && this.parent.objects.size() < this.parent.limit) {
            boolean shouldAggregate = true;
            int aggregateObjects = this.parent.objects.size();
            for(int i = 0; i < 4; i++) {
                if(this.parent.children[i] == null)
                    continue;
                if(this.parent.children[i].numChildren > 0) {
                    shouldAggregate = false;
                    break;
                }
                aggregateObjects += this.parent.children[i].objects.size();
            }
            shouldAggregate &= (aggregateObjects < this.parent.limit);
            if(shouldAggregate)
                this.parent.aggregate();
        }

        return true;
    }

    public boolean refresh(T object) {
        // clear the reference to the owning node, as that may change
        Quadtree<T> node = this.root.objectToNode.remove(object);
        if(node == null)
            return false;

        PointD objMin = new PointD(0, 0);
        PointD objMax = new PointD(0, 0);
        this.function.getBounds(object, objMin, objMax);
        final boolean inNode = (node.minX <= objMin.x &&
                                node.minY <= objMin.y &&
                                node.maxX >= objMax.x &&
                                node.maxY >= objMax.y);

        if(!node.removeImpl(object, !inNode))
            return false;

        if(inNode)
            node.add(object, objMin.x, objMin.y, objMax.x, objMax.y);
        else
            this.root.add(object, objMin.x, objMin.y, objMax.x, objMax.y);
        return true;
    }
    
    public void clear() {
        this.objects.clear();
        for(int i = 0; i < 4; i++)
            this.children[i] = null;
        this.numChildren = 0;
        this.descendants = 0;
    }

    /**************************************************************************/
    
    public static interface Function<T> {
        // XXX - does array have better implications for garbage collection
        //       purposes?
        public void getBounds(T object, PointD min, PointD max);
    }
}
