#include "GLTFRenderer.h"

#include <memory>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "Matrix.h"

#define BUFFER_OFFSET(i) ((char *)NULL + (i))

#define CheckGLErrors(desc)                                                   \
  {                                                                           \
    GLenum e = glGetError();                                                  \
    if (e != GL_NO_ERROR) {                                                   \
      printf("OpenGL error in \"%s\": %d (%d) %s:%d\n", desc, e, e, __FILE__, \
             __LINE__);                                                       \
      exit(20);                                                               \
    }                                                                         \
  }

using namespace tinygltfloader;

namespace {
    void DrawMesh(const GLRendererState &rstate, const GLMeshState &mesh);
    void DrawNode(const GLRendererState &rstate, const GLNodeBinding &node, const GLuint u_xform, const double *xform);
}

void Renderer_draw(const GLRendererState &state, const GLuint u_xform, const double *xform)
{
    GLboolean cullFaceEnabled = glIsEnabled(GL_CULL_FACE);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glFrontFace(GL_CCW);

    for(auto node : state.nodes)
        DrawNode(state, node, u_xform, xform);

    if(cullFaceEnabled)
        glEnable(GL_CULL_FACE);
    else
        glDisable(GL_CULL_FACE);
}
void Renderer_release(const GLRendererState &state)
{
    // cleanup VBOs
    std::vector<GLuint> vbos;
    vbos.reserve(state.gBufferState.size());
    for(auto it = state.gBufferState.begin(); it != state.gBufferState.end(); it++)
        vbos.push_back(it->second);
    glDeleteBuffers(vbos.size(), &vbos.at(0));

    // cleanup textures
    for(auto it = state.textures.begin(); it != state.textures.end(); it++)
        glDeleteTextures(1u, &it->second);
}

namespace {

    void DrawMesh(const GLRendererState &rstate, const GLMeshState &glmesh) {
        for (size_t i = 0; i < glmesh.primitives.size(); i++) {
            const GLPrimitive &primitive = glmesh.primitives[i];

            if(primitive.doubleSided)
                glDisable(GL_CULL_FACE);
            else
                glEnable(GL_CULL_FACE);

            std::vector<GLuint> activeAttribs;
            activeAttribs.reserve(primitive.accessors.size());

            // Assume TEXTURE_2D target for the texture object.
            glBindTexture(GL_TEXTURE_2D, primitive.texid);

            for (const GLAccessor &accessor : primitive.accessors) {
                glBindBuffer(GL_ARRAY_BUFFER, accessor.vb);

                // it->first would be "POSITION", "NORMAL", "TEXCOORD_0", ...
                glVertexAttribPointer(accessor.attrib, accessor.size,
                                      accessor.type, accessor.normalized,
                                      accessor.stride,
                                      BUFFER_OFFSET(accessor.offset));

                glEnableVertexAttribArray(accessor.attrib);
                activeAttribs.push_back(accessor.attrib);
            }

            if(primitive.indexed) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, primitive.indices.vb);
                glDrawElements(primitive.mode, primitive.indices.count, primitive.indices.type,
                               BUFFER_OFFSET(primitive.indices.offset));

                // unbind the index buffer
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            } else {
                glDrawArrays(primitive.mode, 0, primitive.accessors[0].count);
            }
            // disable the attributes enabled for draw
            for(auto attrib_id : activeAttribs)
                glDisableVertexAttribArray(attrib_id);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
    }

// Hierarchically draw nodes
    void DrawNode(const GLRendererState &rstate, const GLNodeBinding &node, const GLuint u_xform, const double *xform) {
        // Apply xform

        //glPushMatrix();
        const double *node_xform = xform;
        double mx[16];
        Matrix_identity(mx);
        if (node.matrix.size() == 16) {
            // Use `matrix' attribute
            //glMultMatrixd(node.matrix.data());
            if(xform)
                Matrix_concatenate(mx, xform, node.matrix.data());
            else
                Matrix_concatenate(mx, mx, node.matrix.data());
            node_xform = mx;
        }

        // upload the node transform if it has been modified
        if(node_xform != xform) {
            float node_xformf[16];
            for(std::size_t i = 0u; i < 16u; i++)
                node_xformf[i] = (float)node_xform[i];
            glUniformMatrix4fv(u_xform, 1u, false, node_xformf);
        }

        //std::cout << "node " << node.name << ", Meshes " << node.meshes.size() << std::endl;

        for (size_t i = 0; i < node.meshes.size(); i++) {
            DrawMesh(rstate, node.meshes[i]);
        }

        // Draw child nodes.
        for (size_t i = 0; i < node.children.size(); i++) {
            DrawNode(rstate, node.children[i], u_xform, node_xform);
        }

        //glPopMatrix();
        if(node_xform != xform) {
            if(!xform) {
                Matrix_identity(mx);
                xform = mx;
            }
            float xformf[16];
            for(std::size_t i = 0u; i < 16u; i++)
                xformf[i] = (float)xform[i];
            glUniformMatrix4fv(u_xform, 1u, false, xformf);
        }
    }
}
