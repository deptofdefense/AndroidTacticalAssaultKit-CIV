#ifndef TAK_ENGINE_RENDERER_MODEL_GLSCENENODELOADER_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLSCENENODELOADER_H_INCLUDED

#include <deque>
#include "port/Platform.h"
#include "renderer/model/GLSceneNode.h"
#include "util/Error.h"
#include "thread/Monitor.h"
#include "thread/ThreadPool.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API GLSceneNodeLoader
                {
                public :
                    GLSceneNodeLoader(const std::size_t numThreads) NOTHROWS;
                    ~GLSceneNodeLoader() NOTHROWS;
                public :
                    Util::TAKErr enqueue(const std::shared_ptr<GLSceneNode> &node, GLSceneNode::LoadContext &&ctx, const bool prefetch) NOTHROWS;
                    Util::TAKErr cancel(const GLSceneNode &node) NOTHROWS;
                    Util::TAKErr cancelAll() NOTHROWS;
                    Util::TAKErr isQueued(bool *value, const GLSceneNode &node, const bool prefetch) NOTHROWS;

                private:
                    struct QueueNode {
                        QueueNode(GLSceneNode *node, GLSceneNode::LoadContext &&ctx) NOTHROWS;

                        bool operator<(const QueueNode &rhs) const NOTHROWS;

                        GLSceneNode *node;
                        GLSceneNode::LoadContext ctx;
                    };

                    static void *threadStart(void *);
                    void threadImpl();

                    Thread::ThreadPoolPtr threadPool;
                    Thread::Monitor monitor;

                    std::deque<QueueNode> queuedNodes;
                    std::deque<QueueNode> prefetchNodes;
                    std::map<const GLSceneNode *, bool> executingNodes;

                    size_t numThreads;

                    bool prefetchDirty;
                    bool queueDirty;
                    bool shutdown;
                };
            }
        }
    }
}

#endif
