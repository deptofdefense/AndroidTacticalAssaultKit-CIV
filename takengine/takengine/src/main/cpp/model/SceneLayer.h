#ifndef TAK_ENGINE_MODEL_SCENELAYER_H_INCLUDED
#define TAK_ENGINE_MODEL_SCENELAYER_H_INCLUDED

#include <map>
#include <set>

#include "core/AbstractLayer2.h"
#include "feature/FeatureDataStore2.h"
#include "feature/FeatureSetDatabase.h"
#include "model/Scene.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            /**
             * Scene access
             *
             * <P>An optional directory path may be specified at construction.
             * If non-NULL, the layer may use this directory for caching and
             * other private implementation purposes. If the same path is used
             * to construct a new SceneLayer object at a later time, it will
             * be initialized to the persistent state of the previously
             * instantiated SceneLayer.
             *
             * <P>
             * Scene IDs map to Feature IDs when performing queries.
             *
             * <P>All functions in this class are thread-safe unless otherwise noted.
             */
            class ENGINE_API SceneLayer : public Core::AbstractLayer2
            {
            public :
                class ENGINE_API ContentChangedListener;
            public :
                /**
                 * 
                 * @param name  The name of the layer
                 * @param path  The path that the layer can use for private/cache data.
                 */
                SceneLayer(const char *name, const char *path) NOTHROWS;
                ~SceneLayer() NOTHROWS;
            private :
                SceneLayer(const SceneLayer &) NOTHROWS;
            public :
                /**
                 * Adds the specified scene to the layer.
                 */
                Util::TAKErr add(int64_t *sid, const std::shared_ptr<Scene> &scene, const SceneInfo &info) NOTHROWS;
                /**
                 * Adds the specified scene to the layer.
                 */
                Util::TAKErr add(int64_t *sid, ScenePtr &&scene, const SceneInfo &info) NOTHROWS;
                /**
                 * Adds the scene from the content at the specified location to the layer.
                 *
                 * @param ssid  Returns the Scene Set ID for the inserted scenes
                 * @param uri   The location of the scene content
                 * @param hint  The type that the scene content should be parsed as (may be NULL)
                 *
                 * @return  TE_Ok on success, TE_InvalidArg if the content could not be parsed, various on other failures
                 */
                Util::TAKErr add(int64_t *ssid, const char *uri, const char *hint) NOTHROWS;

                Util::TAKErr add(int64_t *sid, const SceneInfo &info) NOTHROWS;

                Util::TAKErr remove(const Scene &scene) NOTHROWS;
                Util::TAKErr remove(const char *uri) NOTHROWS;
                Util::TAKErr remove(const SceneInfo &info) NOTHROWS;
                Util::TAKErr removeScene(const int64_t sid) NOTHROWS;
                Util::TAKErr removeSceneSet(const int64_t sid) NOTHROWS;

                Util::TAKErr contains(bool *value, const char *uri) NOTHROWS;

                Util::TAKErr refresh(const char *uri) NOTHROWS;

                Util::TAKErr shutdown() NOTHROWS;

                /**
                 * Updates the specified scene using the specified SceneInfo. This can be used to modify the SRID, name, location, local frame and/or source URI.
                 *
                 * XXX - document how current and new info are merged
                 */
                Util::TAKErr update(const int64_t sid, const SceneInfo &info) NOTHROWS;

                Util::TAKErr setVisible(const int64_t sid, const bool visible) NOTHROWS;
                bool isVisible(const int64_t sid) NOTHROWS;

                Util::TAKErr query(Feature::FeatureCursorPtr &result) NOTHROWS;
                Util::TAKErr query(Feature::FeatureCursorPtr &result, const Feature::FeatureDataStore2::FeatureQueryParameters &params) NOTHROWS;

                Util::TAKErr query(Feature::FeatureSetCursorPtr &result) NOTHROWS;
                Util::TAKErr query(Feature::FeatureSetCursorPtr &result, const Feature::FeatureDataStore2::FeatureSetQueryParameters &params) NOTHROWS;

                Util::TAKErr addContentChangedListener(ContentChangedListener *listener) NOTHROWS;
                Util::TAKErr removeContentChangedListener(ContentChangedListener *listener) NOTHROWS;

                // XXX - progress callback
                /**
                 * Loads and returns the scene with the specified ID.
                 */
                Util::TAKErr loadScene(ScenePtr &value, const int64_t sid) NOTHROWS;
                /**
                 * Loads and returns the scene with the specified ID.
                 */
                Util::TAKErr loadScene(std::shared_ptr<Scene> &value, const int64_t sid) NOTHROWS;

                Util::TAKErr getSceneCacheDir(Port::String &cacheDir, const int64_t sid) const NOTHROWS;
                Util::TAKErr getSceneSetCacheDir(Port::String &cacheDir, const int64_t ssid) const NOTHROWS;
            private :
                static Util::TAKErr addNoSync(int64_t *fid, Feature::FeatureDataStore2 &store, const int64_t fsid, const SceneInfo &info) NOTHROWS;
                void dispatchContentChangedNoSync() NOTHROWS;
                void cleanCache(const int64_t ssid) NOTHROWS;
            private :
                Feature::FeatureSetDatabase persistent;
                Feature::FeatureSetDatabase transient;
                Port::String cacheDir;
                std::set<ContentChangedListener *> listeners;
                std::map<int64_t, std::shared_ptr<Scene>> scenes;
            };

            class ENGINE_API SceneLayer::ContentChangedListener
            {
            public :
                virtual ~ContentChangedListener() NOTHROWS = 0;
            public :
                // XXX -  more granular events?

                virtual Util::TAKErr contentChanged(const SceneLayer &layer) NOTHROWS = 0;
            };

            ENGINE_API Util::TAKErr SceneLayer_getInfo(SceneInfo *value, const atakmap::util::AttributeSet &row) NOTHROWS;
        }
    }
}

#endif
