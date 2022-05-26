#include "db/Cursor.h"

using namespace atakmap::db;

///=====================================
///  Cursor MEMBER FUNCTIONS
///=====================================


Cursor::~Cursor()
    NOTHROWS
{ }


///=====================================
///  CursorProxy MEMBER FUNCTIONS
///=====================================


CursorProxy::CursorProxy(const std::shared_ptr<Cursor> &subject)
    throw (CursorError)
    : subject(subject)
{
    if (!subject)
    {
        throw CursorError("atakmap::db::CursorProxy::CursorProxy: "
            "Received NULL Cursor");
    }
}


///=====================================
///  FilteredCursor MEMBER FUNCTIONS
///=====================================

FilteredCursor::FilteredCursor(const std::shared_ptr<Cursor> &subject)
    throw (CursorError)
    : CursorProxy(subject)
{ }


bool
FilteredCursor::moveToNext()
    throw (CursorError)
{
    bool accepted(false);

    while (!accepted && CursorProxy::moveToNext())
    {
        accepted = accept();
    }
    return accepted;
}
