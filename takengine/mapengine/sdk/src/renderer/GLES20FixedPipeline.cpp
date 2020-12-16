#include <cmath>
#include <cstdlib>
#include <cinttypes>
#include <stdexcept>

#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GL.h"

#include "core/AtakMapView.h"

#include "math/Matrix.h"
#include "util/Logging.h"

#ifdef EMULATE_GL_LINES
#include "renderer/GLLinesEmulation.h"
#endif

using namespace atakmap::math;

namespace
{

void setVertexAttribPointer(const int handle, const atakmap::renderer::ArrayPointer *pointer, bool normalize)
{
    if(!pointer->vbo)
        glVertexAttribPointer(handle, pointer->size, pointer->type, normalize, pointer->stride, pointer->pointer);
    else
        glVertexAttribPointer(handle, pointer->size, pointer->type, normalize, pointer->stride,
                              (GLvoid *)static_cast<intptr_t>(pointer->position));
}

}

namespace atakmap
{
    namespace renderer
    {
        namespace {
            static const char *VECTOR_2D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec2 aVertexCoords;\n"
                "void main() {\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
                "}";

            static const char *VECTOR_3D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec3 aVertexCoords;\n"
                "void main() {\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "}";

            static const char *GENERIC_VECTOR_FRAG_SHADER_SRC =
                "precision mediump float;\n"
                "uniform vec4 uColor;\n"
                "void main(void) {\n"
                "  gl_FragColor = uColor;\n"
                "}";

            static const char *COLOR_POINTER_VECTOR_2D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec2 aVertexCoords;\n"
                "attribute vec4 aColorPointer;\n"
                "varying vec4 vColor;\n"
                "void main() {\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
                "  vColor = aColorPointer;\n"
                "}";

            static const char *COLOR_POINTER_VECTOR_3D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec3 aVertexCoords;\n"
                "attribute vec4 aColorPointer;\n"
                "varying vec4 vColor;\n"
                "void main() {\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "  vColor = aColorPointer;\n"
                "}";

            static const char *COLOR_POINTER_VECTOR_FRAG_SHADER_SRC =
                "precision mediump float;\n"
                "varying vec4 vColor;\n"
                "void main(void) {\n"
                "  gl_FragColor = vColor;\n"
                "}";

            static const char *TEXTURE_2D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "uniform mat4 uTexMatrix;\n"
                "attribute vec2 aVertexCoords;\n"
                "attribute vec2 aTextureCoords;\n"
                "varying vec2 vTexPos;\n"
                "void main() {\n"
                "  vec4 texPos = uTexMatrix * vec4(aTextureCoords.xy, 0.0, 1.0);\n"
                "  vTexPos = texPos.xy;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
                "}";

            static const char *TEXTURE_2D_VERT_SHADER_SRC_PROJ =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "uniform mat4 uTexMatrix;\n"
                "attribute vec4 aVertexCoords;\n"
                "attribute vec4 aTextureCoords;\n"
                "varying vec4 vTexPos;\n"
                "void main() {\n"
                "  vTexPos = uTexMatrix * aTextureCoords;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
                "}";

            static const char *TEXTURE_POINT_2D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "uniform mat4 uTexMatrix;\n"
                "attribute vec2 aVertexCoords;\n"
                "attribute vec2 aTextureCoords;\n"
                "uniform float aPointSize;\n"
                "varying vec2 vTexPos;\n"
                "void main() {\n"
                "  gl_PointSize = aPointSize;\n"
                "  vTexPos = aTextureCoords;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
                "}";

            static const char *POINT_2D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec2 aVertexCoords;\n"
                "uniform float aPointSize;\n"
                "void main() {\n"
                "  gl_PointSize = aPointSize;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
                "}";


            static const char *TEXTURE_3D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "uniform mat4 uTexMatrix;\n"
                "attribute vec3 aVertexCoords;\n"
                "attribute vec2 aTextureCoords;\n"
                "varying vec2 vTexPos;\n"
                "void main() {\n"
                "  vec4 texPos = uTexMatrix * vec4(aTextureCoords.xy, 0.0, 1.0);\n"
                "  vTexPos = texPos.xy;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "}";

