#include "feature/BruteForceLimitOffsetFeatureSetCursor.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

BruteForceLimitOffsetFeatureSetCursor::BruteForceLimitOffsetFeatureSetCursor(FeatureSetCursor2Ptr &&filter_, std::size_t limit_, std::size_t offset_) NOTHROWS :
    filter(std::move(filter_)),
    limit((limit_ > 0) ? limit_ : std::numeric_limits<std::size_t>::max()),
    offset(offset_),
    pos(0)
{}
TAKErr BruteForceLimitOffsetFeatureSetCursor::get(const FeatureSet2 **value) NOTHROWS
{
    return filter->get(value);
}
TAKErr BruteForceLimitOffsetFeatureSetCursor::moveToNext() NOTHROWS
{
    TAKErr code;

    // if we've exceeded the limit, return false
    size_t limitCompare = this->pos < this->offset ? this->offset : this->pos;
    if ((limitCompare - this->offset) >= this->limit)
        return TE_Done;

    code = TE_Ok;

    // fast forward to the first offset record
    while (this->pos < (this->offset /*- 1*/)) {
        code = filter->moveToNext();
        TE_CHECKBREAK_CODE(code);

        this->pos++;
    }
    TE_CHECKRETURN_CODE(code);

    // input exhausted
    code = filter->moveToNext();
    TE_CHECKRETURN_CODE(code);

    // XXX - looks broken

    // update the position and make sure we haven't exceeded the limit
    this->pos++;
    if ((this->pos - this->offset) <= this->limit)
        return TE_Ok;
    else
        return TE_Done;
}
