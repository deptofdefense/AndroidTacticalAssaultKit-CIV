////============================================================================
////
////    FILE:           DB_Error.h
////
////    DESCRIPTION:    Definition of DB_Error exception structure.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Feb 17, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_DB_DB_ERROR_H_INCLUDED
#define ATAKMAP_DB_DB_ERROR_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <stdexcept>


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
///  struct atakmap::db::DB_Error
///
///     Exception type for database-related errors.
///
///=============================================================================


struct DB_Error
  : std::runtime_error
  {
    DB_Error (const char* errString)
      : std::runtime_error (errString)
      { }
  };


///=============================================================================
///
///  struct atakmap::db::DB_Busy
///
///     Exception type for database timeout-related errors.  May indicate a
///     recoverable condition that may be retried (e.g., a transaction commit or
///     a statement not inside of a transaction).
///
///=============================================================================


struct DB_Busy
  : DB_Error
  {
    DB_Busy (const char* errString)
      : DB_Error (errString)
      { }
  };


///=============================================================================
///
///  struct atakmap::db::DB_Interrupted
///
///     Exception type for interrupted database operations.  May indicate a
///     recoverable condition that may be retried.  If the interrupted operation
///     is an INSERT, UPDATE, or DELETE that is inside an explicit transaction,
///     then the entire transaction will be rolled back automatically.
///
///=============================================================================


struct DB_Interrupted
  : DB_Error
  {
    DB_Interrupted (const char* errString)
      : DB_Error (errString)
      { }
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

#endif  // #ifndef ATAKMAP_DB_DB_ERROR_H_INCLUDED
