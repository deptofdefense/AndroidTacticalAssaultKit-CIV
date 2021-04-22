#include "GLTexture.h"
#include "math/Matrix.h"
#include "GLES20FixedPipeline.h"


namespace atakmap {
    namespace renderer {

        namespace {
            int nextPowerOf2(int value)
            {
                --value;
                value = (value >> 1) | value;
                value = (value >> 2) | value;
                value = (value >> 4) | value;
                value = (value >> 8) | value;
                value = (value >> 16) | value;
                ++value;
                return value;
            }

        }


        /*************************************************************************/
        // Private functions

        void GLTexture::apply()
        {
            int t;
            glGetIntegerv(GL_TEXTURE_BINDING_2D, &t);
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT);
            glBindTexture(GL_TEXTURE_2D, t);
        }


        /*************************************************************************/
        // Public API - construction, setup, teardown

        GLTexture::GLTexture(int w, int h,
                             int format, int type) : id(0),
            format(format), type(type), width(0), height(0), minFilter(GL_NEAREST),
            magFilter(GL_LINEAR), wrapS(GL_CLAMP_TO_EDGE), wrapT(GL_CLAMP_TO_EDGE),
            needsApply(false)
        {
            width = nextPowerOf2(w);
            height = nextPowerOf2(h);
        }

