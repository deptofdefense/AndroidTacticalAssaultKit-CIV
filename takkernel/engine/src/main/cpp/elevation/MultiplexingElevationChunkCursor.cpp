#include "elevation/MultiplexingElevationChunkCursor.h"

#include <algorithm>

#include "elevation/ElevationSource.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    struct CursorComparator
    {
        CursorComparator(std::vector<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &)> &impl_) :
            impl(impl_)
        {}

        bool operator()(const std::shared_ptr<ElevationChunkCursor> &a, const std::shared_ptr<ElevationChunkCursor> &b)
        {
            for (std::size_t i = 0u; i < impl.size(); i++) {
                const bool alt = impl[i](*a, *b);
                const bool blt = impl[i](*b, *a);
                if(alt) // strictly less
                    return true;
                else if(blt) // strictly greater
                    return false;
                // else, equal
            }
            return (intptr_t)a.get() < (intptr_t)b.get();
        }

        std::vector<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &) NOTHROWS> &impl;
    };
}

MultiplexingElevationChunkCursor::MultiplexingElevationChunkCursor(Collection<std::shared_ptr<ElevationChunkCursor>> &cursors_) NOTHROWS
{
    do {
        TAKErr code(TE_Ok);

        this->order.push_back(ElevationSource_resolutionDesc);

        if (!cursors_.empty()) {
            Collection<std::shared_ptr<ElevationChunkCursor>>::IteratorPtr iter(nullptr, nullptr);
            code = cursors_.iterator(iter);
            TE_CHECKBREAK_CODE(code);

            do {
                std::shared_ptr<ElevationChunkCursor> element;
                code = iter->get(element);
                TE_CHECKBREAK_CODE(code);
                if (element->moveToNext() == TE_Ok)
                    this->cursors.push_back(element);
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKBREAK_CODE(code);
        }

        if (!this->cursors.empty()) {
            CursorComparator comp(this->order);
            std::sort(this->cursors.begin(), this->cursors.end(), comp);
        }
    } while (false);
}

MultiplexingElevationChunkCursor::MultiplexingElevationChunkCursor(Collection<std::shared_ptr<ElevationChunkCursor>> &cursors_, bool(*order_)(ElevationChunkCursor &, ElevationChunkCursor &)) NOTHROWS
{
    do {
        TAKErr code(TE_Ok);
        if (!order_) {
            order_ = ElevationSource_resolutionDesc;
        }

        this->order.push_back(order_);


        if (!cursors_.empty()) {
            Collection<std::shared_ptr<ElevationChunkCursor>>::IteratorPtr iter(nullptr, nullptr);
            code = cursors_.iterator(iter);
            TE_CHECKBREAK_CODE(code);

            do {
                std::shared_ptr<ElevationChunkCursor> element;
                code = iter->get(element);
                TE_CHECKBREAK_CODE(code);
                if (element->moveToNext() == TE_Ok)
                    this->cursors.push_back(element);
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKBREAK_CODE(code);
        }

        if (!this->cursors.empty()) {
            CursorComparator comp(this->order);
            std::sort(this->cursors.begin(), this->cursors.end(), comp);
        }
    } while (false);
}

MultiplexingElevationChunkCursor::MultiplexingElevationChunkCursor(Collection<std::shared_ptr<ElevationChunkCursor>> &cursors_, Collection<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &)> &order_) NOTHROWS
{
    do {
        TAKErr code(TE_Ok);
        if (order_.empty()) {
            this->order.push_back(ElevationSource_resolutionDesc);
        } else {
            Collection<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &)>::IteratorPtr iter(nullptr, nullptr);
            code = order_.iterator(iter);
            TE_CHECKBREAK_CODE(code);

            do {
                bool (*element)(ElevationChunkCursor &, ElevationChunkCursor &);
                code = iter->get(element);
                TE_CHECKBREAK_CODE(code);
                this->order.push_back(element);
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKBREAK_CODE(code);
        }


        if (!cursors_.empty()) {
            Collection<std::shared_ptr<ElevationChunkCursor>>::IteratorPtr iter(nullptr, nullptr);
            code = cursors_.iterator(iter);
            TE_CHECKBREAK_CODE(code);

            do {
                std::shared_ptr<ElevationChunkCursor> element;
                code = iter->get(element);
                TE_CHECKBREAK_CODE(code);
                if(element->moveToNext() == TE_Ok)
                    this->cursors.push_back(element);
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKBREAK_CODE(code);
        }

        if (!this->cursors.empty()) {
            CursorComparator comp(this->order);
            std::sort(this->cursors.begin(), this->cursors.end(), comp);
        }
    } while (false);
}

TAKErr MultiplexingElevationChunkCursor::get(ElevationChunkPtr &value) NOTHROWS
{
    return this->row->get(value);
}
TAKErr MultiplexingElevationChunkCursor::getResolution(double *value) NOTHROWS
{
    return this->row->getResolution(value);
}
TAKErr MultiplexingElevationChunkCursor::isAuthoritative(bool *value) NOTHROWS
{
    return this->row->isAuthoritative(value);
}
TAKErr MultiplexingElevationChunkCursor::getCE(double *value) NOTHROWS
{
    return this->row->getCE(value);
}
TAKErr MultiplexingElevationChunkCursor::getLE(double *value) NOTHROWS
{
    return this->row->getLE(value);
}
TAKErr MultiplexingElevationChunkCursor::getUri(const char **value) NOTHROWS
{
    return this->row->getUri(value);
}
TAKErr MultiplexingElevationChunkCursor::getType(const char **value) NOTHROWS
{
    return this->row->getType(value);
}
TAKErr MultiplexingElevationChunkCursor::getBounds(const Polygon2 **value) NOTHROWS
{
    return this->row->getBounds(value);
}
TAKErr MultiplexingElevationChunkCursor::getFlags(unsigned int *value) NOTHROWS
{
    return this->row->getFlags(value);
}
TAKErr MultiplexingElevationChunkCursor::moveToNext() NOTHROWS
{
    if(this->row.get()) {
        if(this->row->moveToNext() != TE_Ok)
            this->cursors.erase(this->cursors.begin());
        
        CursorComparator comp(this->order);
        std::sort(this->cursors.begin(), this->cursors.end(), comp);
    }
    if(this->cursors.empty())
        return TE_Done;
    this->row = this->cursors[0];
    return TE_Ok;
}
