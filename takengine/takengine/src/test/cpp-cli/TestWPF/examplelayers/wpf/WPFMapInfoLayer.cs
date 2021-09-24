using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace TestWPF.examplelayers.wpf
{
    class WPFMapInfoLayer : MapRendererWPF.core.WPFAbstractLayer,
                            TestWPF.examplelayers.MapInfoLayer.CursorListener
    {

        System.Diagnostics.Stopwatch stopwatch;
        int frames;
        double mouseX;
        double mouseY;
        atakmap.cpp_cli.core.GeoPoint mouseGeo;

        public WPFMapInfoLayer(MapRendererWPF.core.WPFMapView r, MapInfoLayer l) :
            base(r, l)
        {
            mouseGeo = null;
        }

        protected override void Init()
        {
            frames = 0;
            stopwatch = new System.Diagnostics.Stopwatch();
            stopwatch.Start();

            (subject as MapInfoLayer).AddCursorListener(this);
        }

        protected override void DrawImpl(atakmap.cpp_cli.core.AtakMapView view, System.Drawing.Graphics mapSurface)
        {
            frames++;

            System.Drawing.Brush textBrush = new System.Drawing.SolidBrush(System.Drawing.Color.Red);
            System.Drawing.Font textFont = new System.Drawing.Font("Arial", 8);

            System.Drawing.PointF textLoc = new System.Drawing.PointF(10, textFont.Size*2);

            RenderTextLine(mapSurface, "Projection: " + view.getProjection(), textFont, textBrush, ref textLoc);
            atakmap.cpp_cli.core.GeoPoint center = new atakmap.cpp_cli.core.GeoPoint();
            view.getPoint(center);
            RenderTextLine(mapSurface, "Center: " + center.latitude.ToString("0.000000") + ", " + center.longitude.ToString("0.000000"), textFont, textBrush, ref textLoc);
            RenderTextLine(mapSurface, "Scale: 1:" + (int)(1.0 / view.getMapScale()), textFont, textBrush, ref textLoc);
            RenderTextLine(mapSurface, "Resolution: " + view.getMapResolution().ToString("0.00") + "m", textFont, textBrush, ref textLoc);
            RenderTextLine(mapSurface, "Rotation: " + view.getMapRotation().ToString("0.00"), textFont, textBrush, ref textLoc);
            string cursorString = "Cursor: ";
            if(mouseGeo != null)
                cursorString += mouseGeo.latitude.ToString("0.000000") + ", " + mouseGeo.longitude.ToString("0.000000") + " ";
            cursorString += "[" + (int)mouseX + ", " + (int)mouseY + "]";

            RenderTextLine(mapSurface, cursorString, textFont, textBrush, ref textLoc);

            textLoc.Y = view.getHeight() - (float)(5 * textFont.Size);
            textBrush = new System.Drawing.SolidBrush(System.Drawing.Color.Green);
            RenderTextLine(mapSurface, "FPS: " + (int)Math.Round((double)frames/((double)stopwatch.ElapsedMilliseconds/1000.0)), textFont, textBrush, ref textLoc);

            if (stopwatch.ElapsedMilliseconds >= 1000)
            {
                stopwatch.Restart();
                frames = 0;
            }
        }

        private void RenderTextLine(System.Drawing.Graphics mapSurface, string text, System.Drawing.Font textFont, System.Drawing.Brush textBrush, ref System.Drawing.PointF textLoc)
        {
            mapSurface.DrawString(text, textFont, textBrush, textLoc);
            textLoc.Y += (float)(2.5 * textFont.Size);
        }

        protected override void ReleaseImpl()
        {
            (subject as MapInfoLayer).RemoveCursorListener(this);
        }

        public void PositionUpdated(double x, double y, atakmap.cpp_cli.core.GeoPoint geo)
        {
            renderer.InvokeOnRenderThread(new CursorUpdateTask(this, x, y, geo));
        }

        private class CursorUpdateTask : MapRendererWPF.core.WPFMapView.IRenderTask
        {
            WPFMapInfoLayer owner;
            double mouseX;
            double mouseY;
            atakmap.cpp_cli.core.GeoPoint mouseGeo;

            public CursorUpdateTask(WPFMapInfoLayer o, double x, double y, atakmap.cpp_cli.core.GeoPoint geo)
            {
                owner = o;
                mouseX = x;
                mouseY = y;
                mouseGeo = geo;
            }

            public void Run()
            {
                owner.mouseX = mouseX;
                owner.mouseY = mouseY;
                owner.mouseGeo = mouseGeo;
            }
        }
    }

    public class WPFMapInfoLayerSpi : MapRendererWPF.core.WPFLayerSpi
    {
        public static MapRendererWPF.core.WPFLayerSpi INSTANCE = new WPFMapInfoLayerSpi();

        private WPFMapInfoLayerSpi() { }

        public MapRendererWPF.core.WPFLayer Create(MapRendererWPF.core.WPFMapView r, atakmap.cpp_cli.core.Layer layer)
        {
            MapInfoLayer l = (layer as MapInfoLayer);
            if (l != null)
                return new WPFMapInfoLayer(r, l);
            return null;
        }
    }
}
