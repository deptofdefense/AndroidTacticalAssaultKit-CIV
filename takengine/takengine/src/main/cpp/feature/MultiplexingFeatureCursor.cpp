#include "feature/MultiplexingFeatureCursor.h"

#include "port/Collections.h"
#include "port/STLVectorAdapter.h"

#include <algorithm>

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::vector<FeatureDataStore2::FeatureQueryParameters::Order>::iterator OrderVectorIter;

    bool FidComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1);
    bool FsidComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1);
    bool NameComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1);
    bool CascadingComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1);

    std::vector<FeatureCursor2 *>::iterator upper_bound(std::vector<FeatureCursor2 *>::iterator begin, std::vector<FeatureCursor2 *>::iterator end, std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 *item, bool(*Comp)(std::pair<OrderVectorIter, OrderVectorIter> &, FeatureCursor2 &, FeatureCursor2 &));
}

MultiplexingFeatureCursor::MultiplexingFeatureCursor() NOTHROWS :
    comp(nullptr),
    current(nullptr)
{}

MultiplexingFeatureCursor::MultiplexingFeatureCursor(Collection<FeatureDataStore2::FeatureQueryParameters::Order> &order_) NOTHROWS :
    comp(nullptr),
    current(nullptr)
{
    if (order_.size() > 1) {
        comp = CascadingComparator;
    } else if (order_.size() == 1) {
        FeatureDataStore2::FeatureQueryParameters::Order first;

        Collection<FeatureDataStore2::FeatureQueryParameters::Order>::IteratorPtr iter(nullptr, nullptr);
        if (TE_Ok == order_.iterator(iter) && TE_Ok == iter->get(first)) {
            switch (first.type) {
                case FeatureDataStore2::FeatureQueryParameters::Order::FeatureId:
                    comp = FidComparator;
                case FeatureDataStore2::FeatureQueryParameters::Order::FeatureName:
                    comp = NameComparator;
                    break;
                case FeatureDataStore2::FeatureQueryParameters::Order::FeatureSet:
                    comp = FsidComparator;
                    break;
                default:
                    // XXX - if not supported, using no comparator
                    break;
            }
        }
    }

    STLVectorAdapter<FeatureDataStore2::FeatureQueryParameters::Order> sink(this->order);
    Collections_addAll(sink, order_);
}

TAKErr MultiplexingFeatureCursor::add(FeatureCursorPtr &&ptr) NOTHROWS
{
    FeatureCursor2 *cursor = ptr.get();
    this->cursors[cursor] = std::move(ptr);
    this->invalid.insert(cursor);

    return TE_Ok;
}

TAKErr MultiplexingFeatureCursor::getId(int64_t *value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getId(value);
}

TAKErr MultiplexingFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getFeatureSetId(value);
}
    
TAKErr MultiplexingFeatureCursor::getVersion(int64_t *value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getVersion(value);
}

TAKErr MultiplexingFeatureCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getRawGeometry(value);
}

FeatureDefinition2::GeometryEncoding MultiplexingFeatureCursor::getGeomCoding() NOTHROWS
{
    if (!current)
        return FeatureDefinition2::GeomGeometry;
    return current->getGeomCoding();
}

TAK::Engine::Feature::AltitudeMode MultiplexingFeatureCursor::getAltitudeMode() NOTHROWS 
{
    if (!current) 
        return TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround;
    return current->getAltitudeMode();
}

double MultiplexingFeatureCursor::getExtrude() NOTHROWS 
{
    if (!current) 
        return 0.0;
    return current->getExtrude();
}

TAKErr MultiplexingFeatureCursor::getName(const char **value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getName(value);
}

FeatureDefinition2::StyleEncoding MultiplexingFeatureCursor::getStyleCoding() NOTHROWS
{
    if (!current)
        return FeatureDefinition2::StyleStyle;
    return current->getStyleCoding();
}

TAKErr MultiplexingFeatureCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getRawStyle(value);
}

TAKErr MultiplexingFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->getAttributes(value);
}

TAKErr MultiplexingFeatureCursor::get(const Feature2 **feature) NOTHROWS
{
    if (!current)
        return TE_IllegalState;
    return current->get(feature);
}

