#include "GLMapRenderer.h"
#include "../GLES20FixedPipeline.h"
#include "../RendererUtils.h"

#include <math.h>
#include "renderer/GL.h"

#ifdef __APPLE__
#include <sys/types.h>
#include <sys/sysctl.h>

#define MIB_SIZE 2
#endif

namespace atakmap {
    namespace renderer {
        namespace map {


            GLMapRenderer::GLMapRenderer() : mapView(nullptr), targetMillisPerFrame(0),
                timeCall(0), lastCall(0), currCall(0), count(0), bgRed(0.0f),
                bgGreen(0.0f), bgBlue(0.0f), lastReport(0), pause(false)
            {
            }

            void GLMapRenderer::setView(GLMapView *view) {
                mapView = view;
            }

            void GLMapRenderer::setFrameRate(float frameRate) {
                // set the number of milliseconds we want to spend rendering each frame
                // based on the specified frame rate
                if (frameRate <= 0.0f)
                    targetMillisPerFrame = 0;
                else
                    targetMillisPerFrame = (long)ceil(1000.0f / frameRate);
            }


            /**
            * XXX: This is a very very bad solution to a problem.
            */
            void GLMapRenderer::pauseRender(bool state) {
                pause = state;
            }


            void GLMapRenderer::onDrawFrame() {
                GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

#if 0
                if (count == 0) {
                    timeCall = StubbedFuncs::uptimeMillis();
                } else if (count > 1000) {
                    //Log.v(TAG, "map framerate (f/s) = " +
                    //        (1000000.0 / (SystemClock.uptimeMillis() - timeCall)));
                    timeCall = StubbedFuncs::uptimeMillis();
                    count = 0;
                    lastReport = timeCall;
                } else if ((StubbedFuncs::uptimeMillis() - lastReport) > 1000) {
                    //Log.v(TAG, "map framerate (f/s) = " +
                    //        ((count * 1000.0d) / (SystemClock.uptimeMillis() - timeCall)));
                    lastReport = StubbedFuncs::uptimeMillis();

                    if ((StubbedFuncs::uptimeMillis() - timeCall) > 5000) {
                        timeCall = StubbedFuncs::uptimeMillis();
                        count = 0;
                        lastReport = timeCall;
                    }
                }
#endif
                count++;

                glClearColor(bgRed, bgGreen, bgBlue, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

                fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
                fixedPipe->glLoadIdentity();

                this->mapView->animationDelta = this->mapView->animationLastTick;
                this->mapView->animationLastTick = StubbedFuncs::uptimeMillis();
                
                mapView->render();

                // slows the pipeline down to effect the desired frame rate
#if 0
                currCall = StubbedFuncs::uptimeMillis();
                int64_t frameElapsed = (currCall - lastCall);
                if (targetMillisPerFrame > frameElapsed)
                    StubbedFuncs::curThreadSleep((long)(targetMillisPerFrame - frameElapsed));
                lastCall = currCall;
#endif
            }

            void GLMapRenderer::onSurfaceChanged(int width, int height) {
                GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

                glViewport(0, 0, width, height);
                fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION); // select projection
                // matrix
                fixedPipe->glLoadIdentity(); // reset projection matrix
                fixedPipe->glOrthof(0.0f, (float)width, 0.0f, (float)height, 1.0f, -1.0f);
                fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
                fixedPipe->glLoadIdentity();

                // update the GLMapView bounds when the surface changes
                mapView->left = 0;
                mapView->bottom = 0;
                mapView->right = width - 1;
                mapView->top = height - 1;
            }

            void GLMapRenderer::onSurfaceCreated() {
            }

            void GLMapRenderer::setBgColor(int color) {
                bgRed = Utils::colorExtract(color, Utils::RED) / 255.0f;
                bgGreen = Utils::colorExtract(color, Utils::GREEN) / 255.0f;
                bgBlue = Utils::colorExtract(color, Utils::BLUE) / 255.0f;
            }

            int64_t StubbedFuncs::uptimeMillis() {
#ifdef __APPLE__
                int mib[MIB_SIZE];
                size_t size;
                struct timeval  boottime;
                
                mib[0] = CTL_KERN;
                mib[1] = KERN_BOOTTIME;
                size = sizeof(boottime);
                if (sysctl(mib, MIB_SIZE, &boottime, &size, NULL, 0) != -1) {
                    return boottime.tv_sec * 1000 + boottime.tv_usec / 1000;
                }
                return 0;
#else
                return 0;
#endif
            }
            void StubbedFuncs::curThreadSleep(long millis) {
                
            }


        }
    }
}
