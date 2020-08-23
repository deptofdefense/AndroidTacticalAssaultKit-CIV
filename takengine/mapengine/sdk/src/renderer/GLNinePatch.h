#ifndef ATAKMAP_RENDERER_GLNINEPATCH_H_INCLUDED
#define ATAKMAP_RENDERER_GLNINEPATCH_H_INCLUDED

#include "port/Platform.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/GLRenderBatch2.h"

namespace atakmap {
    namespace renderer {
        class ENGINE_API GLNinePatch {
        public:
            enum Size {
                SMALL,
                MEDIUM,
                LARGE
            };

            GLNinePatch(float width, float height);
            GLNinePatch(TAK::Engine::Renderer::GLTextureAtlas2 *texAtlas, Size s, float width, float height,
                        float x0, float y0, float x1, float y1);
            ~GLNinePatch();

            void batch(GLRenderBatch *batch, float x, float y,
                       float width, float height, float r, float g, float b, float a);
            void batch(TAK::Engine::Renderer::GLRenderBatch2& batch, float x, float y,
                float width, float height, float r, float g, float b, float a);
            void batch(TAK::Engine::Renderer::GLRenderBatch2& batch, float x, float y, float z,
                float width, float height, float r, float g, float b, float a);

            void draw(float width, float height);
            void draw(float x, float y, float width, float height, bool textured);
            void draw(float x, float y, float z, float width, float height, bool textured);


        private:
            float *triangle_verts_;
            
            float last_triangle_x_;
            float last_triangle_y_;
            float last_triangle_z_;
            float last_triangle_width_;
            float last_triangle_height_;

            float last_texture_x_;
            float last_texture_y_;
            float last_texture_z_;
            float last_texture_width_;
            float last_texture_height_;

            float radius_;

            TAK::Engine::Renderer::GLTextureAtlas2 *texture_atlas_;
            int64_t tex_atlas_key_;
            int texture_id_;

            float texture_data_width_;
            float texture_data_height_;

            float x0_;
            float y0_;
            float x1_;
            float y1_;

            float *tex_verts_;
            float *tex_coords_;


            void commonInit(TAK::Engine::Renderer::GLTextureAtlas2 *texAtlas, 
                            float width, float height,
                            float x0, float y0, float x1, float y1);

            int64_t loadTextureBitmap(TAK::Engine::Renderer::GLTextureAtlas2 *atlas, Size s);

            void validateTextureEntry();

            void batchTex(TAK::Engine::Renderer::GLRenderBatch2& batch, float x, float y, float z,
                float width, float height, float r,
                float g, float b, float a);
            void batchFan(TAK::Engine::Renderer::GLRenderBatch2& batch, float x, float y, float z,
                float width, float height, float r,
                float g, float b, float a);


            void drawTriangleFan(float x, float y, float z,
                                 float width, float height);
            void drawTexture(float x, float y, float z, float width, float height);
            void initTexBuffers();

            void buildPatchVerts(float *verts, int numVertsPerCorner,
                                 float radius, double radiansPerVert,
                                 float x, float y, float z, float width, float height);

            void fillCoordsBuffer(const std::size_t size,
                                  float texPatchCol0, float texPatchRow0,
                                  float texPatchCol1, float texPatchRow1,
                                  float texPatchCol2, float texPatchRow2,
                                  float texPatchCol3, float texPatchRow3,
                                  float z,
                                  float *buffer);
        };

    }
}

#endif
