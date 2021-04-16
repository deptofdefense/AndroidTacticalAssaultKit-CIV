
#include <regex>
#include <sstream>
#include <iomanip>
#include <atomic>
#include "model/ContextCaptureSceneSpi.h"
#include "model/SceneBuilder.h"
#include "util/ZipFile.h"
#include "model/SceneInfo.h"
#include "model/MeshTransformer.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Math;

#define CC_MAX_LOD 22u

namespace {
	class CCRootSceneNode;

	class CCTileSceneNode : public SceneNode {
	public:
		CCTileSceneNode(CCRootSceneNode *p)
			: parent(p) { }

		~CCTileSceneNode() override;
	public:
		bool isRoot() const NOTHROWS override;
		TAKErr getParent(const SceneNode **value) const NOTHROWS override;
		const Matrix2 *getLocalFrame() const NOTHROWS override;
		TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS override;
		bool hasChildren() const NOTHROWS override;
		bool hasMesh() const NOTHROWS override;
		const TAK::Engine::Feature::Envelope2 &getAABB() const NOTHROWS override;
		std::size_t getNumLODs() const NOTHROWS override;
		TAKErr loadMeshScene(ScenePtr &scene, const std::size_t lodIdx, ProcessingCallback *callback) NOTHROWS;
		TAKErr loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lodIdx = 0u, ProcessingCallback *callback = nullptr) NOTHROWS override;
		TAKErr getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS override;
		TAKErr getLODIndex(std::size_t *value, const double clod, const int round = 0) const NOTHROWS override;
        	TAKErr getInstanceID(std::size_t *value, const std::size_t lod_val) const NOTHROWS override;
		bool hasSubscene() const NOTHROWS override;
		TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
		bool hasLODNode() const NOTHROWS override;
		TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;

		TAK::Engine::Math::Matrix2 localFrame;
		TAK::Engine::Feature::Envelope2 aabb;

		CCRootSceneNode *parent;
                int x {0};
                int y {0};
		std::string lodBaseDir;
		std::string tileNameSpec;
		std::vector<int> lods;
		/*int minLod = 0;
		int maxLod = 0;*/
		bool lod {false};
	};

	class CCRootSceneNode : public SceneNode {
	public:
		~CCRootSceneNode() override;
	public:
		bool isRoot() const NOTHROWS override;
		TAKErr getParent(const SceneNode **value) const NOTHROWS override;
		const Matrix2 *getLocalFrame() const NOTHROWS override;
		TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS override;
		bool hasChildren() const NOTHROWS override;
		bool hasMesh() const NOTHROWS override;
		const TAK::Engine::Feature::Envelope2 &getAABB() const NOTHROWS override;
		std::size_t getNumLODs() const NOTHROWS override;
		TAKErr loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lodIdx = 0u, ProcessingCallback *callback = nullptr) NOTHROWS override;
		TAKErr getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS override;
		TAKErr getLODIndex(std::size_t *value, const double clod, const int round = 0) const NOTHROWS override;
        TAKErr getInstanceID(std::size_t *value, const std::size_t lod_val) const NOTHROWS override;
		bool hasSubscene() const NOTHROWS override;
		TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
		bool hasLODNode() const NOTHROWS override;
		TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;

		std::string archivePath;

		TAK::Engine::Math::Matrix2 localFrame;
		TAK::Engine::Feature::Envelope2 aabb;

		int minTileX = INT_MAX;
		int maxTileX = INT_MIN;
		int minTileY = INT_MAX;
		int maxTileY = INT_MIN;
		int minTileZ = INT_MAX;
		int maxTileZ = INT_MIN;
		bool lod = false;
		bool known = false;
		
		STLVectorAdapter<std::shared_ptr<SceneNode>> tiles;
	};

	class CCScene : public Scene {
	public:
		~CCScene() NOTHROWS override;
		SceneNode &getRootNode() const NOTHROWS override;
		const TAK::Engine::Feature::Envelope2 &getAABB() const NOTHROWS override;
		unsigned int getProperties() const NOTHROWS override;
		CCRootSceneNode rootNode;
	};

	static bool hasExt(const char *path, const char *ext);
}

