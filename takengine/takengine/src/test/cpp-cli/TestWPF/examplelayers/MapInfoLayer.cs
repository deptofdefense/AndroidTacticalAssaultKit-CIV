using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace TestWPF.examplelayers
{
    /// <summary>
    /// Class that will show some basic information about the map. The
    /// information shown is currently driven by the renderer -- the class type
    /// is used to instruct the renderer what layer renderer class should be
    /// selected.
    /// </summary>
    public class MapInfoLayer : atakmap.cpp_cli.core.AbstractLayer
    {
        private System.Collections.Generic.HashSet<CursorListener> listeners;

        public MapInfoLayer() :
            base("Map Info HUD")
        {
            listeners = new HashSet<CursorListener>();
        }

        public void AddCursorListener(CursorListener l)
        {
            System.Threading.Monitor.Enter(listeners);
            listeners.Add(l);
            System.Threading.Monitor.Exit(listeners);
        }

        public void RemoveCursorListener(CursorListener l)
        {
            System.Threading.Monitor.Enter(listeners);
            listeners.Remove(l);
            System.Threading.Monitor.Exit(listeners);
        }

        public void UpdateCursorPosition(double mouseX, double mouseY, atakmap.cpp_cli.core.GeoPoint geo)
        {
            System.Threading.Monitor.Enter(listeners);
            foreach (CursorListener l in listeners)
                l.PositionUpdated(mouseX, mouseY, geo);
            System.Threading.Monitor.Exit(listeners);
        }

        /// <summary>
        /// Callback interface for cursor position
        /// </summary>
        public interface CursorListener
        {
            void PositionUpdated(double mouseX, double mouseY, atakmap.cpp_cli.core.GeoPoint geo);
        }

    }
}
