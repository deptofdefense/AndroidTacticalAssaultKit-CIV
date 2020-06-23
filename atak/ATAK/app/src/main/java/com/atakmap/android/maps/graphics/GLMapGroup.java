
package com.atakmap.android.maps.graphics;

import android.util.Pair;

import com.atakmap.android.editableShapes.GLMultipolyline;
import com.atakmap.android.maps.AxisOfAdvance;
import com.atakmap.android.maps.CrumbTrail;
import com.atakmap.android.maps.Doghouse;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.android.maps.MetaShape;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.SimpleRectangle;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.maps.graphics.widgets.GLAngleOverlay;
import com.atakmap.android.maps.graphics.widgets.GLAutoSizeAngleOverlay;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.android.widgets.AutoSizeAngleOverlayShape;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;

import org.apache.commons.lang.reflect.ConstructorUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public final class GLMapGroup {

    public static final String TAG = "GLMapGroup";

    private GLMapGroup() {
    }

    private static Class lookupGLClass(Class itemClass) {
        Class glClass = null;

        try {
            String packageName = itemClass.getPackage().getName();
            String className = itemClass.getSimpleName();

            // Allow either graphics.GLClassName or just GLClassName? Former is good if you have
            // several, latter seems better if just one
            try {
                String glClassName = packageName + ".graphics.GL" + className;
                glClass = Class.forName(glClassName);
            } catch (ClassNotFoundException e) {
                String glClassName = packageName + ".GL" + className;
                glClass = Class.forName(glClassName);
            }
        } catch (ClassCastException e) {
            // Log.e(TAG, "Unable to instatiate GL class for map item of class " +
            // itemClass.getName());
            // Log.e(TAG, "error: ", e);
        } catch (ClassNotFoundException e) {
            // Log.e(TAG, "Unable to instatiate GL class for map item of class "+
            // itemClass.getName());
            // Log.e(TAG, "error: ", e);
        } catch (IllegalArgumentException e) {
            // Log.e(TAG, "Unable to instatiate GL class for map item of class " +
            // itemClass.getName());
            // Log.e(TAG, "error: ", e);
        } catch (SecurityException e) {
            // Log.e(TAG, "Unable to instatiate GL class for map item of class " +
            // itemClass.getName());
            // Log.e(TAG, "error: ", e);
        }

        // Check if it's a GLMapItem
        if (glClass != null && !GLMapItem.class.isAssignableFrom(glClass)) {
            glClass = null;
            Log.e(TAG,
                    "GL class was not a subclass of GLMapItem for item of class "
                            + itemClass.getName());
        }

        return glClass;
    }

    static final Map<Class<? extends MapItem>, Class<? extends GLMapItem>> glClasses = new HashMap<>();
    static final Map<Class<? extends GLMapItem>, Constructor> glCtors = new HashMap<>();

    public final static GLMapItemSpi2 DEFAULT_GLMAPITEM_SPI2 = new GLMapItemSpi2() {

        @Override
        public int getPriority() {
            // this SPI will be the fall-through if all else fails
            return -1;
        }

        @Override
        public GLMapItem create(Pair<MapRenderer, MapItem> arg) {
            final MapRenderer surface = arg.first;
            final MapItem item = arg.second;

            if (item instanceof Doghouse)
                return new GLDogHouse(surface, (Doghouse) item);
            else if (item.getClass().equals(Polyline.class))
                return new GLPolyline(surface, (Polyline) item);
            else if (item instanceof SimpleRectangle)
                return new GLRectangle(surface, (SimpleRectangle) item);
            else if (item instanceof Ellipse)
                return new GLEllipse(surface, (Ellipse) item);
            else if (item instanceof MultiPolyline)
                return new GLMultipolyline(surface, (MultiPolyline) item);
            else if (item instanceof AxisOfAdvance)
                return new GLAxisOfAdvance(surface, (AxisOfAdvance) item);
            else if (item instanceof SensorFOV)
                return new GLSensorFOV(surface, (SensorFOV) item);
            else if (item instanceof AngleOverlayShape)
                return new GLAngleOverlay(surface, (AngleOverlayShape) item);
            else if (item instanceof AutoSizeAngleOverlayShape)
                return new GLAutoSizeAngleOverlay(surface,
                        (AutoSizeAngleOverlayShape) item);
            else if (item instanceof MetaMapPoint || item instanceof MetaShape
                    || item instanceof CrumbTrail)
                // Meta items don't have a graphical representation themselves, since they manage
                // other
                // map items or similar
                return null;

            return this.reflect(surface, item);
        }

        private synchronized GLMapItem reflect(MapRenderer surface,
                MapItem item) {
            // Automatically try to find the GL class if it isn't one of the core map items but
            // something added by another component
            Class<? extends MapItem> itemClass = item.getClass();
            Class<? extends GLMapItem> glClass = null;

            Class<?> currentClass = itemClass;
            // If class is cached, use it
            if (glClasses.containsKey(itemClass)) {
                glClass = glClasses.get(itemClass);
            } else {
                // Otherwise, look it up
                while (currentClass != null && glClass == null) {
                    glClass = lookupGLClass(currentClass);
                    if (glClass == null)
                        currentClass = currentClass.getSuperclass();
                }

                // Cache class, or null if it wasn't found
                glClasses.put(itemClass, glClass);
            }

            if (glClass == null) {
                // This is messy.. Should probably just pull in EditablePolyline to MapLibrary and
                // hard code it's GL class above.
                // But for now, it's losely coupled in DrawingTools and this check is needed to pick
                // up any Polyline subclasses
                // that don't have an un-proguardded GL class.
                // (above Polyline check only catches unsubclassed Polylines, since
                // GLEditablePolyline needs to be picked up by the lookup code.)
                if (item instanceof Polyline)
                    return new GLPolyline(surface, (Polyline) item);
            }

            // If there is a GL class, instantiate it
            if (glClass != null) {
                Constructor ctor = glCtors.get(glClass);
                if (ctor == null) {
                    ctor = ConstructorUtils.getMatchingAccessibleConstructor(
                            glClass,
                            new Class[] {
                                    MapRenderer.class, currentClass
                    });
                    glCtors.put(glClass, ctor);
                }

                try {
                    return (GLMapItem) ctor.newInstance(surface, item);
                } catch (InstantiationException ignored) {
                } catch (IllegalAccessException ignored) {
                } catch (InvocationTargetException ignored) {
                }
            }

            return null;
        }

    };
}
