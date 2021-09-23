#ifndef ATAKMAP_RENDERER_GLTEXTURE_H_INCLUDED
#define ATAKMAP_RENDERER_GLTEXTURE_H_INCLUDED

#include "renderer/GL.h"
#include "renderer/Bitmap.h"
#include "math/Point.h"
#include "port/Platform.h"


namespace atakmap
{
    namespace renderer
    {
        class ENGINE_API GLTexture {
        public:
            GLTexture(int width, int height, int format, int type);

            int getType();
            int getFormat();

            int getTexId();
            int getTexWidth();
            int getTexHeight();
            int getWrapT();
            int getWrapS();
            int getMagFilter();
            int getMinFilter();

            void setWrapS(int wrap_s);
            void setWrapT(int wrap_t);
            void setMinFilter(int min_filter);
            void setMagFilter(int mag_filter);

            void init();

            void load(Bitmap *bitmap, int x, int y);
            void load(Bitmap *bitmap);
            void load(void *data, int x, int y, int w, int h);

            void release();

            void draw(int numCoords, int tex_type, void *textureCoordinates, void *vertexCoordinates);
            void draw(int numCoords, int texType, void *textureCoordinates, int vertType,
                      void *vertexCoordinates);


        private:
            GLuint id;
            int format, type;
            int width, height;
            int minFilter;
            int magFilter;
            int wrapS;
            int wrapT;
            bool needsApply;

            void apply();
            

        public:
            static void draw(int texId, int mode, int numCoords, int texType,
                             void *textureCoordinates, int vertType, void *vertexCoordinates);

            static void draw(int texId, int mode, int numCoords, int texSize, int texType,
                             void *textureCoordinates, int vertSize, int vertType, void *vertexCoordinates);

            static void draw(int texId, int mode, int numCoords, int texSize, int texType,
                             void *textureCoordinates, int vertSize, int vertType, void *vertexCoordinates, float alpha);

            static void draw(int texId, int mode, int numCoords, int texType,
                             void *textureCoordinates, int vertType, void *vertexCoordinates, int idxType,
                             void *indices);

            static void draw(int texId, int mode, int numCoords, int texSize, int texType,
                             void *textureCoordinates, int vertSize, int vertType, void *vertexCoordinates, int idxType,
                             void *indices);

            static void draw(int texId, int mode, int numCoords, int texSize, int texType,
                             void *textureCoordinates, int vertSize, int vertType, void *vertexCoordinates, int idxType,
                             void *indices, float alpha);

            static int getNumQuadMeshVertices(int numCellsX, int numCellsY);
            static void createQuadMeshTexCoords(math::Point<float> upperLeft, math::Point<float> upperRight,
                                                math::Point<float> lowerRight, math::Point<float> lowerLeft,
                                                int numCellsX, int numCellsY, float *buffer);

            static void createQuadMeshTexCoords(float width, float height, int numCellsX,
                                                       int numCellsY, float *buffer);

            static int getNumQuadMeshIndices(int numCellsX, int numCellsY);

            static void createQuadMeshIndexBuffer(int numCellsX, int numCellsY, short *buffer);

        };


    }
}

#endif
