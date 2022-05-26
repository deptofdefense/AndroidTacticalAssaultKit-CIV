////============================================================================
////
////    FILE:           SimpleSchemaDefinition.h
////
////    DESCRIPTION:    Concrete OGR schema definition class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 24, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_SIMPLE_SCHEMA_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_SIMPLE_SCHEMA_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <set>

#include "feature/OGR_SchemaDefinition.h"

#include "string/StringLess.hh"


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
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::SimpleSchemaDefinition
///
///     Concrete singleton default OGR schema definition class.
///
///=============================================================================


class SimpleSchemaDefinition
  : public OGR_SchemaDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    SimpleSchemaDefinition (const char* nameColumn,
                            const StringVector& columnNames)
      : nameColumn (1, nameColumn),
        columnNames (columnNames.begin (), columnNames.end ())
      { }

    ~SimpleSchemaDefinition ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //


    //==================================
    //  OGR_SchemaDefinition INTERFACE
    //==================================


    StringVector
    getNameFields (const char* filePath,
                   const OGRFeatureDefn&)
        const
      { return nameColumn; }

    //
    // Returns true if all of the Feature definition's field names match all of
    // the schema's column names.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    bool
    matches (const char* filePath,
             const OGRFeatureDefn&)
        const;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef std::set<PGSC::String, PGSC::StringCaseLess>
            StringSet;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    StringVector nameColumn;
    StringSet columnNames;
  };


}                                       // Close feature namespace.
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

#endif  // #ifndef ATAKMAP_FEATURE_SIMPLE_SCHEMA_DEFINITION_H_INCLUDED