ContextCaptureSceneSpi::~ContextCaptureSceneSpi() NOTHROWS
{}

const char *ContextCaptureSceneSpi::getType() const NOTHROWS {
	return "ContextCapture";
}

int ContextCaptureSceneSpi::getPriority() const NOTHROWS {
	return 1;
}

TAKErr ContextCaptureSceneSpi::create(ScenePtr &scene, const char *URI, ProcessingCallback *callbacks, const Collection<ResourceAlias> *resourceAliases) const NOTHROWS {

	TAKErr code(TE_Ok);

	std::regex lodTilePattern("(.*[\\\\/])?([\\w\\W]+)[\\\\/]Tile_[\\+\\-]\\d+_[\\+\\-]\\d+[\\\\\\/]Tile_([\\+\\-]\\d+)_([\\+\\-]\\d+)_L(\\d+)(_[0123]+)?\\.obj");
	std::regex tilePattern("(.*[\\\\/])?([\\w\\W]+)[\\\\/]Tile_[\\+\\-]\\d+_[\\+\\-]\\d+[\\\\\\/]Tile_([\\+\\-]\\d+)_([\\+\\-]\\d+).obj");

    if(!strstr(URI, ".zip"))
        return TE_Unsupported;

	// Find the archive path
	String archivePath = URI;
	while (!hasExt(archivePath.get(), ".zip")) {
		code = IO_getParentFile(archivePath, archivePath.get());
		if (code != TE_Ok)
			return TE_Unsupported;
	}

	ZipFilePtr zipFilePtr(nullptr, nullptr);
	code = ZipFile::open(zipFilePtr, archivePath.get());
	TE_CHECKRETURN_CODE(code);

	size_t numEntries = 0;
	code = zipFilePtr->getNumEntries(numEntries);
	int progressMax = static_cast<int>(numEntries + 1);
	TE_CHECKRETURN_CODE(code);

	if (callbacks)
		callbacks->progress(callbacks->opaque, 0, progressMax);

	code = zipFilePtr->gotoFirstEntry();
	TE_CHECKRETURN_CODE(code);

	std::unique_ptr<CCScene> resultScenePtr(nullptr);
	std::map<std::string, std::map<int64_t, std::shared_ptr<CCTileSceneNode>>> tiles;
	int progress = 0;

	while (code == TE_Ok) {

		String entryPath;
		code = zipFilePtr->getCurrentEntryPath(entryPath);
		TE_CHECKBREAK_CODE(code);

		std::string entryPathStr = entryPath.get();
		
		// not folder and .obj
		if (entryPathStr.size() > 0 && 
			entryPathStr.back() != '/' &&
			hasExt(entryPathStr.c_str(), ".obj")) {

			std::smatch match;
			int x = 0;
			int y = 0;
			int z = 0;
			
			if ((!resultScenePtr || resultScenePtr->rootNode.lod) &&
				std::regex_search(entryPathStr, match, lodTilePattern)) {
			
				x = strtol(match[3].str().c_str(), nullptr, 10);
				y = strtol(match[4].str().c_str(), nullptr, 10);
				z = strtol(match[5].str().c_str(), nullptr, 10);

                if (z > CC_MAX_LOD) {
                    if (callbacks)
			            callbacks->progress(callbacks->opaque, ++progress, progressMax);

		            code = zipFilePtr->gotoNextEntry();
                    continue;
                }
				
				if (!resultScenePtr)
					resultScenePtr.reset(new CCScene());
				resultScenePtr->rootNode.lod = true;
			} else if ((!resultScenePtr || !resultScenePtr->rootNode.lod) &&
				std::regex_search(entryPathStr, match, tilePattern)) {

				x = strtol(match[3].str().c_str(), nullptr, 10);
				y = strtol(match[4].str().c_str(), nullptr, 10);
				
				if (!resultScenePtr)
					resultScenePtr.reset(new CCScene());
				resultScenePtr->rootNode.lod = false;
			}

			if (!resultScenePtr)
				return TE_Unsupported;

			int64_t key = ((int64_t)y << 32LL) | ((int64_t)x & 0xFFFFFFFFLL);

			resultScenePtr->rootNode.minTileX = std::min(x, resultScenePtr->rootNode.minTileX);
			resultScenePtr->rootNode.maxTileX = std::max(x, resultScenePtr->rootNode.maxTileX);
			resultScenePtr->rootNode.minTileY = std::min(y, resultScenePtr->rootNode.minTileY);
			resultScenePtr->rootNode.maxTileY = std::max(y, resultScenePtr->rootNode.maxTileY);
			resultScenePtr->rootNode.minTileZ = std::min(z, resultScenePtr->rootNode.minTileZ);
			resultScenePtr->rootNode.maxTileZ = std::max(z, resultScenePtr->rootNode.maxTileZ);

			std::string tileSeg = match[2].str();
			auto it = tiles.find(tileSeg);
			if (it == tiles.end()) {
				it = tiles.insert(std::make_pair(tileSeg, std::map<int64_t, std::shared_ptr<CCTileSceneNode>>())).first;
			}

			String lodBaseDir;
			code = IO_getParentFile(lodBaseDir, entryPathStr.c_str());
			TE_CHECKBREAK_CODE(code);

			String tileNameSpec;
			code = IO_getName(tileNameSpec, lodBaseDir.get());
			TE_CHECKBREAK_CODE(code);

			std::shared_ptr<CCTileSceneNode> tileNode;
			auto tileIt = it->second.find(key);
			if (tileIt == it->second.end()) {
				tileNode = std::make_shared<CCTileSceneNode>(&resultScenePtr->rootNode);
				tileNode->x = x;
				tileNode->y = y;
				tileNode->lodBaseDir = lodBaseDir.get();
				tileNode->tileNameSpec = tileNameSpec.get();
				tileNode->lod = resultScenePtr->rootNode.lod;
				tileIt = it->second.insert(std::make_pair(key, tileNode)).first;
			}
			tileNode = tileIt->second;
            if (tileNode->lod && std::find(tileNode->lods.begin(), tileNode->lods.end(), (CC_MAX_LOD - z)) == tileNode->lods.end())
            {
                tileNode->lods.push_back(CC_MAX_LOD - z);

                std::sort(tileNode->lods.begin(), tileNode->lods.end());
            }
		}
			
		if (callbacks)
			callbacks->progress(callbacks->opaque, ++progress, progressMax);

		code = zipFilePtr->gotoNextEntry();
	}

	if (code != TE_Done)
		return code;

    
	code = TE_Err;
	resultScenePtr->rootNode.archivePath = archivePath.get();

	do {
		// XXX - locate tile in center
		int centerTileX = resultScenePtr->rootNode.minTileX + ((resultScenePtr->rootNode.maxTileX - resultScenePtr->rootNode.minTileX + 1) / 2);
		int centerTileY = resultScenePtr->rootNode.minTileY + ((resultScenePtr->rootNode.maxTileY - resultScenePtr->rootNode.minTileY + 1) / 2);

		TAK::Engine::Feature::Envelope2 centerMbb;
		bool haveCenterMbb = false;

		// compute MBB as nominal tile MBB
		for (auto it = tiles.begin(); it != tiles.end(); ++it) {

			auto tileIt = it->second.find(((int64_t)centerTileY << 32L) | ((int64_t)centerTileX & 0xFFFFFFFFL));
			if (tileIt == it->second.end()) {
				continue;
			}

			std::shared_ptr<CCTileSceneNode> tile = tileIt->second;

			for (size_t i = tile->lods.size(); i > 0; i--) {
				ScenePtr tilePtr(nullptr, nullptr);
				if (tile->loadMeshScene(tilePtr, i-1u, nullptr) == TE_Ok) {

					centerMbb = tilePtr->getAABB();
					haveCenterMbb = true;
					break;

					/*TODO--if (!grid.lod) {
						// decode texture bounds
						BitmapFactory.Options img = new BitmapFactory.Options();
						findLargestTexture(m, img);

						// compute GSD
						final double pixels = Math.sqrt(img.outWidth * img.outHeight);
						final double meters = Math.sqrt((centerMbb.maxX - centerMbb.minX) * (centerMbb.maxY - centerMbb.minY));

						if (pixels == 0d) {
							// XXX - choose some high default ?
							grid.minTileZoom = 23;
							grid.maxTileZoom = 23;
						}
						else {
							final double gsd = meters / pixels;

							grid.minTileZoom = (int)Math.ceil(GLContextCaptureTile.gsd2lod(gsd));
							grid.maxTileZoom = (int)Math.ceil(GLContextCaptureTile.gsd2lod(gsd));
						}
					}*/

				}
			}

			if (haveCenterMbb)
				break;
		}

		// estimate scene MBB based on grid extents and nominal bounds
		if (haveCenterMbb) {
			double tileWidth = (centerMbb.maxX - centerMbb.minX);
			double tileHeight = (centerMbb.maxY - centerMbb.minY);

			resultScenePtr->rootNode.aabb.minX = centerMbb.minX - (tileWidth * (centerTileX - resultScenePtr->rootNode.minTileX));
			resultScenePtr->rootNode.aabb.minY = centerMbb.minY - (tileHeight * (centerTileY - resultScenePtr->rootNode.minTileY));
			resultScenePtr->rootNode.aabb.minZ = centerMbb.minZ - 1000;
			resultScenePtr->rootNode.aabb.maxX = centerMbb.maxX + (tileWidth * (resultScenePtr->rootNode.maxTileX - centerTileX));
			resultScenePtr->rootNode.aabb.maxY = centerMbb.maxY + (tileHeight * (resultScenePtr->rootNode.maxTileY - centerTileY));
			resultScenePtr->rootNode.aabb.maxZ = centerMbb.maxZ + 1000;

			for (auto it = tiles.begin(); it != tiles.end(); ++it) {

				for (auto tileIt = it->second.begin(); tileIt != it->second.end(); ++tileIt) {
					std::shared_ptr<CCTileSceneNode> est = tileIt->second;
					//est->minLod = resultScenePtr->rootNode.minTileZ;
					//est->maxLod = resultScenePtr->rootNode.maxTileZ;
					
					est->aabb.minX = centerMbb.minX + (tileWidth * (est->x - centerTileX));
					est->aabb.minY = centerMbb.minY + (tileHeight * (est->y - centerTileY));
					est->aabb.minZ = centerMbb.minZ - 1000;
					est->aabb.maxX = centerMbb.maxX + (tileWidth * (est->x - centerTileX));
					est->aabb.maxY = centerMbb.maxY + (tileHeight * (est->y - centerTileY));
					est->aabb.maxZ = centerMbb.maxZ + 1000;

					resultScenePtr->rootNode.tiles.add(est);
				}
			}

			code = TE_Ok;
		}

	} while (false);

	if (code == TE_Ok)
		scene = ScenePtr(resultScenePtr.release(), Memory_deleter_const<Scene, CCScene>);

	if (callbacks)
		callbacks->progress(callbacks->opaque, progressMax, progressMax);

	return code;
}

