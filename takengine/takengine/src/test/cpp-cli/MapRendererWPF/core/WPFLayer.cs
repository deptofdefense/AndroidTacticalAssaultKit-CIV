using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MapRendererWPF.core
{
    public interface WPFLayer : WPFMapRenderable
    {
        atakmap.cpp_cli.core.Layer getSubject();
    }

    public interface WPFLayerSpi
    {
        WPFLayer Create(WPFMapView renderer, atakmap.cpp_cli.core.Layer layer);
    }

    public class WPFLayerFactory
    {
        private static System.Collections.Generic.HashSet<WPFLayerSpi> spis = new HashSet<WPFLayerSpi>();

        private WPFLayerFactory() { }

        public static WPFLayer Create(WPFMapView view, atakmap.cpp_cli.core.Layer layer)
        {
            System.Threading.Monitor.Enter(spis);
            try
            {
                WPFLayer retval;
                foreach (WPFLayerSpi spi in spis)
                {
                    retval = spi.Create(view, layer);
                    if (retval != null)
                        return retval;
                }
                return null;
            }
            finally
            {
                System.Threading.Monitor.Exit(spis);
            }
        }

        public static void Register(WPFLayerSpi spi) {
            System.Threading.Monitor.Enter(spis);
            try
            {
                spis.Add(spi);
            }
            finally
            {
                System.Threading.Monitor.Exit(spis);
            }
        }

        public static void Unregister(WPFLayerSpi spi) {
            System.Threading.Monitor.Enter(spis);
            try
            {
                spis.Remove(spi);
            }
            finally
            {
                System.Threading.Monitor.Exit(spis);
            }
        }
    }

    public abstract class WPFAbstractLayer : atakmap.cpp_cli.core.Layer.VisibilityListener,
                                             WPFLayer
                                             
    {
        protected WPFMapView renderer;
        protected atakmap.cpp_cli.core.Layer subject;
        private bool initialized;
        private bool visible;

        protected WPFAbstractLayer(WPFMapView r, atakmap.cpp_cli.core.Layer s)
        {
            renderer = r;
            subject = s;
            initialized = false;
        }

        protected abstract void Init();
        protected abstract void DrawImpl(atakmap.cpp_cli.core.AtakMapView view, System.Drawing.Graphics mapSurface);
        protected abstract void ReleaseImpl();

        public atakmap.cpp_cli.core.Layer getSubject()
        {
            return subject;
        }

        public void Draw(atakmap.cpp_cli.core.AtakMapView view, System.Drawing.Graphics mapSurface)
        {
            if(!initialized)
            {
                Init();
                subject.addVisibilityListener(this);
                visible = subject.isVisible();
                initialized = true;
            }

            if (visible)
                DrawImpl(view, mapSurface);
        }

        public void Release()
        {
            subject.removeVisibilityListener(this);
            ReleaseImpl();
            initialized = false;
        }

        public void visibilityChanged(atakmap.cpp_cli.core.Layer layer) {
            renderer.InvokeOnRenderThread(new UpdateVisibilityTask(this, subject.isVisible()));
        }

        private class UpdateVisibilityTask : WPFMapView.IRenderTask
        {
            private WPFAbstractLayer owner;
            private bool visible;

            public UpdateVisibilityTask(WPFAbstractLayer o, bool v)
            {
                owner = o;
                visible = v;
            }

            public void Run()
            {
                owner.visible = this.visible;
            }
        }
    }
}
