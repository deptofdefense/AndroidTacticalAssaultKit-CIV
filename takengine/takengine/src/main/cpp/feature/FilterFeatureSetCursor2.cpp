#include "feature/FilterFeatureSetCursor2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

FilterFeatureSetCursor2::FilterFeatureSetCursor2(FeatureSetCursor2Ptr &&impl_) NOTHROWS :
    impl(std::move(impl))
{}
FilterFeatureSetCursor2::~FilterFeatureSetCursor2() NOTHROWS
{}

TAKErr FilterFeatureSetCursor2::get(const FeatureSet2 **featureSet) NOTHROWS
{
    return impl->get(featureSet);
}
TAKErr FilterFeatureSetCursor2::moveToNext() NOTHROWS
{
    return impl->moveToNext();
}