namespace {
	static bool hasExt(const char *path, const char *ext) {
		const char *lastExt = strrchr(path, '.');
		if (lastExt) {
			int cmp = -1;
			TAK::Engine::Port::String_compareIgnoreCase(&cmp, ext, lastExt);
			if (cmp == 0)
				return true;
		}
		return false;
	}

	//
	// CCRootSceneNode
	//

	CCRootSceneNode::~CCRootSceneNode()
	{}

	bool CCRootSceneNode::isRoot() const NOTHROWS {
		return true;
	}
	
	TAKErr CCRootSceneNode::getParent(const SceneNode **value) const NOTHROWS {
		if (!value)
			return TE_InvalidArg;
		
		*value = nullptr;
		return TE_Ok;
	}
	
	const Matrix2 *CCRootSceneNode::getLocalFrame() const NOTHROWS {
		return &localFrame;
	}
	
	TAKErr CCRootSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS {
		return const_cast<CCRootSceneNode *>(this)->tiles.iterator(value);
	}
	
	bool CCRootSceneNode::hasChildren() const NOTHROWS {
		return const_cast<CCRootSceneNode *>(this)->tiles.size() > 0;
	}
	
	bool CCRootSceneNode::hasMesh() const NOTHROWS {
		return false;
	}
	
	const TAK::Engine::Feature::Envelope2 &CCRootSceneNode::getAABB() const NOTHROWS {
		return aabb;
	}
	
