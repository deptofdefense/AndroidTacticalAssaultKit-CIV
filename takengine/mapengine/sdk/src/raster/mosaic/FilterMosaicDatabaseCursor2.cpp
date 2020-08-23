#include "raster/mosaic/FilterMosaicDatabaseCursor2.h"

#include "util/Memory.h"

using namespace TAK::Engine::Raster::Mosaic;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::unique_ptr<Collection<std::shared_ptr<Filter<MosaicDatabase2::Cursor &>>>, void(*)(const Collection<std::shared_ptr<Filter<MosaicDatabase2::Cursor &>>> *)> FiltersPtr;

    class FilterImpl : public FilterMosaicDatabaseCursor2
    {
    public :
        FilterImpl(MosaicDatabase2::CursorPtr &&impl_, FiltersPtr &&filters_) NOTHROWS;
    public :
        TAKErr moveToNext() NOTHROWS override;
    private :
        FiltersPtr filters;
    };
}

FilterMosaicDatabaseCursor2::FilterMosaicDatabaseCursor2(MosaicDatabase2::CursorPtr &&impl_) NOTHROWS :
    impl(std::move(impl_))
{}

TAKErr FilterMosaicDatabaseCursor2::moveToNext() NOTHROWS
{
    return this->impl->moveToNext();
}
    
TAKErr FilterMosaicDatabaseCursor2::getUpperLeft(GeoPoint2 *value) NOTHROWS
{
    return this->impl->getUpperLeft(value);
}

TAKErr FilterMosaicDatabaseCursor2::getUpperRight(GeoPoint2 *value) NOTHROWS
{
    return this->impl->getUpperRight(value);
}

TAKErr FilterMosaicDatabaseCursor2::getLowerRight(GeoPoint2 *value) NOTHROWS
{
    return this->impl->getLowerRight(value);
}

TAKErr FilterMosaicDatabaseCursor2::getLowerLeft(GeoPoint2 *value) NOTHROWS
{
    return this->impl->getLowerLeft(value);
}

    TAKErr FilterMosaicDatabaseCursor2::getMinLat(double *value) NOTHROWS
{
    return this->impl->getMinLat(value);
}

TAKErr FilterMosaicDatabaseCursor2::getMinLon(double *value) NOTHROWS
{
    return this->impl->getMinLon(value);
}

TAKErr FilterMosaicDatabaseCursor2::getMaxLat(double *value) NOTHROWS
{
    return this->impl->getMaxLat(value);
}

TAKErr FilterMosaicDatabaseCursor2::getMaxLon(double *value) NOTHROWS
{
    return this->impl->getMaxLon(value);
}

TAKErr FilterMosaicDatabaseCursor2::getPath(const char **value) NOTHROWS
{
    return this->impl->getPath(value);
}

TAKErr FilterMosaicDatabaseCursor2::getType(const char **value) NOTHROWS
{
    return this->impl->getType(value);
}

TAKErr FilterMosaicDatabaseCursor2::getMinGSD(double *value) NOTHROWS
{
    return this->impl->getMinGSD(value);
}

TAKErr FilterMosaicDatabaseCursor2::getMaxGSD(double *value) NOTHROWS
{
    return this->impl->getMaxGSD(value);
}

TAKErr FilterMosaicDatabaseCursor2::getWidth(int *value) NOTHROWS
{
    return this->impl->getWidth(value);
}

TAKErr FilterMosaicDatabaseCursor2::getHeight(int *value) NOTHROWS
{
    return this->impl->getHeight(value);
}

TAKErr FilterMosaicDatabaseCursor2::getId(int *value) NOTHROWS
{
    return this->impl->getId(value);
}

TAKErr FilterMosaicDatabaseCursor2::getSrid(int *value) NOTHROWS
{
    return this->impl->getSrid(value);
}
    
TAKErr FilterMosaicDatabaseCursor2::isPrecisionImagery(bool *value) NOTHROWS
{
    return this->impl->isPrecisionImagery(value);
}
    
TAKErr TAK::Engine::Raster::Mosaic::FilterMosaicDatabaseCursor2_filter(MosaicDatabase2::CursorPtr &value, MosaicDatabase2::CursorPtr &&ptr, std::unique_ptr<Port::Collection<std::shared_ptr<Util::Filter<MosaicDatabase2::Cursor &>>>, void(*)(const Port::Collection<std::shared_ptr<Util::Filter<MosaicDatabase2::Cursor &>>> *)> &&filter) NOTHROWS
{
    if (!ptr.get())
        return TE_InvalidArg;
    if (!filter.get())
        return TE_InvalidArg;
    value = MosaicDatabase2::CursorPtr(new FilterImpl(std::move(ptr), std::move(filter)), Memory_deleter_const<MosaicDatabase2::Cursor, FilterImpl>);
    return TE_Ok;
}

namespace
{
    FilterImpl::FilterImpl(MosaicDatabase2::CursorPtr &&impl_, FiltersPtr &&filters_) NOTHROWS :
        FilterMosaicDatabaseCursor2(std::move(impl_)),
        filters(std::move(filters_))
    {}

    TAKErr FilterImpl::moveToNext() NOTHROWS
    {
        TAKErr code(TE_Ok);
        do {
            code = FilterMosaicDatabaseCursor2::moveToNext();
            TE_CHECKBREAK_CODE(code);

            bool accepted = true;
            if (!filters->empty()) {
                Collection<std::shared_ptr<Filter<MosaicDatabase2::Cursor &>>>::IteratorPtr iter(nullptr, nullptr);
                code = filters->iterator(iter);
                TE_CHECKBREAK_CODE(code);

                do {
                    std::shared_ptr<Filter<MosaicDatabase2::Cursor &>> filter;
                    code = iter->get(filter);
                    TE_CHECKBREAK_CODE(code);

                    // see if the current row is accepted
                    accepted &= filter->accept(*this->impl);
                    if (!accepted)
                        break;
                    code = iter->next();
                    TE_CHECKBREAK_CODE(code);
                } while (true);
                // all filters were passed over, move to evaluate if accepted
                if (code == TE_Done)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);
            }

            // move to next row
            if (!accepted)
                continue;

            // result was accepted, break
            break;
        } while (true);

        return code;
    }
}
