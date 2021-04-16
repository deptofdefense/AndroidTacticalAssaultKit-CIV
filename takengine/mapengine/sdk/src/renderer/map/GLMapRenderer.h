#ifndef ATAKMAP_RENDERER_GLMAPRENDERER_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPRENDERER_H_INCLUDED

#include "GLMapView.h"
#include <inttypes.h>

namespace atakmap
{
    namespace renderer
    {
        namespace map {

            namespace StubbedFuncs {
                int64_t uptimeMillis();
                void curThreadSleep(long millis);
            }



            class GLMapRenderer {
            public:
                GLMapRenderer();
                void setView(GLMapView *view);
                void setFrameRate(float frameRate);
                void pauseRender(bool state);
                void onDrawFrame();
                void onSurfaceChanged(int width, int height);
                void onSurfaceCreated();
                void setBgColor(int color);



            private:

                GLMapView *mapView;
                int64_t targetMillisPerFrame;
                int64_t timeCall;
                int64_t lastCall;
                int64_t currCall;
                int64_t count = 0;
                float bgRed;
                float bgGreen;
                float bgBlue;

                int64_t lastReport = 0L;
                bool pause;



            };

        }
    }
}


#endif
