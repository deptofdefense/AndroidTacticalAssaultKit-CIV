using atakmap.cpp_cli.core;
using atakmap.cpp_cli.math;
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MapRendererWPF.bluemarble
{
    public class WPFBlueMarble : MapRendererWPF.core.WPFMapRenderable
    {

        System.Collections.Generic.SortedDictionary<int, BaseMap> baseMaps;
        double baseResolution;

        public WPFBlueMarble()
        {
            baseMaps = null;
        }

        private BaseMap GetBaseMap(double resolution)
        {
            int rset = (int)(Math.Log(resolution / baseResolution) / Math.Log(2));
            if (rset < 0)
                rset = 0;

            BaseMap retval = null;
            if (baseMaps.TryGetValue(rset, out retval))
                return retval;

            BaseMap src = null;
            for (int level = rset - 1; level > 0; level--)
                if (baseMaps.TryGetValue(level, out src))
                    break;
            if (src == null)
                src = baseMaps[0];

            Image scaled = new Bitmap(src.image, new Size((int)(src.image.Width / (1 << rset)), (int)(src.image.Height / (1 << rset))));
            retval = new BaseMap(scaled);
            baseMaps.Add(rset, retval);

            return retval;
        }

        public void Draw(AtakMapView view, Graphics mapSurface)
        {
            if (baseMaps == null)
            {
                Image r0 = Image.FromFile("bluemarble.jpg");

                baseMaps = new SortedDictionary<int, BaseMap>();
                BaseMap bm = new BaseMap(r0);
                baseResolution = bm.nominalResolution;
                baseMaps.Add(0, bm);
            }

            BaseMap toRender = GetBaseMap(view.getMapResolution());

            GeoPoint geo = new GeoPoint();

            atakmap.cpp_cli.math.PointF ul = new atakmap.cpp_cli.math.PointF();
            geo.latitude = view.getMaxLatitude();
            geo.longitude = view.getMinLongitude();
            view.forward(geo, ul);
            atakmap.cpp_cli.math.PointF ur = new atakmap.cpp_cli.math.PointF();
            geo.latitude = view.getMaxLatitude();
            geo.longitude = view.getMaxLongitude();
            view.forward(geo, ur);
            atakmap.cpp_cli.math.PointF lr = new atakmap.cpp_cli.math.PointF();
            geo.latitude = view.getMinLatitude();
            geo.longitude = view.getMaxLongitude();
            view.forward(geo, lr);
            atakmap.cpp_cli.math.PointF ll = new atakmap.cpp_cli.math.PointF();
            geo.latitude = view.getMinLatitude();
            geo.longitude = view.getMinLongitude();
            view.forward(geo, ll);

            Point[] pts = new Point[3];
            pts[0] = new Point((int)ul.x, (int)ul.y);
            pts[1] = new Point((int)ur.x, (int)ur.y);
            pts[2] = new Point((int)ll.x, (int)ll.y);

            mapSurface.DrawImage(toRender.image, pts);
        }

        public void Release()
        {
            if (baseMaps != null)
            {
                foreach (BaseMap m in baseMaps.Values)
                    m.image.Dispose();
                baseMaps = null;
            }
        }

        private class BaseMap : IComparable<BaseMap>
        {
            public Image image;
            public double nominalResolution;
            public Matrix img2geo;
            public Matrix geo2img;

            public BaseMap(Image i)
            {
                image = i;
                nominalResolution = 6378137.0 / (double)image.Width;

                img2geo = new Matrix();
                Matrix.mapQuads(0, 0,
                        image.Width, 0,
                        image.Width, image.Height,
                        0, image.Height,
                        -180, 90,
                        180, 90,
                        180, -90,
                        -180, -90, img2geo);

                geo2img = new Matrix();
                img2geo.createInverse(geo2img);
            }

            public int CompareTo(BaseMap obj)
            {
                if (this.nominalResolution > obj.nominalResolution)
                    return 1;
                else if (this.nominalResolution < obj.nominalResolution)
                    return -1;
                else
                    return 0;
            }
        }
    }
}