        void GLTexture::init()
        {
            if (id == 0) {
                glGenTextures(1, &id);
                glBindTexture(GL_TEXTURE_2D, id);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT);
                glTexImage2D(GL_TEXTURE_2D, 0, format,
                                                 width, height, 0,
                                                 format, type, nullptr);
                glBindTexture(GL_TEXTURE_2D, 0);

            }
        }

        void GLTexture::release()
        {
            glDeleteTextures(1, &id);
            id = 0;
        }


        /*************************************************************************/
        // Public API - Member get/sets

        int GLTexture::getType()
        {
            return type;
        }
        int GLTexture::getFormat()
        {
            return format;
        }

        int GLTexture::getTexId()
        {
            if (needsApply) {
                needsApply = false;
                apply();
            }
            return id;
        }

        int GLTexture::getTexWidth()
        {
            return width;
        }

        int GLTexture::getTexHeight()
        {
            return height;
        }

        int GLTexture::getWrapT()
        {
            return wrapT;
        }

        int GLTexture::getWrapS()
        {
            return wrapS;
        }

        int GLTexture::getMagFilter()
        {
            return magFilter;
        }

        int GLTexture::getMinFilter()
        {
            return minFilter;
        }

        void GLTexture::setWrapS(int wrap_s)
        {
            this->wrapS = wrap_s;
            needsApply = true;
        }

        void GLTexture::setWrapT(int wrap_t)
        {
            this->wrapT = wrap_t;
            needsApply = true;
        }

        void GLTexture::setMinFilter(int min_filter)
        {
            this->minFilter = min_filter;
            needsApply = true;
        }

        void GLTexture::setMagFilter(int mag_filter)
        {
            this->magFilter = minFilter;
            needsApply = true;
        }


        /*************************************************************************/
        // Public API - texture loading

        void GLTexture::load(Bitmap *bitmap, int x, int y)
        {
            init();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, bitmap->width, bitmap->height, format, type, bitmap->data);
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        void GLTexture::load(Bitmap *bitmap)
        {
            load(bitmap, 0, 0);
        }

        void GLTexture::load(void *data, int x, int y, int w, int h)
        {
            init();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h,
                                                format, type, data);
            glBindTexture(GL_TEXTURE_2D, 0);
        }



        /*************************************************************************/
        // Public API - Drawing

        void GLTexture::draw(int numCoords, int tex_type, void *textureCoordinates,
                             void *vertexCoordinates)
        {
            draw(numCoords, tex_type, textureCoordinates, tex_type, vertexCoordinates);
        }
        void GLTexture::draw(int numCoords, int texType, void *textureCoordinates,
                             int vertType, void *vertexCoordinates)
        {
            draw(id, GL_TRIANGLE_FAN, numCoords, texType, textureCoordinates,
                 vertType, vertexCoordinates);
        }




        /*************************************************************************/
        // Public API - Static Drawing

        void GLTexture::draw(int texId, int mode, int numCoords, int texType,
                             void *textureCoordinates, int vertType, void *vertexCoordinates)
        {
            draw(texId, mode, numCoords,
                 2, texType, textureCoordinates,
                 2, vertType, vertexCoordinates);
        }

        void GLTexture::draw(int texId, int mode, int numCoords, int texSize, int texType,
                             void *textureCoordinates, int vertSize, int vertType, void *vertexCoordinates)
        {
            GLTexture::draw(texId, mode, numCoords, texSize, texType, textureCoordinates, vertSize, vertType, vertexCoordinates, 1.0f);
        }

        void GLTexture::draw(int texId, int mode, int numCoords, int texSize, int texType, void *textureCoordinates, int vertSize,
                             int vertType, void *vertexCoordinates, float alpha) {
            if (texId == 0)
                return;

            GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

            fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            fixedPipe->glVertexPointer(vertSize, vertType, 0, vertexCoordinates);

            fixedPipe->glTexCoordPointer(texSize, texType, 0, textureCoordinates);
            glBindTexture(GL_TEXTURE_2D, texId);

            fixedPipe->glColor4f(1.0f, 1.0f, 1.0f, alpha);
            fixedPipe->glDrawArrays(mode, 0, numCoords);

            fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
            glDisable(GL_BLEND);
        }

        void GLTexture::draw(int texId, int mode, int numCoords, int texType,
                             void *textureCoordinates, int vertType, void *vertexCoordinates, int idxType,
                             void *indices)
        {
            draw(texId, mode, numCoords, 2, texType, textureCoordinates, 2, vertType, vertexCoordinates, idxType, indices);
        }

        void GLTexture::draw(int texId, int mode, int numCoords, int texSize, int texType, void *textureCoordinates, int vertSize,
                             int vertType, void *vertexCoordinates, int idxType, void *indices) {
            draw(texId, mode, numCoords, texSize, texType, textureCoordinates, vertSize, vertType, vertexCoordinates, idxType, indices, 1.0f);
        }

        void GLTexture::draw(int texId, int mode, int numCoords, int texSize, int texType,
                             void *textureCoordinates, int vertSize, int vertType, void *vertexCoordinates, int idxType,
                             void *indices, float alpha)
        {
            if (texId == 0)
                return;

            GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

            fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            fixedPipe->glVertexPointer(vertSize, vertType, 0, vertexCoordinates);

            fixedPipe->glTexCoordPointer(texSize, texType, 0, textureCoordinates);
            glBindTexture(GL_TEXTURE_2D, texId);

            fixedPipe->glColor4f(1.0f, 1.0f, 1.0f, alpha);
            fixedPipe->glDrawElements(mode, numCoords, idxType, indices);
            // GLES20FixedPipeline.glDrawArrays(mode, 0, numCoords);

            fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
            glDisable(GL_BLEND);
        }


        /*************************************************************************/
        // Public API - Static utility

        int GLTexture::getNumQuadMeshVertices(int numCellsX, int numCellsY)
        {
            return (numCellsX + 1) * (numCellsY + 1);
        }

        void GLTexture::createQuadMeshTexCoords(math::Point<float> upperLeft, math::Point<float> upperRight,
                                                math::Point<float> lowerRight, math::Point<float> lowerLeft,
                                                int numCellsX, int numCellsY, float *buffer)
        {
            math::Matrix gridToTexCoord = math::Matrix();
            math::Matrix::mapQuads(0, 0, // grid UL
                                   numCellsX, 0, // grid UR
                                   numCellsX, numCellsY, // gridLR
                                   0, numCellsY, // gridLL
                                   upperLeft.x, upperLeft.y,
                                   upperRight.x, upperRight.y,
                                   lowerRight.x, lowerRight.y,
                                   lowerLeft.x, lowerLeft.y, &gridToTexCoord);

            math::Point<double> gridCoord = math::Point<double>(0, 0);
            math::Point<double> texCoord = math::Point<double>(0, 0);

            for (int y = 0; y <= numCellsY; y++) {
                gridCoord.y = y;
                for (int x = 0; x <= numCellsX; x++) {
                    gridCoord.x = x;

                    gridToTexCoord.transform(&gridCoord, &texCoord);

                    *buffer++ = (float)texCoord.x;
                    *buffer++ = (float)texCoord.y;
                }
            }
        }

        void GLTexture::createQuadMeshTexCoords(float width, float height, int numCellsX,
                                                int numCellsY, float *buffer)
        {
            for (int y = 0; y <= numCellsY; y++) {
                for (int x = 0; x <= numCellsX; x++) {
                    *buffer++ = (width * ((float)x / (float)(numCellsX + 1)));
                    *buffer++ = (height * ((float)y / (float)(numCellsY + 1)));
                }
            }
        }

        int GLTexture::getNumQuadMeshIndices(int numCellsX, int numCellsY)
        {
            return ((2 * (numCellsX + 1)) * numCellsY) + (2 * (numCellsY - 1));
        }

        void GLTexture::createQuadMeshIndexBuffer(int numCellsX, int numCellsY, short *buffer)
        {
            short index = 0;
            const int numVertsX = numCellsX + 1;
            for (int y = 0; y < numCellsY; y++) {
                for (int x = 0; x < numVertsX; x++) {
                    *buffer++ = index;
                    *buffer++ = ((short)(index + numVertsX));
                    index++;
                }
                // the degenerate triangle
                if (y < (numCellsY - 1)) {
                    *buffer++ = ((short)((index + numVertsX) - 1));
                    *buffer++ = (index);
                }
            }
        }

    }
}