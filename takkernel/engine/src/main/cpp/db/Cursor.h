////============================================================================
////
////    FILE:           Cursor.h
////
////    DESCRIPTION:    Abstract base classes for database cursors.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 17, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_DB_CURSOR_H_INCLUDED
#define ATAKMAP_DB_CURSOR_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <stdint.h>
#include <utility>
#include <vector>

#include "util/Blob.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


///=============================================================================
///
///  class atakmap::db::Cursor
///
///     An abstract base class for database cursors.
///
///=============================================================================


class Cursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef atakmap::util::BlobImpl
            Blob;

    struct CursorError
      : std::logic_error
      {
        CursorError (const char* errString)
          : std::logic_error (errString)
          { }
      };

    struct CursorBusy
      : CursorError
      {
        CursorBusy (const char* errString)
          : CursorError (errString)
          { }
      };

    struct CursorInterrupted
      : CursorError
      {
        CursorInterrupted (const char* errString)
          : CursorError (errString)
          { }
      };

    enum FieldType                      // Values match android.database.Cursor.
      {
        NULL_FIELD,
        INTEGER_FIELD,
        FLOAT_FIELD,
        STRING_FIELD,
        BLOB_FIELD
      };


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~Cursor ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    virtual
    Blob
    getBlob (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    std::size_t
    getColumnCount ()
        const
        = 0;

    virtual
    std::size_t
    getColumnIndex (const char* columnName)
        const
        throw (CursorError)
        = 0;

    virtual
    const char*
    getColumnName (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    std::vector<const char*>
    getColumnNames ()
        const
        = 0;

    virtual
    double
    getDouble (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    int
    getInt (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    int64_t
    getLong (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    const char*
    getString (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    FieldType
    getType (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    bool
    isNull (std::size_t column)
        const
        throw (CursorError)
        = 0;

    virtual
    bool
    moveToNext ()
        throw (CursorError)
        = 0;
  };


///=============================================================================
///
///  class atakmap::db::CursorProxy
///
///     Forwarding proxy for a Cursor.
///
///=============================================================================


class CursorProxy
  : public Cursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//
#ifdef __clang__
      CursorProxy(const CursorProxy &) = default;
#endif

  CursorProxy (const std::shared_ptr<Cursor> &subject)       // Adopts subject cursor.
        throw (CursorError);            // Thrown if subject is NULL.

    ~CursorProxy ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //


    //==================================
    //  Cursor INTERFACE
    //==================================


    Blob
    getBlob (std::size_t column)
        const
        throw (CursorError)
      { return subject->getBlob (column); }

    std::size_t
    getColumnCount ()
        const
      { return subject->getColumnCount (); }

    std::size_t
    getColumnIndex (const char* columnName)
        const
        throw (CursorError)
      { return subject->getColumnIndex (columnName); }

    const char*
    getColumnName (std::size_t column)
        const
        throw (CursorError)
      { return subject->getColumnName (column); }

    std::vector<const char*>
    getColumnNames ()
        const
      { return subject->getColumnNames (); }

    double
    getDouble (std::size_t column)
        const
        throw (CursorError)
      { return subject->getDouble (column); }

    int
    getInt (std::size_t column)
        const
        throw (CursorError)
      { return subject->getInt (column); }

    int64_t
    getLong (std::size_t column)
        const
        throw (CursorError)
      { return subject->getLong (column); }

    const char*
    getString (std::size_t column)
        const
        throw (CursorError)
      { return subject->getString (column); }

    FieldType
    getType (std::size_t column)
        const
        throw (CursorError)
      { return subject->getType (column); }

    bool
    isNull (std::size_t column)
        const
        throw (CursorError)
      { return subject->isNull (column); }

    bool
    moveToNext ()
        throw (CursorError)
      { return subject->moveToNext (); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    Cursor&
    getSubject ()
        const
        NOTHROWS
      { return *subject; }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::shared_ptr<Cursor> subject;
  };


///=============================================================================
///
///  class atakmap::db::FileCursor
///
///     Abstract base class for accessing queryFiles results.
///
///=============================================================================


class FileCursor
  : public CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~FileCursor ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    //
    // Returns the file path corresponding to the current row.
    //
    virtual
    const char*
    getFile ()
        const
        throw (CursorError)
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    FileCursor (const std::shared_ptr<Cursor> &subject)
        throw (CursorError)
      : CursorProxy (subject)
      { }
  };


///=============================================================================
///
///  class atakmap::db::FilteredCursor
///
///     Abstract filtering proxy for a Cursor.
///
///=============================================================================


class FilteredCursor
  : public CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    FilteredCursor (const std::shared_ptr<Cursor> &subject)    // Adopts subject cursor.
        throw (CursorError);            // Thrown if subject is NULL.

    ~FilteredCursor ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    //
    // Returns true if the current item is accepted by the filtering criteria.
    //
    virtual
    bool
    accept ()
        const
        = 0;


    //==================================
    //  Cursor INTERFACE
    //==================================


    bool
    moveToNext ()                       // Skips unaccepted items.
        throw (CursorError);
  };


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_DB_CURSOR_H_INCLUDED
