#ifndef CESIUM3DTILES_GLTFRENDERER_H_INCLUDED
#define CESIUM3DTILES_GLTFRENDERER_H_INCLUDED

#include <map>
#include <string>
#include <vector>

#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

#include "tinygltf_decls.h"

typedef struct {
    GLuint attrib;
    GLuint vb;
    GLuint size;
    GLenum type;
    GLboolean normalized;
    GLuint stride;
    GLuint offset;
    GLuint count;
} GLAccessor;

typedef struct {
    GLuint texid;
    std::vector<GLAccessor> accessors;
    bool shared_vbo;
    GLenum mode;
    bool doubleSided;
    bool indexed;
    GLAccessor indices;
} GLPrimitive;

typedef struct {
    std::vector<GLPrimitive> primitives;  // for each primitive in mesh
} GLMeshState;

struct GLProgramState {
    std::map<std::string, int> attribAliases;
    int nextAttribAlias;
    std::map<int, GLint> attribs;
};

struct GLNodeBinding {
    std::vector<double> matrix;
    std::vector<GLMeshState> meshes;
    std::vector<struct GLNodeBinding> children;
};

struct GLRendererState {
    std::map<std::string, int> bufferAliases;
    std::map<std::string, int> meshAliases;
    std::map<std::string, int> textureAliases;

    int nextBufferAlias;
    int nextMeshAlias;
    int nextTextureAlias;

    /** */
    std::map<int, GLuint> gBufferState;
    std::map<int, GLMeshState> gMeshState;
    std::map<int, GLuint> textures;
    GLProgramState gGLProgramState;

    std::vector<GLNodeBinding> nodes;
};

bool Renderer_bindModel(GLRendererState *state, tinygltf::Model &model);
bool Renderer_bindModel(GLRendererState *state, tinygltfloader::Scene &scene);
void Renderer_draw(const GLRendererState &state, const GLuint u_xform, const double *xform);
void Renderer_release(const GLRendererState &state);

#endif
