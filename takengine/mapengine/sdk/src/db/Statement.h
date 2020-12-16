////============================================================================
////
////    FILE:           Statement.h
////
////    DESCRIPTION:    Abstract base class for a database statement.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 21, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_DB_STATEMENT_H_INCLUDED
#define ATAKMAP_DB_STATEMENT_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <stdint.h>
#include <utility>

#include "db/DB_Error.h"

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
///  class atakmap::db::Statement
///
///     An abstract base class for database statements.
///
///=============================================================================


class Statement
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef atakmap::util::BlobImpl
            Blob;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~Statement ()                       // Derived destructor should call close.
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    virtual
    void
    bind (std::size_t index,
          const char* value)
        throw (DB_Error)
        = 0;

    virtual
    void
    bind (std::size_t index,
          double value)
        throw (DB_Error)
        = 0;

    virtual
    void
    bind (std::size_t index,
          int value)
        throw (DB_Error)
        = 0;

    virtual
    void
    bind (std::size_t index,
          int64_t value)
        throw (DB_Error)
        = 0;

    virtual
    void
    bind (std::size_t index,
          const Blob& value)
        throw (DB_Error)
        = 0;

    virtual
    void
    bindNULL (std::size_t index)
        throw (DB_Error)
        = 0;

    virtual
    void
    clearBindings ()
        throw (DB_Error)
        = 0;

    virtual
    void
    execute ()
        throw (DB_Error)
        = 0;
#if 0
    virtual
    void
    reset()
        throw (DB_Error)
        = 0;
#endif
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

#endif  // #ifndef ATAKMAP_DB_STATEMENT_H_INCLUDED
