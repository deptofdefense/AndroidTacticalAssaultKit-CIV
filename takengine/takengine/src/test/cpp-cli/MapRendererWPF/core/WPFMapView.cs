using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Threading;

namespace MapRendererWPF.core
{
    public class WPFMapView : atakmap.cpp_cli.core.AtakMapView.MapLayersChangedListener
    {
        private atakmap.cpp_cli.core.AtakMapView mapView;
        private Dispatcher renderThreadDispatcher;
        private WPFMapRenderable baseMap;
        private Dictionary<atakmap.cpp_cli.core.Layer, WPFLayer> layers;
        private List<WPFLayer> layerRenderers;

        private delegate void RunRenderTaskDelegate();

        public WPFMapView(atakmap.cpp_cli.core.AtakMapView mv, System.Windows.Threading.Dispatcher d)
        {
            mapView = mv;
            renderThreadDispatcher = d;

            layers = new Dictionary<atakmap.cpp_cli.core.Layer, WPFLayer>();
            layerRenderers = new List<WPFLayer>();
            mapView.addLayersChangedListener(this);
            UpdateLayers();
        }

        public void SetBaseMap(WPFMapRenderable bm)
        {
            InvokeOnRenderThread(new SetBasemapTask(this, bm));
        }

        public void Draw(System.Drawing.Graphics surface)
        {
            surface.Clear(System.Drawing.Color.Black);

            // render the base map
            if(baseMap != null)
                baseMap.Draw(mapView, surface);

            // render the layers
            foreach (WPFLayer layer in layerRenderers)
                layer.Draw(mapView, surface);
        }

        public void InvokeOnRenderThread(IRenderTask task)
        {
            renderThreadDispatcher.BeginInvoke(new RunRenderTaskDelegate(task.Run));
        }

        /*********************************************************************/
        // Map Layers Changed Listener

        public void mapLayerAdded(atakmap.cpp_cli.core.AtakMapView view, atakmap.cpp_cli.core.Layer layer)
        {
            UpdateLayers();
        }

        public void mapLayerPositionChanged(atakmap.cpp_cli.core.AtakMapView view, atakmap.cpp_cli.core.Layer layer, int oldPos, int newPos)
        {
            UpdateLayers();
        }

        public void mapLayerRemoved(atakmap.cpp_cli.core.AtakMapView view, atakmap.cpp_cli.core.Layer layer)
        {
            UpdateLayers();
        }

        private void UpdateLayers()
        {
            System.Collections.Generic.LinkedList<atakmap.cpp_cli.core.Layer> layers = new System.Collections.Generic.LinkedList<atakmap.cpp_cli.core.Layer>();
            for (int i = 0; i < mapView.getNumLayers(); i++)
                layers.AddLast(mapView.getLayer(i));

            InvokeOnRenderThread(new ValidateLayersTask(this, layers));
        }

        /*********************************************************************/

        public interface IRenderTask
        {
            void Run();
        }

        /*********************************************************************/

        private abstract class InternalRenderTask : IRenderTask
        {
            protected WPFMapView owner;

            protected InternalRenderTask(WPFMapView o)
            {
                owner = o;
            }

            public abstract void Run();
        }

        private class ValidateLayersTask : InternalRenderTask
        {
            private System.Collections.Generic.LinkedList<atakmap.cpp_cli.core.Layer> layers;

            public ValidateLayersTask(WPFMapView m, System.Collections.Generic.LinkedList<atakmap.cpp_cli.core.Layer> l)
                : base(m)
            {
                layers = l;
            }

            public override void Run()
            {
                Dictionary<atakmap.cpp_cli.core.Layer, WPFLayer> old = new Dictionary<atakmap.cpp_cli.core.Layer, WPFLayer>(owner.layers);
                owner.layers.Clear();

                WPFLayer wpfLayer;
                foreach(atakmap.cpp_cli.core.Layer l in layers) {
                    if(!old.ContainsKey(l)) {
                        wpfLayer = WPFLayerFactory.Create(owner, l);
                    } else {
                        wpfLayer = old[l];
                        old.Remove(l);
                    }
                    if(wpfLayer != null) {
                        owner.layers.Add(l, wpfLayer);
                        owner.layerRenderers.Add(wpfLayer);
                    }
                }

                foreach (WPFLayer l in old.Values)
                    l.Release();
                old.Clear();
            }
        }

        private class SetBasemapTask : InternalRenderTask
        {
            private WPFMapRenderable newBaseMap;

            public SetBasemapTask(WPFMapView m, WPFMapRenderable bm) :
                base(m)
            {
                newBaseMap = bm;
            }

            public override void Run()
            {
                owner.baseMap = newBaseMap;  
            }
        }
    }
}
