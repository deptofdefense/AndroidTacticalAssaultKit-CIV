#include "db/CursorWrapper2.h"

using namespace TAK::Engine::DB;

using namespace TAK::Engine::Util;

CursorWrapper2::CursorWrapper2(QueryPtr &&filter_) :
    filterPtr(std::move(filter_)),
    filter(filterPtr.get())
{}

CursorWrapper2::~CursorWrapper2() NOTHROWS
{}


TAKErr CursorWrapper2::moveToNext() NOTHROWS
{
    return filter->moveToNext();
}

TAKErr CursorWrapper2::getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS
{
    return filter->getColumnIndex(value, columnName);
}

TAKErr CursorWrapper2::getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS
{
    return filter->getColumnName(value, columnIndex);
}

TAKErr CursorWrapper2::getColumnCount(std::size_t *value) NOTHROWS
{
    return filter->getColumnCount(value);
}

TAKErr CursorWrapper2::getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS
{
    return filter->getBlob(value, len, columnIndex);
}

TAKErr CursorWrapper2::getString(const char **value, const std::size_t columnIndex) NOTHROWS
{
    return filter->getString(value, columnIndex);
}

TAKErr CursorWrapper2::getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS
{
    return filter->getInt(value, columnIndex);
}

TAKErr CursorWrapper2::getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS
{
    return filter->getLong(value, columnIndex);
}

TAKErr CursorWrapper2::getDouble(double *value, const std::size_t columnIndex) NOTHROWS
{
    return filter->getDouble(value, columnIndex);
}

TAKErr CursorWrapper2::getType(FieldType *value, const std::size_t columnIndex) NOTHROWS
{
    return filter->getType(value, columnIndex);
}

TAKErr CursorWrapper2::isNull(bool *value, const std::size_t columnIndex) NOTHROWS
{
    return filter->isNull(value, columnIndex);
}

TAKErr CursorWrapper2::bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS
{
    return filter->bindBlob(idx, blob, size);
}

TAKErr CursorWrapper2::bindInt(const std::size_t idx, const int32_t value) NOTHROWS
{
    return filter->bindInt(idx, value);
}

TAKErr CursorWrapper2::bindLong(const std::size_t idx, const int64_t value) NOTHROWS
{
    return filter->bindLong(idx, value);
}

TAKErr CursorWrapper2::bindDouble(const std::size_t idx, const double value) NOTHROWS
{
    return filter->bindDouble(idx, value);
}

TAKErr CursorWrapper2::bindString(const std::size_t idx, const char *value) NOTHROWS
{
    return filter->bindString(idx, value);
}

TAKErr CursorWrapper2::bindNull(const std::size_t idx) NOTHROWS
{
    return filter->bindNull(idx);
}

TAKErr CursorWrapper2::clearBindings() NOTHROWS
{
    return filter->clearBindings();
}
