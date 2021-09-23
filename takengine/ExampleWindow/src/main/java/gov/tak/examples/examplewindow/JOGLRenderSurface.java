package gov.tak.examples.examplewindow;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.RenderSurface;
import javax.media.opengl.GLAutoDrawable;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class JOGLRenderSurface implements RenderSurface {
    static {
        do {
            try {
                ScalableSurfaceCompat.instance = new ReflectScalableSurface_3F("com.jogamp.nativewindow.ScalableSurface");
                break;
            } catch (Throwable ignored) {}
            try {
                ScalableSurfaceCompat.instance = new ReflectScalableSurface_3I("javax.media.nativewindow.ScalableSurface");
                break;
            } catch (Throwable ignored) {}
        } while(false);
    }

    private JOGLRenderSurface() {}

    @Override
    public final double getDpi() {
        return 128.0;
    }


    public static RenderSurface get(GLAutoDrawable drawable) {
        if(drawable instanceof Component) {
            ScalableSurfaceCompat.instance.setSurfaceScale((Component)drawable, 1f, 1f);
            return new ComponentImpl((Component) drawable);
        } else if(drawable instanceof com.jogamp.newt.Window) {
            return new WindowImpl((com.jogamp.newt.Window)drawable);
        } else {
            return null;
        }
    }

    private static class ComponentImpl extends JOGLRenderSurface {

        final Component impl;
        final Map<OnSizeChangedListener, ComponentListener> listeners = new IdentityHashMap<>();

        private ComponentImpl(Component impl) {
            this.impl = impl;
        }

        @Override
        public int getWidth() {
            return impl.getWidth();
        }

        @Override
        public int getHeight() {
            return impl.getHeight();
        }

        @Override
        public void addOnSizeChangedListener(OnSizeChangedListener l) {
            ComponentListener cl;
            synchronized(listeners) {
                if(listeners.containsKey(l))
                    return;
                cl = new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        l.onSizeChanged(ComponentImpl.this, impl.getWidth(), impl.getHeight());
                    }
                };
                listeners.put(l, cl);
            }
            impl.addComponentListener(cl);
        }

        @Override
        public void removeOnSizeChangedListener(OnSizeChangedListener l) {
            ComponentListener cl;
            synchronized(listeners) {
                cl = listeners.remove(l);
                if(cl == null)
                    return;
            }
            impl.removeComponentListener(cl);
        }
    }

    private static class WindowImpl extends JOGLRenderSurface {

        final com.jogamp.newt.Window impl;
        final Map<OnSizeChangedListener, com.jogamp.newt.event.WindowListener> listeners = new IdentityHashMap<>();

        private WindowImpl(com.jogamp.newt.Window impl) {
            this.impl = impl;
        }

        @Override
        public int getWidth() {
            return impl.getWidth();
        }

        @Override
        public int getHeight() {
            return impl.getHeight();
        }

        @Override
        public void addOnSizeChangedListener(OnSizeChangedListener l) {
            com.jogamp.newt.event.WindowListener cl;
            synchronized(listeners) {
                if(listeners.containsKey(l))
                    return;
                cl = new com.jogamp.newt.event.WindowAdapter() {
                    @Override
                    public void windowResized(com.jogamp.newt.event.WindowEvent e) {
                        l.onSizeChanged(WindowImpl.this, impl.getWidth(), impl.getHeight());
                    }
                };
                listeners.put(l, cl);
            }
            impl.addWindowListener(cl);
        }

        @Override
        public void removeOnSizeChangedListener(OnSizeChangedListener l) {
            com.jogamp.newt.event.WindowListener cl;
            synchronized(listeners) {
                cl = listeners.remove(l);
                if(cl == null)
                    return;
            }
            impl.removeWindowListener(cl);
        }
    }

    static class ScalableSurfaceCompat {
        static ScalableSurfaceCompat instance = new ScalableSurfaceCompat();

        void setSurfaceScale(Component c, float scaleX, float scaleY) {} //  no-op
    }

    static abstract class ReflectScalableSurface extends ScalableSurfaceCompat {
        Class clazz;
        Method setSurfaceScale;

        ReflectScalableSurface(String className, Class[] setSurfaceScaleArgs) throws ClassNotFoundException, NoSuchMethodException {
            clazz = getClass().getClassLoader().loadClass(className);
            setSurfaceScale = clazz.getMethod("setSurfaceScale", setSurfaceScaleArgs);
        }
    }
    final static class ReflectScalableSurface_3F extends ReflectScalableSurface {
        ReflectScalableSurface_3F(String className) throws ClassNotFoundException, NoSuchMethodException {
            super(className, new Class[] {float[].class});
        }

        @Override
        void setSurfaceScale(Component c, float scaleX, float scaleY) {
            if(clazz.isAssignableFrom(c.getClass())) {
                try {
                    setSurfaceScale.invoke(c, new float[]{scaleX, scaleY});
                } catch (Throwable t) {
                    Log.w("JOGLRenderSurface$ReflectScalableSurface_3F", "Failed to invoke `setSurfaceScale`");
                }
            }
        }
    }
    final static class ReflectScalableSurface_3I extends ReflectScalableSurface {
        ReflectScalableSurface_3I(String className) throws ClassNotFoundException, NoSuchMethodException {
            super(className, new Class[] {int[].class});
        }

        @Override
        void setSurfaceScale(Component c, float scaleX, float scaleY) {
            if(clazz.isAssignableFrom(c.getClass())) {
                try {
                    setSurfaceScale.invoke(c, new int[]{(int)scaleX, (int)scaleY});
                } catch (Throwable t) {
                    Log.w("JOGLRenderSurface$ReflectScalableSurface_3F", "Failed to invoke `setSurfaceScale`");
                }
            }
        }
    }
}