            static const char *TEXTURE_3D_VERT_SHADER_SRC_PROJ =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "uniform mat4 uTexMatrix;\n"
                "attribute vec3 aVertexCoords;\n"
                "attribute vec4 aTextureCoords;\n"
                "varying vec4 vTexPos;\n"
                "void main() {\n"
                "  vTexPos = uTexMatrix * aTextureCoords;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "}";

            static const char *TEXTURE_POINT_3D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "uniform mat4 uTexMatrix;\n"
                "attribute vec3 aVertexCoords;\n"
                "attribute vec2 aTextureCoords;\n"
                "uniform float aPointSize;\n"
                "varying vec2 vTexPos;\n"
                "void main() {\n"
                "  gl_PointSize = aPointSize;\n"
                "  vTexPos = aTextureCoords;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "}";


            static const char *POINT_3D_VERT_SHADER_SRC =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec3 aVertexCoords;\n"
                "uniform float aPointSize;\n"
                "void main() {\n"
                "  gl_PointSize = aPointSize;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "}";

            static const char *GENERIC_TEXTURE_FRAG_SHADER_SRC =
                "precision mediump float;\n"
                "uniform sampler2D uTexture;\n"
                "varying vec2 vTexPos;\n"
                "void main(void) {\n"
                "  gl_FragColor = texture2D(uTexture, vTexPos);\n"
                "}";

            static const char *GENERIC_TEXTURE_FRAG_SHADER_SRC_PROJ =
                "precision mediump float;\n"
                "uniform sampler2D uTexture;\n"
                "varying vec4 vTexPos;\n"
                "void main(void) {\n"
                "  gl_FragColor = texture2DProj(uTexture, vTexPos);\n"
                "}";

            static const char *MODULATED_TEXTURE_FRAG_SHADER_SRC =
                "precision mediump float;\n"
                "uniform sampler2D uTexture;\n"
                "uniform vec4 uColor;\n"
                "varying vec2 vTexPos;\n"
                "void main(void) {\n"
                "  gl_FragColor = uColor * texture2D(uTexture, vTexPos);\n"
                "}";

            static const char *MODULATED_TEXTURE_FRAG_SHADER_SRC_PROJ =
                "precision mediump float;\n"
                "uniform sampler2D uTexture;\n"
                "uniform vec4 uColor;\n"
                "varying vec4 vTexPos;\n"
                "void main(void) {\n"
                "  gl_FragColor = uColor * texture2DProj(uTexture, vTexPos);\n"
                "}";

            static const char *TEXTURED_POINT_FRAG_SHADER_SRC =
                "precision mediump float;\n"
                "uniform sampler2D uTexture;\n"
                "varying vec2 vTexPos;\n"
                "void main(void) {\n"
                "  gl_FragColor = texture2D(uTexture, gl_PointCoord);\n"
                "}";

            static const char *POINT_FRAG_SHADER_SRC =
                "precision mediump float;\n"
                "uniform vec4 uColor;\n"
                "void main(void) {\n"
                "  gl_FragColor = uColor;\n"
                "}";

#if 1 // fixes checkGLErrors unused warning
            void checkGLErrors(int line) {
                using namespace atakmap::util;

                int n = 0;
                static int lastok = 0;
                while (true) {
                    GLenum e = glGetError();
                    if (e != GL_NO_ERROR) {
                        Logger::log(Logger::Error, "GL ERROR was pending = %d at %d last ok at %d", e, line, lastok);
                        
                        n++;
                    } else {
                        if (n == 0)
                            lastok = line;
                        break;
                    }
                }
            }
#endif

