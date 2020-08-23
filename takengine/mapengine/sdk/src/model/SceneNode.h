#ifndef TAK_ENGINE_MODEL_SCENENODE_H_INCLUDED
#define TAK_ENGINE_MODEL_SCENENODE_H_INCLUDED

#include <memory>

#include "feature/Envelope2.h"
#include "math/Matrix2.h"
#include "model/Mesh.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "util/ProcessingCallback.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class Scene;
            struct SceneInfo;

            class ENGINE_API SceneNode
            {
            public :
                enum {
                    InstanceID_None = 0u,
                };
            public:
                virtual ~SceneNode() = 0;
            public:
                virtual bool isRoot() const NOTHROWS = 0;
                virtual Util::TAKErr getParent(const SceneNode **value) const NOTHROWS = 0;
                virtual const Math::Matrix2 *getLocalFrame() const NOTHROWS = 0;
                /**
                 * NOTE: Constant iteration order is guaranteed
                 */
                virtual Util::TAKErr getChildren(Port::Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS = 0;
                virtual bool hasChildren() const NOTHROWS = 0;
                virtual bool hasMesh() const NOTHROWS = 0;
                virtual const Feature::Envelope2 &getAABB() const NOTHROWS = 0;
                /**
                 * Returns the number of LODs contained by this scene node.
                 * LOD storage may be sparse; the actual Level of Detail is
                 * determined via `getLOD(...)` with a valid index
                 * (`0` through `getNumLODs()-1`).
                 */
                virtual std::size_t getNumLODs() const NOTHROWS = 0;

                /**
                 * 
                 */
                virtual bool hasLODNode() const NOTHROWS = 0;
                
                /**
                 *
                 */
                virtual Util::TAKErr getLODNode(std::shared_ptr<SceneNode> &value, const std::size_t lodIdx) NOTHROWS = 0;

                /**
                 * @param value         Returns the mesh data
                 * @param lodIdx        The LOD _index_
                 */
                virtual Util::TAKErr loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lodIdx = 0u, Util::ProcessingCallback *callback = nullptr) NOTHROWS = 0;

                /**
                 * Returns the Level of Detail given the LOD index for this
                 * scene node. LODs shall always be descending per LOD index;
                 * LOD 0 represents the most detailed representation of the
                 * data.
                 *
                 * <P>Consecutive LOD levels shall represent an approximate
                 * halving of detail.
                 */
                virtual Util::TAKErr getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS = 0;
                /**
                 * Returns the available LOD index that satisfies the desired Level of Detail.
                 * @param clod  The level of detail, supporting fractional values
                 * @param round If `>0`, rounds up to the next highest LOD; if `<0`, rounds down to the next lowest LOD, if `==0` rounds to the nearest LOD.
                 */
                virtual Util::TAKErr getLODIndex(std::size_t *value, const double clod, const int round = 0) const NOTHROWS = 0;
                /**
                 * Returns the Instance ID associated with the mesh. If the
                 * mesh is not instanced, 'InstanceID_None' will be returned.
                 * If the mesh is instanced, the pre-transformed mesh data will
                 * be identical to the pre-transformed mesh data for all other
                 * nodes that share the same Instance ID.
                 */
                virtual Util::TAKErr getInstanceID(std::size_t *instanceId, const std::size_t lodIdx) const NOTHROWS = 0;

                // Subscenes

                /**
                 * Returns true if this SceneNode has a subscene.
                 */
                virtual bool hasSubscene() const NOTHROWS = 0;

                virtual Util::TAKErr getSubsceneInfo(const SceneInfo **result) NOTHROWS = 0;
            };

            typedef std::unique_ptr<SceneNode, void(*)(const SceneNode *)> SceneNodePtr;
            typedef std::unique_ptr<const SceneNode, void(*)(const SceneNode *)> SceneNodePtr_const;
        }
    }
}

#endif