	std::size_t CCRootSceneNode::getNumLODs() const NOTHROWS {
		return 0;
	}
	
	TAKErr CCRootSceneNode::loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lodIdx, ProcessingCallback *callback) NOTHROWS {
		return TE_IllegalState;
	}
	
	TAKErr CCRootSceneNode::getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS {
		return TE_IllegalState;
	}
	
	TAKErr CCRootSceneNode::getLODIndex(std::size_t *value, const double clod, const int round) const NOTHROWS {
		return TE_IllegalState;
	}

    TAKErr CCRootSceneNode::getInstanceID(std::size_t *value, const std::size_t lod_val) const NOTHROWS
    {
        if (lod_val >= getNumLODs())
            return TE_InvalidArg;
        *value = SceneNode::InstanceID_None;
        return TE_Ok;
    }

	bool CCRootSceneNode::hasSubscene() const NOTHROWS
	{
		return false;
	}
	TAKErr CCRootSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS
	{
		return TE_IllegalState;
	}

	bool CCRootSceneNode::hasLODNode() const NOTHROWS {
		return false;
	}

	TAKErr CCRootSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
		return TE_Unsupported;
	}

	//
	// CCTileSceneNode
	//

	CCTileSceneNode::~CCTileSceneNode()
	{}

	bool CCTileSceneNode::isRoot() const NOTHROWS {
		return false;
	}

	TAKErr CCTileSceneNode::getParent(const SceneNode **value) const NOTHROWS {
		if (!value)
			return TE_InvalidArg;

		*value = this->parent;
		return TE_Ok;
	}

	const Matrix2 *CCTileSceneNode::getLocalFrame() const NOTHROWS {
		return &localFrame;
	}

	TAKErr CCTileSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS {
		return TE_IllegalState;
	}

	bool CCTileSceneNode::hasChildren() const NOTHROWS {
		return false;
	}

	bool CCTileSceneNode::hasMesh() const NOTHROWS {
		return true;
	}

	const TAK::Engine::Feature::Envelope2 &CCTileSceneNode::getAABB() const NOTHROWS {
		return aabb;
	}

	std::size_t CCTileSceneNode::getNumLODs() const NOTHROWS {
		return lods.size();
	}

	TAKErr CCTileSceneNode::loadMeshScene(ScenePtr &scene, const std::size_t lodIdx, ProcessingCallback *callback) NOTHROWS {
		std::ostringstream entryPath;
		entryPath << lodBaseDir << "/" << tileNameSpec;
		if (lod) {
			entryPath << "_L" << std::setfill('0') << std::setw(2) << (CC_MAX_LOD-this->lods[lodIdx]);
		}
		entryPath << ".obj";

		std::ostringstream tileURI;
		tileURI << static_cast<CCRootSceneNode *>(this->parent)->archivePath << "/" << entryPath.str();

		return SceneFactory_create(scene, tileURI.str().c_str(), "ASSIMP", callback, nullptr);
	}

	TAKErr getFirstMesh(SceneNode &node, std::shared_ptr<const Mesh> &value) {
		if (node.hasMesh()) {
			return node.loadMesh(value);
		}

		Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
		TAKErr code = node.getChildren(iter);
		while (code == TE_Ok) {

			std::shared_ptr<SceneNode> childNode;
			code = iter->get(childNode);
			TE_CHECKBREAK_CODE(code);

			code = getFirstMesh(*childNode, value);
			if (code != TE_Done)
				return code;

			code = iter->next();
		}

		return code;
	}

	TAKErr CCTileSceneNode::loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lodIdx, ProcessingCallback *callback) NOTHROWS {
		ScenePtr tileScene(nullptr, nullptr);
        // load the OBJ scene (should be single mesh)
		TAKErr code = this->loadMeshScene(tileScene, lodIdx, callback);
		if (code == TE_Ok) {
            // extract the first mesh
			code = getFirstMesh(tileScene->getRootNode(), value);
		}

		return code;
	}

	TAKErr CCTileSceneNode::getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS {

		if (!value)
			return TE_InvalidArg;

		if (lodIdx >= this->getNumLODs())
			return TE_BadIndex;

		*value = this->lods[lodIdx];
		return TE_Ok;
	}

	TAKErr CCTileSceneNode::getLODIndex(std::size_t *value, const double clod, const int round) const NOTHROWS {


        if (lods.empty())
            return TE_IllegalState;
        if(clod < lods[0]) {
            *value = 0u;
            return TE_Ok;
        } else if (clod > lods[lods.size() - 1u]) {
            *value = lods.size() - 1u;
            return TE_Ok;
        } else if(round > 0) {
            for (std::size_t i = lods.size(); i > 0; i--) {
                if (clod >= lods[i - 1u]) {
                    *value = i - 1u;
                    return TE_Ok;
                }
            }
            return TE_IllegalState;
        } else if (round < 0) {
            for (std::size_t i = 0; i < lods.size(); i++) {
                if (clod <= lods[i]) {
                    *value = i;
                    return TE_Ok;
                }
            }
            return TE_IllegalState;
        } else { // round == 0
            for (std::size_t i = 0; i < lods.size()-1u; i++) {
                if (clod >= lods[i] && clod <= lods[i+1u]) {
                    const double a = clod - (double)lods[i];
                    const double b = (double)lods[i] - clod;
                    if (a <= b)
                        *value = i;
                    else // b < a
                        *value = i + 1u;
                    return TE_Ok;
                }
            }
            return TE_IllegalState;
        }
	}

    TAKErr CCTileSceneNode::getInstanceID(std::size_t *value, const std::size_t lod_val) const NOTHROWS
    {
        if (lod_val >= getNumLODs())
            return TE_InvalidArg;
        *value = SceneNode::InstanceID_None;
        return TE_Ok;
    }

	bool CCTileSceneNode::hasSubscene() const NOTHROWS
	{
		return false;
	}
	TAKErr CCTileSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS
	{
		return TE_IllegalState;
	}

	bool CCTileSceneNode::hasLODNode() const NOTHROWS {
		return false;
	}

	TAKErr CCTileSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
		return TE_Unsupported;
	}

	//
	// CCScene
	//

	CCScene::~CCScene() NOTHROWS {

	}

	SceneNode &CCScene::getRootNode() const NOTHROWS {
		return const_cast<CCRootSceneNode &>(rootNode);
	}

	const TAK::Engine::Feature::Envelope2 &CCScene::getAABB() const NOTHROWS {
		return rootNode.getAABB();
	}

	unsigned int CCScene::getProperties() const NOTHROWS {
		return DirectSceneGraph;
	}
}
