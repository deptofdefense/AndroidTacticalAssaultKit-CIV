using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;

namespace TestWPF
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        atakmap.cpp_cli.core.AtakMapView mapView;
        MapRendererWPF.core.WPFMapView mapViewRenderer;
        examplelayers.MapInfoLayer infoLayer;

        bool mapPaintInProgress = false;
        public delegate void InvokeDelegate();

        RepaintThread repaintThread;

        WriteableBitmap swap = null;

        bool inPanDrag = false;
        bool inRotateDrag = false;
        float lastMouseX;
        float lastMouseY;

        public MainWindow()
        {
            InitializeComponent();

            // XXX - set up some initial sizes as the MapSurfaceImage control
            //       does not seem to be reporting size
            int initWidth = 100;
            int initHeight = 50;

            // assign a WritableBitmap that we are going to render into
            MapSurfaceImage.Source = new WriteableBitmap(initWidth, initHeight,
                         1.0, 1.0, System.Windows.Media.PixelFormats.Pbgra32, null);

            // XXX - hardcoded DPI
            int dpi = 240;

            // create a new mapview with the initial sizes (we'll update the
            // dimensions whenever the associated control changes size)
            mapView = new atakmap.cpp_cli.core.AtakMapView(initWidth, initHeight, dpi);

            // set the default focus point -- the center should generally be used
            mapView.getController().setDefaultFocusPoint(new atakmap.cpp_cli.math.PointF(initWidth / 2, initHeight / 2));

            // create the custom WPF renderer.  The renderer will observe
            // changes to the mapview and automate layer renderer creation
            mapViewRenderer = new MapRendererWPF.core.WPFMapView(mapView, MapSurfaceImage.Dispatcher);

            // set the basemap -- we'll use the Blue Marble basemap
            mapViewRenderer.SetBaseMap(new MapRendererWPF.bluemarble.WPFBlueMarble());

            // create a thread to invoke repaints
            repaintThread = new RepaintThread(this);
            repaintThread.Start();

            // register the layer renderer service providers that for the
            // layers we want to include
            MapRendererWPF.core.WPFLayerFactory.Register(TestWPF.examplelayers.wpf.WPFMapInfoLayerSpi.INSTANCE);

            // add the "info" layer to the map
            infoLayer = new TestWPF.examplelayers.MapInfoLayer();
            mapView.addLayer(infoLayer);
        }

        private void MapSurfaceImage_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            // update the dimensions and focus point when the MapSurfaceImage
            // control changes size
            System.Windows.Size size = e.NewSize;

            swap = new WriteableBitmap((int)size.Width, (int)size.Height,
             1.0, 1.0, System.Windows.Media.PixelFormats.Pbgra32, null);

            if (mapView != null) {
                mapView.setSize((float)size.Width, (float)size.Height);
                mapView.getController().setDefaultFocusPoint(new atakmap.cpp_cli.math.PointF((float)size.Width / 2, (float)size.Height / 2));
            }
        }

        private void PaintMap()
        {
            System.Windows.Media.Imaging.WriteableBitmap mapSurface = null;
            System.Drawing.Bitmap drawingBitmap = null;
            System.Drawing.Graphics surface = null;
            bool swapping = (swap != null);
            try
            {
                // make sure we render to the back buffer if the size is
                // changing
                if (swapping) {
                    mapSurface = swap;
                    swap = null;
                } else {
                    mapSurface = (MapSurfaceImage.Source as WriteableBitmap);
                }

                if (mapSurface != null)
                {
                    // lock the bits
                    mapSurface.Lock();

                    // create a bitmap so we can obtain a Grpahics object to
                    // draw on
                    drawingBitmap = new System.Drawing.Bitmap(mapSurface.PixelWidth, mapSurface.PixelHeight,
                                         mapSurface.BackBufferStride,
                                         System.Drawing.Imaging.PixelFormat.Format32bppPArgb,
                                         mapSurface.BackBuffer);
                    surface = System.Drawing.Graphics.FromImage(drawingBitmap);

                    // pass the Graphics off to the renderer
                    mapViewRenderer.Draw(surface);
                }

            }
            catch (Exception wbmpAccess)
            {
                System.Diagnostics.Trace.WriteLine(wbmpAccess.ToString());
            }
            finally
            {
                // cleanup the Graphics and intermediate Bitmap objects
                if (surface != null)
                    surface.Dispose();
                if (drawingBitmap != null)
                    drawingBitmap.Dispose();

                if (mapSurface != null)
                {
                    // mark WritableBitmap we updated as dirty
                    mapSurface.AddDirtyRect(new Int32Rect(0, 0, mapSurface.PixelWidth, mapSurface.PixelHeight));
                    // unlock the bits
                    mapSurface.Unlock();

                    // flip the buffer if we were resizing
                    if (swapping)
                        MapSurfaceImage.Source = mapSurface;
                }
                mapPaintInProgress = false;
            }
        }

        
        public void RepaintMap()
        {
            // issue a repaint if we're not currently repainting
            if (!mapPaintInProgress) {
                try
                {
                    MapSurfaceImage.Dispatcher.BeginInvoke(new InvokeDelegate(PaintMap));
                }
                catch (NullReferenceException e)
                {
                    Console.WriteLine(e);
                }
            }
        }

        private class RepaintThread
        {
            System.Threading.Thread thread;
            MainWindow mapWin;

            public RepaintThread(MainWindow mw)
            {
                thread = new Thread(new ParameterizedThreadStart(Monitor));
                thread.Priority = ThreadPriority.BelowNormal;
                thread.IsBackground = true;
                mapWin = mw;
            }

            protected static void Monitor(object sm)
            {
                RepaintThread mon = (RepaintThread)sm;
                while (true)
                {
                    mon.mapWin.RepaintMap();

                    // target ~30FPS
                    Thread.Sleep(33);
                }
            }

            public void Start()
            {
                thread.Start(this);
            }
        }

        private void MapSurfaceImage_MouseWheel(object sender, MouseWheelEventArgs e)
        {
            // zoom on mousewheel
            if (e.Delta > 0 && mapView.getMapScale() * 2.0 < mapView.getMaxMapScale())
                mapView.getController().zoomTo(mapView.getMapScale() * 2, false);
            else if (e.Delta < 0 && mapView.getMapScale() / 2.0 > mapView.getMinMapScale())
                mapView.getController().zoomTo(mapView.getMapScale() / 2, false);

        }

        private void MapSurfaceImage_MouseMove(object sender, MouseEventArgs e)
        {
            float mouseX = (float)e.GetPosition(MapSurfaceImage).X;
            float mouseY = (float)e.GetPosition(MapSurfaceImage).Y;


            // pan/drag
            if (inPanDrag)
            {
                float tx = lastMouseX - mouseX;
                float ty = lastMouseY - mouseY;

                // use the pixel translate pan method
                mapView.getController().panBy(tx, ty, false);
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
            else if (inRotateDrag)
            {
                atakmap.cpp_cli.math.PointF focus = new atakmap.cpp_cli.math.PointF();
                mapView.getController().getFocusPoint(focus);

                // compute the relative rotation about the focus point
                double curTheta = System.Math.Atan2(mouseY-focus.y, mouseX-focus.x)*180/System.Math.PI;
                double lastTheta = System.Math.Atan2(lastMouseY - focus.y, lastMouseX - focus.x) * 180 / System.Math.PI;

                // update the rotation
                mapView.getController().rotateTo(mapView.getMapRotation() + (curTheta - lastTheta), false);

                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }

            if (infoLayer != null)
            {
                atakmap.cpp_cli.core.GeoPoint mouseGeo = new atakmap.cpp_cli.core.GeoPoint();
                // use the inverse function to convert a pixel coordinate into
                // a geodetic coordinate
                mapView.inverse(new atakmap.cpp_cli.math.PointF(mouseX, mouseY), mouseGeo);
                infoLayer.UpdateCursorPosition(mouseX, mouseY, mouseGeo);
            }
        }

        private void MapSurfaceImage_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            // initiate pan
            inPanDrag = true;

            lastMouseX = (float)e.GetPosition(MapSurfaceImage).X;
            lastMouseY = (float)e.GetPosition(MapSurfaceImage).Y;
        }

        private void MapSurfaceImage_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            // complete pan
            inPanDrag = false;
        }

        private void MapSurfaceImage_MouseRightButtonUp(object sender, MouseButtonEventArgs e)
        {
            // initiate rotate
            inRotateDrag = false;
        }

        private void MapSurfaceImage_MouseRightButtonDown(object sender, MouseButtonEventArgs e)
        {
            // complete rotate
            inRotateDrag = true;

            lastMouseX = (float)e.GetPosition(MapSurfaceImage).X;
            lastMouseY = (float)e.GetPosition(MapSurfaceImage).Y;
        }
    }
}
