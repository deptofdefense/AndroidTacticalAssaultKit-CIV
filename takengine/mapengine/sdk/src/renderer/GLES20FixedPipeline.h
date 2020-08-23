#ifndef ATAKMAP_RENDERER_GLES20FIXEDPIPELINE_H_INCLUDED
#define ATAKMAP_RENDERER_GLES20FIXEDPIPELINE_H_INCLUDED

#include "GLMatrix.h"

#include <map>
#include <stack>

#include "port/Platform.h"

namespace atakmap
{
    namespace math
    {
        class ENGINE_API Matrix;
    }
}

namespace atakmap
{
    namespace renderer
    {
        struct ENGINE_API MatrixStack
        {
            MatrixStack(size_t n);
            ~MatrixStack();
            const size_t limit;
            float *current;
            float *matrices;
        };

        struct ENGINE_API ArrayPointer {
            int size;
            int type;
            int stride;
            const void *pointer;
            bool enabled;
            int position;
            bool vbo;

            ArrayPointer();
            void setPointer(int size_val, int type_val, int stride_val, const void *pointer_val);
            void setPointer(int size_val, int type_val, int stride_val, int position_val);
        };

        struct ENGINE_API Program
        {
            int program;
            int vertShader;
            int fragShader;

            Program();
            ~Program();
            bool create(const char *vertSrc, const char *fragSrc);
            void destroy();

            static int loadShader(const char *src, int type);
            static int createProgram(int vertShader, int fragShader);

        private:
            int createProgram();
        };

        struct FPGLSettings {
            float r, g, b, a;
            float pointSize;

            FPGLSettings();
        };

        class ENGINE_API GLES20FixedPipeline
        {
        public:
            enum ClientState {
                CS_GL_VERTEX_ARRAY,
                CS_GL_TEXTURE_COORD_ARRAY,
                CS_GL_COLOR_ARRAY
            };

            enum MatrixMode {
                MM_GL_PROJECTION,
                MM_GL_TEXTURE,
                MM_GL_MODELVIEW
            };

            static const int GLFP_POLYGON_SMOOTH_HINT = 0;

            /**************************************************************************/

            static GLES20FixedPipeline *getInstance();

            void glMatrixMode(MatrixMode m);
            void glActiveTexture(int tex);
            int getActiveTexture();
            void glOrthoi(int left, int right, int bottom, int top, int zNear, int zFar);
            void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar);
            void glOrthod(double left, double right, double bottom, double top, double zNear, double zFar);
            void glPushMatrix();
            void glPopMatrix();
            void glTranslatef(float tx, float ty, float tz);
            void glRotatef(float angle, float x, float y, float z);
            void glScalef(float sx, float sy, float sz);
            void glMultMatrixf(const float *m);
            void glLoadMatrixf(const float *m);

            void glLoadIdentity();
            void glReadMatrix(float *matrix);
            void readMatrix(MatrixMode m, float *matrix);

            void glColor4f(float r, float g, float b, float a);
            void glColor4x(int r, int g, int b, int a);
            void getColor(float *f);
            void glEnableClientState(ClientState s);
            void glDisableClientState(ClientState s);
            void setEnableTex2D(bool en);
            bool isTex2DEnabled();
            void glVertexPointer(int size, int type, int stride, const void *buffer);
            void glVertexPointer(int size, int type, int stride, int position);
            void glTexCoordPointer(int size, int type, int stride, const void *buffer);
            void glTexCoordPointer(int size, int type, int stride, int position);
            void glColorPointer(int size, int type, int stride, const void *buffer);
            void glColorPointer(int size, int type, int stride, int position);
            float getPointSize();
            void glPointSize(float size);
            void glLineWidth(float w);

            // Equiv to glPushAttrib(GL_ALL_ATTRIB_BITS)
            void pushAllAttribs();
            // Equiv to glPopAttrib(GL_ALL_ATTRIB_BITS)
            void popAllAttribs();

            void glDrawArrays(int mode, int first, int count);
            void glDrawElements(int mode, int count, int type, const void *indices);

        private:
            GLES20FixedPipeline();
            ~GLES20FixedPipeline();

            void drawGenericVector(int mode, int first, int count);
            void drawGenericVector(int mode, int count, int type, const void *indices);

            void drawGenericVectorImpl(int mode, int first, int count, bool useColorPtr);
            void drawGenericVectorImpl(int mode, int count, int type, const void *indices, bool useColorPtr);

            void drawBoundTexture(int mode, int textureUnit, int first, int count);
            void drawBoundTexture(int mode, int textureUnit, int count, int type, const void *indices);

            void drawPoints(int textureUnit, int first, int count);
            void drawPoints(int textureUnit, int count, int type, const void *indices);


            int getGenericVectorProgram(int size, bool colorize);
            int getGenericTextureProgram(int size, int tsize, bool colorize);
            int getGenericPointProgram(int size, bool textured);

            MatrixStack *modelView;
            MatrixStack *projection;
            MatrixStack *texture;

            MatrixStack *current;

            std::stack<FPGLSettings> *attribStack;
            FPGLSettings curAttribs;

            std::map<int, Program *> vectorPrograms;
            std::map<int, Program *> texturePrograms;
            std::map<int, Program *> pointPrograms;

            ArrayPointer texCoordPointer;
            ArrayPointer vertexPointer;
            ArrayPointer colorPointer;

            int activeTextureUnit;

            bool tex2DEnabled;

            friend class GLLinesEmulation;
        };
    }
}

#endif
