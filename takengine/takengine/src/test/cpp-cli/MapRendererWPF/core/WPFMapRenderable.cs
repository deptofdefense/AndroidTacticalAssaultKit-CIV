using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MapRendererWPF.core
{
    public interface WPFMapRenderable
    {
        void Draw(atakmap.cpp_cli.core.AtakMapView view, System.Drawing.Graphics mapSurface);
        void Release();
    }
}