TAKErr MultiplexingFeatureCursor::moveToNext() NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    // update entries for any cursors marked 'invalid'
    std::set<FeatureCursor2 *>::iterator cursor;

    cursor = this->invalid.begin();
    while (cursor != this->invalid.end()) {
        code = (*cursor)->moveToNext();
        if (code == TE_Ok) {
            // XXX - sorted insert
            if (this->comp) {
                std::pair<OrderVectorIter, OrderVectorIter> orderArg(this->order.begin(), this->order.end());
                this->pendingResults.insert(
                    ::upper_bound(
                        this->pendingResults.begin(),
                        this->pendingResults.end(),
                        orderArg,
                        *cursor,
                        this->comp),
                    *cursor);
            } else {
                this->pendingResults.push_back(*cursor);
            }
            cursor++;
        } else if (code == TE_Done) {
            // the cursor has been exhaused; remove it
            this->cursors.erase(*cursor);
            cursor = this->invalid.erase(cursor);
            code = TE_Ok;
        } else {
            break;
        }
    }
    TE_CHECKRETURN_CODE(code);

    // all cursors are now valid
    this->invalid.clear();

    // if there are no pending results, we've exhausted the input
    if (this->pendingResults.empty())
        return TE_Done;

    // remove the cursor pointing at the frame with the highest
    // resolution (numerically lowest GSD value)
    std::vector<FeatureCursor2 *>::iterator next;
    next = this->pendingResults.begin();

    // reset our current pointer
    this->current = *next;
    // now removed, we want to re-evaluate the next call to 'moveToNext'
    // so place it in the invalid list
    this->invalid.insert(*next);
    // effect the removal
    this->pendingResults.erase(next);

    return code;
}

namespace
{
    typedef std::vector<FeatureDataStore2::FeatureQueryParameters::Order>::iterator OrderVectorIter;

    bool FidComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1)
    {
        int64_t fid0;
        arg0.getId(&fid0);
        int64_t fid1;
        arg1.getId(&fid1);

        return (fid0 < fid1);
    }

    bool FsidComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1)
    {
        int64_t fsid0;
        arg0.getFeatureSetId(&fsid0);
        int64_t fsid1;
        arg1.getId(&fsid1);

        return (fsid0 < fsid1);
    }

    bool NameComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1)
    {
        const char *name0;
        arg0.getName(&name0);
        const char *name1;
        arg1.getName(&name1);

        return TAK::Engine::Port::String_strcasecmp(name0, name1) < 0;
    }

    bool CascadingComparator(std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 &arg0, FeatureCursor2 &arg1)
    {
        OrderVectorIter iter;
        for (iter = order.first; iter != order.second; iter++) {
            FeatureDataStore2::FeatureQueryParameters::Order::Type type;
            type = (*iter).type;
            std::pair<OrderVectorIter, OrderVectorIter> orderArg(iter, order.second);
            switch (type)
            {
            case FeatureDataStore2::FeatureQueryParameters::Order::FeatureId:
                if (FidComparator(orderArg, arg0, arg1))
                    return true;
                break;
            case FeatureDataStore2::FeatureQueryParameters::Order::FeatureName:
                if (NameComparator(orderArg, arg0, arg1))
                    return true;
                break;
            case FeatureDataStore2::FeatureQueryParameters::Order::FeatureSet:
                if (FsidComparator(orderArg, arg0, arg1))
                    return true;
                break;
            default:
                // XXX - if not supported, use FID comparator...
                if (FidComparator(orderArg, arg0, arg1))
                    return true;
                break;
            }
        }
        return false;
    }

    std::vector<FeatureCursor2 *>::iterator upper_bound(std::vector<FeatureCursor2 *>::iterator begin, std::vector<FeatureCursor2 *>::iterator end, std::pair<OrderVectorIter, OrderVectorIter> &order, FeatureCursor2 *item, bool(*Comp)(std::pair<OrderVectorIter, OrderVectorIter> &, FeatureCursor2 &, FeatureCursor2 &))
    {
        std::vector<FeatureCursor2 *>::iterator it;
        for (it = begin; it != end; it++) {
            if (!Comp(order, *item, **it))
                return it;
        }
        return end;
    }
}
