#include "renderer/model/GLSceneNodeLoader.h"

#include <algorithm>
#include <atomic>

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    template <typename T, typename E>
    inline typename std::deque<T>::iterator findNode(std::deque<T> &q, const E *node) {
        auto it = q.begin();
        while (it != q.end()) {
            if (it->node == node)
                break;
            ++it;
        }
        return it;
    }
}

GLSceneNodeLoader::QueueNode::QueueNode(GLSceneNode *node_, GLSceneNode::LoadContext &&ctx_) NOTHROWS 
    : node(node_),
    ctx(std::move(ctx_))
{}

bool GLSceneNodeLoader::QueueNode::operator<(const QueueNode &rhs) const NOTHROWS
{
    return ctx.gsd < rhs.ctx.gsd;
}

GLSceneNodeLoader::GLSceneNodeLoader(const std::size_t numThreads) NOTHROWS 
    : threadPool(nullptr, nullptr),
    numThreads(numThreads),
    prefetchDirty(false),
    queueDirty(false),
    shutdown(false)
{}

GLSceneNodeLoader::~GLSceneNodeLoader() NOTHROWS
{
    shutdown = true;

    {
        Monitor::Lock lock(monitor);
        if (lock.status == TE_Ok)
            lock.broadcast();
    }

    if (threadPool.get())
        threadPool.reset();
}
                
TAKErr GLSceneNodeLoader::enqueue(const std::shared_ptr<GLSceneNode> &node, GLSceneNode::LoadContext &&ctx, const bool prefetch) NOTHROWS
{
    TAKErr code(TE_Ok);

    Monitor::Lock lock(monitor);
    code = lock.status; 
    TE_CHECKRETURN_CODE(code);

    if (this->executingNodes.find(node.get()) != this->executingNodes.end())
        return code;

    std::deque<QueueNode> *queueTo = nullptr;

    auto it = findNode(this->queuedNodes, node.get());
    if (it != this->queuedNodes.end() && prefetch) {
        this->queuedNodes.erase(it);
        queueTo = &this->prefetchNodes;
        this->prefetchDirty = true;
    } else if ((it = findNode(this->prefetchNodes, node.get())) != this->prefetchNodes.end() && !prefetch) {
        this->prefetchNodes.erase(it);
        queueTo = &this->queuedNodes;
        this->prefetchDirty = true;
    } else if (prefetch) {
        queueTo = &this->prefetchNodes;
        this->prefetchDirty = true;
    } else { // !prefetch
        queueTo = &this->queuedNodes;
        this->queueDirty = true;
    }

    if (queueTo)
        queueTo->push_back(QueueNode(node.get(), std::move(ctx)));

    if (!this->threadPool) {
        code = Thread::ThreadPool_create(this->threadPool, this->numThreads, threadStart, this);
        TE_CHECKRETURN_CODE(code);
    }

    code = lock.broadcast();
    return code;
}
TAKErr GLSceneNodeLoader::cancel(const GLSceneNode &node) NOTHROWS
{
    TAKErr code(TE_Ok);

    Monitor::Lock lock(monitor);
    code = lock.status; 
    TE_CHECKRETURN_CODE(code);

    auto it = findNode(this->queuedNodes, &node);
    if (it != this->queuedNodes.end()) {
        this->queuedNodes.erase(it);
        return code;
    }

    it = findNode(this->prefetchNodes, &node);
    if (it != this->prefetchNodes.end()) {
        this->prefetchNodes.erase(it);
        return code;
    }

    auto cancelIt = this->executingNodes.find(&node);
    if (cancelIt != this->executingNodes.end()) {
        //XXX-- need full barrier atomic exchange
        cancelIt->second = true;
    }

    return code;
}
TAKErr GLSceneNodeLoader::cancelAll() NOTHROWS
{
    TAKErr code(TE_Ok);

    Monitor::Lock lock(monitor);
    code = lock.status; 
    TE_CHECKRETURN_CODE(code);

    this->queuedNodes.clear();
    this->prefetchNodes.clear();
    for (auto it = this->executingNodes.begin(); it != this->executingNodes.end(); ++it) {
        //XXX-- need full barrier atomic exchange
        it->second = true;
    }

    return code;
}
TAKErr GLSceneNodeLoader::isQueued(bool *value, const GLSceneNode &node, const bool prefetch) NOTHROWS
{
    TAKErr code(TE_Ok);

    Monitor::Lock lock(monitor);
    code = lock.status; 
    TE_CHECKRETURN_CODE(code);

    if (!value)
        return TE_InvalidArg;

    *value = this->executingNodes.find(&node) != this->executingNodes.end() ||
        (prefetch ?
            findNode(this->prefetchNodes, &node) != this->prefetchNodes.end() :
            findNode(this->queuedNodes, &node) != this->queuedNodes.end());

    return code;
}

void *GLSceneNodeLoader::threadStart(void *arg) 
{
    auto *loader = static_cast<GLSceneNodeLoader *>(arg);
    loader->threadImpl();
    return nullptr;
}

void GLSceneNodeLoader::threadImpl() 
{
    std::deque<QueueNode> *queues[] = {
           &this->queuedNodes,
           &this->prefetchNodes
    };
    bool dirty[] = { false, false };

    while (true) {
       
        GLSceneNode *node = nullptr;
        GLSceneNode::LoadContext ctx;
        bool *cancelToken = nullptr;
        
        {
            Monitor::Lock lock(monitor);
            if (lock.status != TE_Ok)
                return;

            if (this->shutdown)
                break;

            if (this->prefetchNodes.empty() && this->queuedNodes.empty()) {
                lock.wait();
                continue;
            }

            dirty[0] = this->queueDirty;
            dirty[1] = this->prefetchDirty;

            for (int i = 0; i < 2; i++) {
                if (queues[i]->empty())
                    continue;
                if (dirty[i])
                    std::sort(queues[i]->begin(), queues[i]->end());

                node = queues[i]->front().node;
                ctx = queues[i]->front().ctx;
                queues[i]->pop_front();
                break;
            }

            if (node == nullptr)
                continue; // illegal state

            cancelToken = &executingNodes.insert(std::make_pair(node, false)).first->second;
        }

        TAKErr code = node->asyncLoad(ctx, cancelToken);
        if (code != TE_Ok) {
            Logger_log(TELL_Error, "Failed to load node %s", node->info.uri.get());
        }

        {
            Monitor::Lock lock(monitor);
            if (lock.status != TE_Ok)
                return;

            executingNodes.erase(node);
        }
    }
}
