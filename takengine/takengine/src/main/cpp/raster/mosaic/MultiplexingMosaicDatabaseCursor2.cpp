#include "raster/mosaic/MultiplexingMosaicDatabaseCursor2.h"

#include <cmath>
#include <cstring>

using namespace TAK::Engine::Raster::Mosaic;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

MultiplexingMosaicDatabaseCursor2::MultiplexingMosaicDatabaseCursor2() NOTHROWS :
impl(new Impl())
{}

TAKErr MultiplexingMosaicDatabaseCursor2::add(MosaicDatabase2::CursorPtr &&cursor) NOTHROWS
{
    TAKErr code(TE_Ok);
    this->impl->invalid.insert(cursor.get());
    this->impl->cursors.push_back(std::move(cursor));
    return code;
}

TAKErr MultiplexingMosaicDatabaseCursor2::moveToNext() NOTHROWS
{
    return this->impl->moveToNext();
}

TAKErr MultiplexingMosaicDatabaseCursor2::getUpperLeft(GeoPoint2 *value) NOTHROWS
{
    return this->impl->current->getUpperLeft(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getUpperRight(GeoPoint2 *value) NOTHROWS
{
    return this->impl->current->getUpperRight(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getLowerRight(GeoPoint2 *value) NOTHROWS
{
    return this->impl->current->getLowerRight(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getLowerLeft(GeoPoint2 *value) NOTHROWS
{
    return this->impl->current->getLowerLeft(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getMinLat(double *value) NOTHROWS
{
    return this->impl->current->getMinLat(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getMinLon(double *value) NOTHROWS
{
    return this->impl->current->getMinLon(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getMaxLat(double *value) NOTHROWS
{
    return this->impl->current->getMaxLat(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getMaxLon(double *value) NOTHROWS
{
    return this->impl->current->getMaxLon(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getPath(const char **value) NOTHROWS
{
    return this->impl->current->getPath(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getType(const char **value) NOTHROWS
{
    return this->impl->current->getType(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getMinGSD(double *value) NOTHROWS
{
    return this->impl->current->getMinGSD(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getMaxGSD(double *value) NOTHROWS
{
    return this->impl->current->getMaxGSD(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getWidth(int *value) NOTHROWS
{
    return this->impl->current->getWidth(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getHeight(int *value) NOTHROWS
{
    return this->impl->current->getHeight(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getId(int *value) NOTHROWS
{
    // XXX -
    return this->impl->current->getId(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::getSrid(int *value) NOTHROWS
{
    return this->impl->current->getSrid(value);
}

TAKErr MultiplexingMosaicDatabaseCursor2::isPrecisionImagery(bool *value) NOTHROWS
{
    return this->impl->current->isPrecisionImagery(value);
}

/**************************************************************************/

MultiplexingMosaicDatabaseCursor2::Impl::Impl() NOTHROWS :
current(nullptr)
{}

TAKErr MultiplexingMosaicDatabaseCursor2::Impl::moveToNext() NOTHROWS
{
    TAKErr code(TE_Ok);
    code = TE_Ok;
    // update entries for any cursors marked 'invalid'
    std::set<MosaicDatabase2::Cursor *>::iterator cursor;
    for (cursor = this->invalid.begin(); cursor != this->invalid.end(); cursor++) {
        code = (*cursor)->moveToNext();
        if (code == TE_Ok) {
            this->pendingResults.insert(Entry(*cursor));
        }
        else if (code == TE_Done) {
            // the cursor has been exhausted, drop it after we exit the loop
            code = TE_Ok;
        }
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    // all cursors are now valid
    this->invalid.clear();

    // if there are no pending results, we've exhausted the input
    if (this->pendingResults.empty())
        return TE_Done;

    // remove the cursor pointing at the frame with the highest
    // resolution (numerically lowest GSD value)
    Entry entry = *this->pendingResults.begin();
    this->pendingResults.erase(this->pendingResults.begin());
    // now removed, we want to re-evaluate the next call to 'moveToNext'
    // so place it in the invalid list
    this->invalid.insert(entry.cursor);
    // reset our current pointer
    this->current = entry.cursor;

    return code;
}

MultiplexingMosaicDatabaseCursor2::Entry::Entry(MosaicDatabase2::Cursor *cursor_) NOTHROWS :
    gsd(NAN),
    path(nullptr),
    cursor(cursor_)
{
    TAKErr code(TE_Ok);

    this->cursor->getMaxGSD(&this->gsd);

    const char *cpath;
    code = this->cursor->getPath(&cpath);

    this->path = cpath;
}

bool MultiplexingMosaicDatabaseCursor2::Entry::operator<(const Entry &other) const
{
    if (this->gsd < other.gsd)
        return true;
    else if (this->gsd > other.gsd)
        return false;
    else // this.gsd == other.gsd
         // XXX - should consider something faster than string comparison
        return (strcmp(this->path, other.path) < 0);
}
