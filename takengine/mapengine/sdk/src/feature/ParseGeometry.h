////============================================================================
////
////    FILE:           ParseGeometry.h
////
////    DESCRIPTION:    Geometry parsing utility functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 16, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_PARSE_GEOMETRY_H_INCLUDED
#define ATAKMAP_FEATURE_PARSE_GEOMETRY_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <iosfwd>

#include "port/Platform.h"
#include "util/Blob.h"
#include "util/IO_Decls.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


class ENGINE_API Geometry;


}                                       // Close feature namespace.

namespace util
{


struct IO_Error;


}
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


typedef atakmap::util::BlobImpl   ByteBuffer;


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


//
// Parses a Geometry from the supplied byte buffer.
//
ENGINE_API Geometry*
parseBlob (const ByteBuffer&)
    throw (util::IO_Error);

//
// Parses a Geometry from the supplied input stream.
//
ENGINE_API Geometry*
parseBlob (std::istream&)
    throw (util::IO_Error);

//
// Parses a Geometry from the supplied byte buffer.
//
ENGINE_API Geometry*
parseWKB (const ByteBuffer&)
    throw (util::IO_Error);

//
// Parses a Geometry from the supplied input stream.
//
ENGINE_API Geometry*
parseWKB (std::istream&)
    throw (util::IO_Error);

//
// Parses a Geometry from the supplied string.
//
ENGINE_API Geometry*
parseWKT (const char* input)
    throw (util::IO_Error);


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


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

#endif  // #ifndef ATAKMAP_FEATURE_PARSE_GEOMETRY_H_INCLUDED
