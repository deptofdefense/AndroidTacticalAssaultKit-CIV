package gov.tak.examples.examplewindow;

import com.atakmap.map.RenderSurface;
import com.jogamp.opengl.GLAutoDrawable;

import java.awt.*;
import java.awt.event.*;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class JOGLRenderSurface implements RenderSurface {
    private JOGLRenderSurface() {}

    @Override
    public final double getDpi() {
        return 128.0;
    }


    public static RenderSurface get(GLAutoDrawable drawable) {
        if(drawable instanceof Component) {
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
}