// Use the following for debugging
//#define CHECKERRS() checkGLErrors(__LINE__)
// Use this for production
#define CHECKERRS()

        }


        GLES20FixedPipeline *GLES20FixedPipeline ::getInstance()
        {
            static GLES20FixedPipeline *singleton = nullptr;
            if (singleton == nullptr)
                singleton = new GLES20FixedPipeline();
            return singleton;
        }


        GLES20FixedPipeline::GLES20FixedPipeline() : current(nullptr), curAttribs(),
            texCoordPointer(), vertexPointer(), colorPointer(),
            activeTextureUnit(GL_TEXTURE0), tex2DEnabled(false)
        {
#ifdef MSVC
            // XXX - 
            modelView = new MatrixStack(256);
            projection = new MatrixStack(256);
            texture = new MatrixStack(256);
#else
            modelView = new MatrixStack(32u);
            projection = new MatrixStack(8u);
            texture = new MatrixStack(4u);
#endif
            attribStack = new std::stack<FPGLSettings>();

        }

        GLES20FixedPipeline::~GLES20FixedPipeline()
        {
            delete modelView;
            delete projection;
            delete texture;
            delete attribStack;

            std::map<int, Program *>::iterator iter;
            for (iter = vectorPrograms.begin(); iter != vectorPrograms.end(); ++iter)
                delete iter->second;
            for (iter = texturePrograms.begin(); iter != texturePrograms.end(); ++iter)
                delete iter->second;
        }


        void GLES20FixedPipeline::glMatrixMode(MatrixMode m)
        {
            switch (m) {
            case MatrixMode::MM_GL_PROJECTION:
                current = projection;
                break;
            case MatrixMode::MM_GL_TEXTURE:
                current = texture;
                break;
            case MatrixMode::MM_GL_MODELVIEW:
                current = modelView;
                break;
            }
        }

        void GLES20FixedPipeline::glActiveTexture(int tex)
        {
            ::glActiveTexture(tex);
            activeTextureUnit = tex;
        }

        int GLES20FixedPipeline::getActiveTexture()
        {
            return activeTextureUnit;
        }

        void GLES20FixedPipeline::glOrthoi(int left, int right,
                                          int bottom, int top, int zNear, int zFar)
        {
            glOrthod((double)left, (double)right, (double)bottom, (double)top, (double)zNear, (double)zFar);
        }
        void GLES20FixedPipeline::glOrthof(float left, float right,
                                           float bottom, float top,
                                           float zNear, float zFar)
        {
            glOrthod(left, right, bottom, top, zNear, zFar);
        }
        void GLES20FixedPipeline::glOrthod(double left, double right,
                                           double bottom, double top,
                                           double zNear, double zFar)
        {
            GLMatrix::orthoM(current->current, 
                static_cast<float>(left), static_cast<float>(right), 
                static_cast<float>(bottom), static_cast<float>(top), 
                static_cast<float>(zNear), static_cast<float>(zFar));
        }


        void GLES20FixedPipeline::glPushMatrix()
        {
            if((current->current-current->matrices)/16 == (current->limit-1))
                throw std::out_of_range("glPushMatrix");
            
            memcpy(current->current+16, current->current, 16*sizeof(float));
            current->current += 16;
        }

        void GLES20FixedPipeline::glPopMatrix()
        {
            if(current->current == current->matrices)
                throw std::out_of_range("glPopMatrix");
            current->current -= 16;
        }

        void GLES20FixedPipeline::glTranslatef(float tx, float ty, float tz)
        {
            GLMatrix::translate(current->current, current->current, tx, ty, tz);
        }


        void GLES20FixedPipeline::glRotatef(float angle, float x, float y, float z)
        {
            GLMatrix::rotate(current->current, current->current, angle, x, y, z);
        }

        void GLES20FixedPipeline::glScalef(float sx, float sy, float sz)
        {
            GLMatrix::scale(current->current, current->current, sx, sy, sz);
        }

        void GLES20FixedPipeline::glMultMatrixf(const float *m)
        {
            GLMatrix::multiply(current->current, current->current, m);
        }

        void GLES20FixedPipeline::glLoadMatrixf(const float *m)
        {
            memcpy(current->current, m, 16*sizeof(float));
        }

        void GLES20FixedPipeline::glLoadIdentity()
        {
            GLMatrix::identity(current->current);
        }

        void GLES20FixedPipeline::glReadMatrix(float *matrix)
        {
            memcpy(matrix, current->current, 16*sizeof(float));
        }

        void GLES20FixedPipeline::readMatrix(MatrixMode m, float *matrix)
        {
            float *mm;
            switch (m) {
            case MatrixMode::MM_GL_MODELVIEW:
                mm = modelView->current;
                break;
            case MatrixMode::MM_GL_PROJECTION:
                mm = projection->current;
                break;
            case MatrixMode::MM_GL_TEXTURE:
                mm = texture->current;
                break;
			default :
				memset(matrix, 0u, sizeof(float) * 16);
				return;
            }
            memcpy(matrix, mm, 16*sizeof(float));
        }

        void GLES20FixedPipeline::getColor(float *f)
        {
            f[0] = curAttribs.r;
            f[1] = curAttribs.g;
            f[2] = curAttribs.b;
            f[3] = curAttribs.a;
        }

        void GLES20FixedPipeline::glColor4f(float r, float g, float b, float a)
        {
            curAttribs.r = r;
            curAttribs.g = g;
            curAttribs.b = b;
            curAttribs.a = a;
        }

        void GLES20FixedPipeline::glColor4x(int r, int g, int b, int a)
        {
            glColor4f((float)r / 255.0f, (float)g / 255.0f, (float)b / 255.0f, (float)a / 255.0f);
        }

        void GLES20FixedPipeline::setEnableTex2D(bool en)
        {
            tex2DEnabled = en;
        }

        bool GLES20FixedPipeline::isTex2DEnabled()
        {
            return tex2DEnabled;
        }

        void GLES20FixedPipeline::glEnableClientState(ClientState state)
        {
            using namespace atakmap::util;

            ArrayPointer *p = nullptr;
            if (state == ClientState::CS_GL_VERTEX_ARRAY)
                p = &vertexPointer;
            else if (state == ClientState::CS_GL_TEXTURE_COORD_ARRAY)
                p = &texCoordPointer;
            else if (state == ClientState::CS_GL_COLOR_ARRAY)
                p = &colorPointer;
            if (p != nullptr)
                p->enabled = true;
        }

        void GLES20FixedPipeline::glDisableClientState(ClientState state)
        {
            ArrayPointer *p = nullptr;
            if (state == ClientState::CS_GL_VERTEX_ARRAY)
                p = &vertexPointer;
            else if (state == ClientState::CS_GL_TEXTURE_COORD_ARRAY)
                p = &texCoordPointer;
            else if (state == ClientState::CS_GL_COLOR_ARRAY)
                p = &colorPointer;
            if (p != nullptr) {
                p->enabled = false;
                p->pointer = nullptr;
                p->vbo = false;
            }
        }

        void GLES20FixedPipeline::glVertexPointer(int size, int type, int stride, const void *buffer)
        {
            vertexPointer.setPointer(size, type, stride, buffer);
        }
        void GLES20FixedPipeline::glVertexPointer(int size, int type, int stride, int position)
        {
            vertexPointer.setPointer(size, type, stride, position);
        }
        void GLES20FixedPipeline::glTexCoordPointer(int size, int type, int stride, const void *buffer)
        {
            texCoordPointer.setPointer(size, type, stride, buffer);
        }
        void GLES20FixedPipeline::glTexCoordPointer(int size, int type, int stride, int position)
        {
            texCoordPointer.setPointer(size, type, stride, position);
        }
        void GLES20FixedPipeline::glColorPointer(int size, int type, int stride, const void *buffer)
        {
            colorPointer.setPointer(size, type, stride, buffer);
        }
        void GLES20FixedPipeline::glColorPointer(int size, int type, int stride, int position)
        {
            colorPointer.setPointer(size, type, stride, position);
        }

        float GLES20FixedPipeline::getPointSize()
        {
            return curAttribs.pointSize;
        }

        void GLES20FixedPipeline::glPointSize(float size)
        {
            curAttribs.pointSize = size;
        }

        void GLES20FixedPipeline::glLineWidth(float w)
        { 
            ::glLineWidth(w * atakmap::core::AtakMapView::DENSITY);
        }

        void GLES20FixedPipeline::pushAllAttribs()
        {
            attribStack->push(curAttribs);
        }
        void GLES20FixedPipeline::popAllAttribs()
        {
            curAttribs = attribStack->top();
            attribStack->pop();
        }

        void GLES20FixedPipeline::glDrawArrays(int mode, int first, int count)
        {
            CHECKERRS();
#if 0
            // XXX - should be handled internally by drawBoundTexture
            if (vertexPointer->enabled && !vertexPointer->pointer) // VBO
                drawBoundTexture(mode, activeTextureUnit - GLES20.GL_TEXTURE0, count, vertexPointer,
                texCoordPointer);
            else
#endif
            // XXX - ATAK rendering code never explicitly enables texturing
            if (mode == GL_POINTS) {
                drawPoints(activeTextureUnit - GL_TEXTURE0, first, count);
            }
            else if ((/*tex2DEnabled && */texCoordPointer.enabled)) {
                drawBoundTexture(mode, activeTextureUnit - GL_TEXTURE0, first, count);
            } 
            else if (vertexPointer.enabled) {
#ifdef EMULATE_GL_LINES
                switch (mode) {
                case GL_LINES :
                case GL_LINE_STRIP :
                case GL_LINE_LOOP:
                {
                    float width;
                    glGetFloatv(GL_LINE_WIDTH, &width);

                    if (width > 1) {
                        GLLinesEmulation::emulateLineDrawArrays(mode, first, count, this, width, vertexPointer.enabled ? &vertexPointer : nullptr, texCoordPointer.enabled ? &texCoordPointer : nullptr);
                        break;
                    }
                }
                default :
                    drawGenericVector(mode, first, count);
                    break;
                }
#else
                drawGenericVector(mode, first, count);
#endif
            }
            CHECKERRS();
        }

        void GLES20FixedPipeline::glDrawElements(int mode, int count, int type, const void *indices)
        {
            // XXX - ATAK rendering code never explicitly enables texturing
            if (mode == GL_POINTS)
                drawPoints(activeTextureUnit - GL_TEXTURE0, count, type, indices);
            else if ((/*tex2DEnabled && */texCoordPointer.enabled))
                drawBoundTexture(mode, activeTextureUnit - GL_TEXTURE0, count, type, indices);
			else if (vertexPointer.enabled) {
#ifdef EMULATE_GL_LINES
				switch (mode) {
				case GL_LINES:
				case GL_LINE_STRIP:
				case GL_LINE_LOOP:
				{
					float width;
					glGetFloatv(GL_LINE_WIDTH, &width);

					if (width > 1) {
						GLLinesEmulation::emulateLineDrawElements(mode, count, type, indices, this, width, vertexPointer.enabled ? &vertexPointer : nullptr, texCoordPointer.enabled ? &texCoordPointer : nullptr);
						break;
					}
				}
				default:
					drawGenericVector(mode, count, type, indices);
					break;
				}
#else
				drawGenericVector(mode, count, type, indices);
#endif

			}
        }


        void GLES20FixedPipeline::drawGenericVector(int mode, int first, int count) {
            drawGenericVectorImpl(mode, first, count, colorPointer.enabled);
        }

        void GLES20FixedPipeline::drawGenericVector(int mode, int count, int type,
                                                    const void *indices) {
            drawGenericVectorImpl(mode, count, type, indices, colorPointer.enabled);
        }

        void GLES20FixedPipeline::drawGenericVectorImpl(int mode, int first, int count, bool useColorPtr)
        {
            int aColorPointerHandle = 0;
            int programHandle = getGenericVectorProgram(vertexPointer.size, useColorPtr);

            int prevProgram;
            glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
            glUseProgram(programHandle);

            float p[16];
            int uProjectionHandle = glGetUniformLocation(programHandle, "uProjection");
            readMatrix(MatrixMode::MM_GL_PROJECTION, p);
            glUniformMatrix4fv(uProjectionHandle, 1, false, p);
            CHECKERRS();

            int uModelViewHandle = glGetUniformLocation(programHandle, "uModelView");
            readMatrix(MatrixMode::MM_GL_MODELVIEW, p);
            glUniformMatrix4fv(uModelViewHandle, 1, false, p);
            CHECKERRS();
            
            int aVertexCoordsHandle = glGetAttribLocation(programHandle, "aVertexCoords");
            glEnableVertexAttribArray(aVertexCoordsHandle);

            setVertexAttribPointer(aVertexCoordsHandle, &vertexPointer, false);

            if (!useColorPtr) {
                int uColorHandle = glGetUniformLocation(programHandle, "uColor");
                glUniform4f(uColorHandle, curAttribs.r, curAttribs.g, curAttribs.b, curAttribs.a);
            } else {
                aColorPointerHandle = glGetAttribLocation(programHandle, "aColorPointer");
                glEnableVertexAttribArray(aColorPointerHandle);

                setVertexAttribPointer(aColorPointerHandle, &colorPointer, true);
            }

            ::glDrawArrays(mode, first, count);

            glDisableVertexAttribArray(aVertexCoordsHandle);
            if (useColorPtr)
                glDisableVertexAttribArray(aColorPointerHandle);
            glUseProgram(prevProgram);
        }


        void GLES20FixedPipeline::drawGenericVectorImpl(int mode, int count, int type, const void *indices, bool useColorPtr) {
            int aColorPointerHandle = 0;
            int programHandle = getGenericVectorProgram(vertexPointer.size, false);
            int prevProgram;
            glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
            glUseProgram(programHandle);

            float p[16];
            int uProjectionHandle = glGetUniformLocation(programHandle, "uProjection");
            readMatrix(MatrixMode::MM_GL_PROJECTION, p);
            glUniformMatrix4fv(uProjectionHandle, 1, false, p);
            CHECKERRS();

            int uModelViewHandle = glGetUniformLocation(programHandle, "uModelView");
            readMatrix(MatrixMode::MM_GL_MODELVIEW, p);
            glUniformMatrix4fv(uModelViewHandle, 1, false, p);
            CHECKERRS();

            int aVertexCoordsHandle = glGetAttribLocation(programHandle, "aVertexCoords");
            glEnableVertexAttribArray(aVertexCoordsHandle);

            setVertexAttribPointer(aVertexCoordsHandle, &vertexPointer, false);

            if (!useColorPtr) {
                int uColorHandle = glGetUniformLocation(programHandle, "uColor");
                glUniform4f(uColorHandle, curAttribs.r, curAttribs.g, curAttribs.b, curAttribs.a);
            } else {
                aColorPointerHandle = glGetAttribLocation(programHandle, "aColorPointer");
                glEnableVertexAttribArray(aColorPointerHandle);

                setVertexAttribPointer(aColorPointerHandle, &colorPointer, true);
            }
            CHECKERRS();

            ::glDrawElements(mode, count, type, indices);

            glDisableVertexAttribArray(aVertexCoordsHandle);
            if (useColorPtr)
                glDisableVertexAttribArray(aColorPointerHandle);
            glUseProgram(prevProgram);

        }


        void GLES20FixedPipeline::drawBoundTexture(int mode, int textureUnit, int first, int count)
        {
            bool colorize = (curAttribs.r != 1.0f || curAttribs.g != 1.0f || curAttribs.b != 1.0f || curAttribs.a != 1.0f);
            int programHandle = getGenericTextureProgram(vertexPointer.size, texCoordPointer.size,
                                                         colorize);
            CHECKERRS();
            int prevProgram;
            glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
            glUseProgram(programHandle);

            CHECKERRS();

            float p[16];
            int uProjectionHandle = glGetUniformLocation(programHandle, "uProjection");
            readMatrix(MatrixMode::MM_GL_PROJECTION, p);
            glUniformMatrix4fv(uProjectionHandle, 1, false, p);
            CHECKERRS();

            int uModelViewHandle = glGetUniformLocation(programHandle, "uModelView");
            readMatrix(MatrixMode::MM_GL_MODELVIEW, p);
            glUniformMatrix4fv(uModelViewHandle, 1, false, p);
            CHECKERRS();

            int uTexMatrixHandle = glGetUniformLocation(programHandle, "uTexMatrix");
            readMatrix(MatrixMode::MM_GL_TEXTURE, p);
            glUniformMatrix4fv(uTexMatrixHandle, 1, false, p);
            CHECKERRS();

            int uTextureHandle = glGetUniformLocation(programHandle, "uTexture");
            glUniform1i(uTextureHandle, textureUnit);
            CHECKERRS();

            if (colorize) {
                int uColorHandle = glGetUniformLocation(programHandle, "uColor");
                glUniform4f(uColorHandle, curAttribs.r, curAttribs.g, curAttribs.b, curAttribs.a);
            }
            CHECKERRS();

            int aVertexCoordsHandle = glGetAttribLocation(programHandle, "aVertexCoords");
            int aTextureCoordsHandle = glGetAttribLocation(programHandle, "aTextureCoords");
            CHECKERRS();

            setVertexAttribPointer(aVertexCoordsHandle, &vertexPointer, false);

            glEnableVertexAttribArray(aVertexCoordsHandle);
            CHECKERRS();

            setVertexAttribPointer(aTextureCoordsHandle, &texCoordPointer, false);
            glEnableVertexAttribArray(aTextureCoordsHandle);
            CHECKERRS();

            ::glDrawArrays(mode, first, count);
            CHECKERRS();

            glDisableVertexAttribArray(aVertexCoordsHandle);
            glDisableVertexAttribArray(aTextureCoordsHandle);
            CHECKERRS();
            glUseProgram(prevProgram);
        }

        void GLES20FixedPipeline::drawBoundTexture(int mode, int textureUnit, int count, int type, const void *indices)
        {
            bool colorize = (curAttribs.r != 1.0f || curAttribs.g != 1.0f || curAttribs.b != 1.0f || curAttribs.a != 1.0f);
            int programHandle = getGenericTextureProgram(vertexPointer.size, texCoordPointer.size, colorize);
            glUseProgram(programHandle);

            float p[16];
            int uProjectionHandle = glGetUniformLocation(programHandle, "uProjection");
            readMatrix(MatrixMode::MM_GL_PROJECTION, p);
            glUniformMatrix4fv(uProjectionHandle, 1, false, p);
            CHECKERRS();

            int uModelViewHandle = glGetUniformLocation(programHandle, "uModelView");
            readMatrix(MatrixMode::MM_GL_MODELVIEW, p);
            glUniformMatrix4fv(uModelViewHandle, 1, false, p);
            CHECKERRS();

            int uTexMatrixHandle = glGetUniformLocation(programHandle, "uTexMatrix");
            readMatrix(MatrixMode::MM_GL_TEXTURE, p);
            glUniformMatrix4fv(uTexMatrixHandle, 1, false, p);
            CHECKERRS();

            int uTextureHandle = glGetUniformLocation(programHandle, "uTexture");
            glUniform1i(uTextureHandle, textureUnit);
            CHECKERRS();

            if (colorize) {
                int uColorHandle = glGetUniformLocation(programHandle, "uColor");
                glUniform4f(uColorHandle, curAttribs.r, curAttribs.g, curAttribs.b, curAttribs.a);
            }
            CHECKERRS();

            int aVertexCoordsHandle = glGetAttribLocation(programHandle, "aVertexCoords");
            int aTextureCoordsHandle = glGetAttribLocation(programHandle, "aTextureCoords");

            setVertexAttribPointer(aVertexCoordsHandle, &vertexPointer, false);

            glEnableVertexAttribArray(aVertexCoordsHandle);

            setVertexAttribPointer(aTextureCoordsHandle, &texCoordPointer, false);
            glEnableVertexAttribArray(aTextureCoordsHandle);

            ::glDrawElements(mode, count, type, indices);

            glDisableVertexAttribArray(aVertexCoordsHandle);
            glDisableVertexAttribArray(aTextureCoordsHandle);
        }

        void GLES20FixedPipeline::drawPoints(int textureUnit, int first, int count)
        {
            int programHandle = getGenericPointProgram(vertexPointer.size, texCoordPointer.enabled);
            CHECKERRS();
            int prevProgram;
            
            glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
            
            glUseProgram(programHandle);

            CHECKERRS();

            float p[16];
            int uProjectionHandle = glGetUniformLocation(programHandle, "uProjection");
            readMatrix(MatrixMode::MM_GL_PROJECTION, p);
            glUniformMatrix4fv(uProjectionHandle, 1, false, p);
            CHECKERRS();

            int uModelViewHandle = glGetUniformLocation(programHandle, "uModelView");
            readMatrix(MatrixMode::MM_GL_MODELVIEW, p);
            glUniformMatrix4fv(uModelViewHandle, 1, false, p);
            CHECKERRS();

            if (texCoordPointer.enabled) {
                int uTexMatrixHandle = glGetUniformLocation(programHandle, "uTexMatrix");
                readMatrix(MatrixMode::MM_GL_TEXTURE, p);
                glUniformMatrix4fv(uTexMatrixHandle, 1, false, p);
                CHECKERRS();

                int uTextureHandle = glGetUniformLocation(programHandle, "uTexture");
                glUniform1i(uTextureHandle, textureUnit);
                CHECKERRS();
            } else {
                int uColorHandle = glGetUniformLocation(programHandle, "uColor");
                glUniform4f(uColorHandle, curAttribs.r, curAttribs.g, curAttribs.b, curAttribs.a);
            }
            CHECKERRS();

            int aPointSizeHandle = glGetUniformLocation(programHandle, "aPointSize");
            glUniform1f(aPointSizeHandle, curAttribs.pointSize);

            CHECKERRS();

            int aVertexCoordsHandle = glGetAttribLocation(programHandle, "aVertexCoords");
            setVertexAttribPointer(aVertexCoordsHandle, &vertexPointer, false);
            CHECKERRS();

            int aTextureCoordsHandle = 0;
            if (texCoordPointer.enabled) {
                // XXX - legacy comment: Texture coords aren't needed for GL_POINTS
                aTextureCoordsHandle = glGetAttribLocation(programHandle, "aTextureCoords");
                setVertexAttribPointer(aTextureCoordsHandle, &texCoordPointer, false);
                glEnableVertexAttribArray(aTextureCoordsHandle);
                CHECKERRS();
            }

            ::glDrawArrays(GL_POINTS, first, count);
            CHECKERRS();

            glDisableVertexAttribArray(aVertexCoordsHandle);
            if (texCoordPointer.enabled) {
                glDisableVertexAttribArray(aTextureCoordsHandle);
            }
            glUseProgram(prevProgram);
            CHECKERRS();
        }

        void GLES20FixedPipeline::drawPoints(int textureUnit, int count, int type, const void *indices)
        {
            int programHandle = getGenericPointProgram(vertexPointer.size, texCoordPointer.enabled);
            glUseProgram(programHandle);

            float p[16];
            int uProjectionHandle = glGetUniformLocation(programHandle, "uProjection");
            readMatrix(MatrixMode::MM_GL_PROJECTION, p);
            glUniformMatrix4fv(uProjectionHandle, 1, false, p);
            CHECKERRS();

            int uModelViewHandle = glGetUniformLocation(programHandle, "uModelView");
            readMatrix(MatrixMode::MM_GL_MODELVIEW, p);
            glUniformMatrix4fv(uModelViewHandle, 1, false, p);
            CHECKERRS();

            if (texCoordPointer.enabled) {
                int uTexMatrixHandle = glGetUniformLocation(programHandle, "uTexMatrix");
                readMatrix(MatrixMode::MM_GL_TEXTURE, p);
                glUniformMatrix4fv(uTexMatrixHandle, 1, false, p);
                CHECKERRS();
                int uTextureHandle = glGetUniformLocation(programHandle, "uTexture");
                glUniform1i(uTextureHandle, textureUnit);
                CHECKERRS();
            } else {
                int uColorHandle = glGetUniformLocation(programHandle, "uColor");
                glUniform4f(uColorHandle, curAttribs.r, curAttribs.g, curAttribs.b, curAttribs.a);
            }
            CHECKERRS();

            int aPointSizeHandle = glGetUniformLocation(programHandle, "aPointSize");
            glUniform1f(aPointSizeHandle, curAttribs.pointSize);

            int aVertexCoordsHandle = glGetAttribLocation(programHandle, "aVertexCoords");
            setVertexAttribPointer(aVertexCoordsHandle, &vertexPointer, false);
            glEnableVertexAttribArray(aVertexCoordsHandle);

            int aTextureCoordsHandle = 0;
            if (texCoordPointer.enabled) {
                aTextureCoordsHandle = glGetAttribLocation(programHandle, "aTextureCoords");
                setVertexAttribPointer(aTextureCoordsHandle, &texCoordPointer, false);
                glEnableVertexAttribArray(aTextureCoordsHandle);
            }

            ::glDrawElements(GL_POINTS, count, type, indices);

            glDisableVertexAttribArray(aVertexCoordsHandle);
            if (texCoordPointer.enabled) {
                glDisableVertexAttribArray(aTextureCoordsHandle);
            }
        }

        int GLES20FixedPipeline::getGenericVectorProgram(int size, bool colorize)
        {
            int colorFlags = colorize ? 0x00 : 0x01;
            int programFlags = colorFlags | (size << 1);
            Program *p = vectorPrograms[programFlags];
            if (p == nullptr) {
                const char *vertShaderSource;
                const char *fragShaderSource;
                if (colorize) {
                    switch (size) {
                    case 2:
                        vertShaderSource = COLOR_POINTER_VECTOR_2D_VERT_SHADER_SRC;
                        break;
                    case 3:
                        vertShaderSource = COLOR_POINTER_VECTOR_3D_VERT_SHADER_SRC;
                        break;
                    default:
                        return GL_FALSE;
                    }
                    fragShaderSource = COLOR_POINTER_VECTOR_FRAG_SHADER_SRC;
                } else {
                    switch (size) {
                    case 2:
                        vertShaderSource = VECTOR_2D_VERT_SHADER_SRC;
                        break;
                    case 3:
                        vertShaderSource = VECTOR_3D_VERT_SHADER_SRC;
                        break;
                    default:
                        return GL_FALSE;
                    }
                    fragShaderSource = GENERIC_VECTOR_FRAG_SHADER_SRC;
                }
                p = new Program();
                if (p->create(vertShaderSource, fragShaderSource) == GL_FALSE) {
                    delete p;
                    return GL_FALSE;
                }
                vectorPrograms[programFlags] = p;
            }
            return p->program;
        }

        int GLES20FixedPipeline::getGenericPointProgram(int size, bool textured)
        {
            int texturedFlag = textured ? 0x01 : 0x00;
            int programFlags = (size << 1) | texturedFlag;
            Program *p = pointPrograms[programFlags];
            if (p == nullptr) {
                const char *vertShaderSource;
                const char *fragShaderSource;
                if (textured) {
                    switch (size) {
                    case 2:
                        vertShaderSource = TEXTURE_POINT_2D_VERT_SHADER_SRC;
                        break;
                    case 3:
                        vertShaderSource = TEXTURE_POINT_3D_VERT_SHADER_SRC;
                        break;
                    default:
                        return GL_FALSE;
                    }
                    fragShaderSource = TEXTURED_POINT_FRAG_SHADER_SRC;
                }
                else {
                    switch (size) {
                    case 2:
                        vertShaderSource = POINT_2D_VERT_SHADER_SRC;
                        break;
                    case 3:
                        vertShaderSource = POINT_3D_VERT_SHADER_SRC;
                        break;
                    default:
                        return GL_FALSE;
                    }
                    fragShaderSource = POINT_FRAG_SHADER_SRC;
                }
                p = new Program();
                if (p->create(vertShaderSource, fragShaderSource) == GL_FALSE) {
                    delete p;
                    return GL_FALSE;
                }
                pointPrograms[programFlags] = p;
            }
            return p->program;
        }

        int GLES20FixedPipeline::getGenericTextureProgram(int vSize, int tcSize, bool colorize)
        {
            if ((tcSize != 4 && tcSize != 2) || vSize < 0 || vSize > 3)
                return GL_FALSE;
            int colorFlag = colorize ? 0x01 : 0x00;
            int programFlags = colorFlag | (0 << 1) | (vSize << 2) | (tcSize << 4);
            Program *p = texturePrograms[programFlags];
            if (p == nullptr) {
                const char *vertShaderSource = nullptr;
                switch (vSize) {
                case 2:
                    vertShaderSource = (tcSize == 4) ? TEXTURE_2D_VERT_SHADER_SRC_PROJ : TEXTURE_2D_VERT_SHADER_SRC;
                    break;
                case 3:
                    vertShaderSource = (tcSize == 4) ? TEXTURE_3D_VERT_SHADER_SRC_PROJ : TEXTURE_3D_VERT_SHADER_SRC;
                    break;
                default:
                    return GL_FALSE;
                }
                const char *fragShaderSource;
                if (colorize)
                    fragShaderSource = (tcSize == 4) ? MODULATED_TEXTURE_FRAG_SHADER_SRC_PROJ : MODULATED_TEXTURE_FRAG_SHADER_SRC;
                else if (tcSize == 4)
                    fragShaderSource = GENERIC_TEXTURE_FRAG_SHADER_SRC_PROJ;
                else
                    fragShaderSource = GENERIC_TEXTURE_FRAG_SHADER_SRC;

                p = new Program();
                if (p->create(vertShaderSource, fragShaderSource) == GL_FALSE) {
                    delete p;
                    return GL_FALSE;
                }
                texturePrograms[programFlags] = p;
            }
            return p->program;
        }



        ArrayPointer::ArrayPointer() : size(0), type(0), stride(0), pointer(nullptr), enabled(false), position(0), vbo(false)
        {

        }

        void ArrayPointer::setPointer(int size_val, int type_val, int stride_val, const void *pointer_val)
        {
            this->size = size_val;
            this->type = type_val;
            this->stride = stride_val;
            this->pointer = pointer_val;
            this->position = 0;
            this->vbo = false;
        }

        void ArrayPointer::setPointer(int size_val, int type_val, int stride_val, int position_val)
        {
            this->size = size_val;
            this->type = type_val;
            this->stride = stride_val;
            this->pointer = nullptr;
            this->position = position_val;
            this->vbo = true;
        }


        MatrixStack::MatrixStack(size_t n) : limit(n)
        {
            matrices = new float[16*limit];
            current = matrices;
            GLMatrix::identity(current);
        }

        MatrixStack::~MatrixStack()
        {
            delete [] matrices;
        }

        Program::Program() : program(GL_FALSE), vertShader(GL_FALSE), fragShader(GL_FALSE)
        {
        }
        Program::~Program()
        {
            destroy();
        }

        bool Program::create(const char *vertSrc, const char *fragSrc)
        {
            vertShader = loadShader(vertSrc, GL_VERTEX_SHADER);
            if (vertShader == GL_FALSE)
                return false;
            fragShader = loadShader(fragSrc, GL_FRAGMENT_SHADER);
            if (fragShader == GL_FALSE) {
                glDeleteShader(vertShader);
                vertShader = GL_FALSE;
                return false;
            }

            program = createProgram();
            if (program == GL_FALSE) {
                glDeleteShader(fragShader);
                glDeleteShader(vertShader);
                fragShader = vertShader = GL_FALSE;
                return false;
            }
            return true;
        }

        int Program::loadShader(const char *src, int type)
        {
            using namespace atakmap::util;

            int n = glCreateShader(type);
            CHECKERRS();
            if (n == GL_FALSE)
                return n;
            glShaderSource(n, 1, &src, nullptr);
            CHECKERRS();
            glCompileShader(n);
            CHECKERRS();
            int rc = 0;
            glGetShaderiv(n, GL_COMPILE_STATUS, &rc);
            CHECKERRS();
            if (rc == 0) {
                char *msg = nullptr;
                int msgLen;
                glGetShaderiv(n, GL_INFO_LOG_LENGTH, &msgLen);
                if(msgLen) {
                    msg = new char[msgLen+1];
                    glGetShaderInfoLog(n, msgLen+1, nullptr, msg);
                }
                Logger::log(Logger::Error, "Failed to compile shader %d, src:\n%s\nmsg: %s", n, src, msg);
                if(msg)
                    delete [] msg;
                glDeleteShader(n);
                return GL_FALSE;
            }
            return n;
        }

        int Program::createProgram()
        {
            return createProgram(vertShader, fragShader);
        }
         
        int Program::createProgram(int vertShader, int fragShader)
        {
            using namespace atakmap::util;

            int n = glCreateProgram();
            CHECKERRS();
            if (n == GL_FALSE)
                return n;
            glAttachShader(n, vertShader);
            CHECKERRS();
            glAttachShader(n, fragShader);
            CHECKERRS();
            glLinkProgram(n);
            CHECKERRS();
            int ok = 0;
            glGetProgramiv(n, GL_LINK_STATUS, &ok);
            CHECKERRS();
            if (ok == 0) {
                char *msg = nullptr;
                int msgLen;
                glGetProgramiv(n, GL_INFO_LOG_LENGTH, &msgLen);
                if(msgLen) {
                    msg = new char[msgLen+1];
                    glGetProgramInfoLog(n, msgLen+1, nullptr, msg);
                }
                Logger::log(Logger::Error, "Failed to create program, vertShader=%d fragShader=%d\nmsg: %s", vertShader, fragShader, msg);
                if(msg)
                    delete [] msg;
                glDeleteProgram(n);
                return GL_FALSE;
            }
            return n;
        }
        void Program::destroy()
        {
            if (program != GL_FALSE) {
                glDeleteProgram(program);
                glDeleteShader(vertShader);
                glDeleteShader(fragShader);
                program = vertShader = fragShader = GL_FALSE;
            }
        }

        FPGLSettings::FPGLSettings() : r(1), g(1), b(1), a(1), pointSize(1) {
        }
    }
}