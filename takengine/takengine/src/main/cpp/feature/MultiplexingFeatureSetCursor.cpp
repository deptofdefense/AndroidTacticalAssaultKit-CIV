#include "feature/MultiplexingFeatureSetCursor.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

MultiplexingFeatureSetCursor::MultiplexingFeatureSetCursor() NOTHROWS
{}
TAKErr MultiplexingFeatureSetCursor::add(FeatureSetCursor2Ptr &&result) NOTHROWS
{
    results.push_back(std::move(result));
    return TE_Ok;
}
TAKErr MultiplexingFeatureSetCursor::get(const FeatureSet2 **value) NOTHROWS
{
    if (results.empty())
        return TE_IllegalState;
    return results.front()->get(value);
}
TAKErr MultiplexingFeatureSetCursor::moveToNext() NOTHROWS
{
    TAKErr code(TE_Ok);
    do {
        if (results.empty())
            return TE_Done;
        code = results.front()->moveToNext();
        if (code != TE_Done)
            break;
        // head is done, evict to move to next cursor
        results.erase(results.begin());
    } while (true);

    return code;
}
