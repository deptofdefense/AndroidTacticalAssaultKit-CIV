////============================================================================
////
////    FILE:           OGR_SchemaDefinition.cpp
////
////    DESCRIPTION:    Implementation of OGR_SchemaDefinition static member
////                    functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 22, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/OGR_SchemaDefinition.h"

#include <algorithm>
#include <functional>
#include <set>
#include <stdexcept>

#include "thread/Lock.h"
#include "thread/Mutex.h"


#define MEM_FN( fn )    "atakmap::feature::OGR_SchemaDefinition::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

using namespace TAK::Engine::Thread;

////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


class SchemaMatcher
  : public std::unary_function<const feature::OGR_SchemaDefinition*, bool>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    SchemaMatcher (const char* filePath,
                   const OGRFeatureDefn& featureDef)
      : filePath (filePath),
        featureDef (featureDef)
      { }

    bool
    operator() (const feature::OGR_SchemaDefinition* schemaDef)
        const
      { return schemaDef && schemaDef->matches (filePath, featureDef); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    const char* filePath;
    const OGRFeatureDefn& featureDef;
  };


typedef std::set<const feature::OGR_SchemaDefinition*>
        SchemaSet;


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


Mutex&
schemaSetMutex()
    NOTHROWS
  {
    static Mutex m(TEMT_Recursive);
    return m;
  }

SchemaSet&
getSchemaSet ()
  {
    static SchemaSet schemaSet;
    return schemaSet;
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=====================================
///  OGR_SchemaDefinition MEMBER FUNCTIONS
///=====================================

OGR_SchemaDefinition::~OGR_SchemaDefinition ()
    NOTHROWS
  { }

const OGR_SchemaDefinition*
OGR_SchemaDefinition::getSchema (const char* filePath,
                                 const OGRFeatureDefn& featureDef)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("getSchema")
                                     "Received NULL filePath");
      }

    Lock lock(schemaSetMutex());
    SchemaSet& schemaSet (getSchemaSet ());

    auto end (schemaSet.end ());
    auto iter
        (std::find_if (schemaSet.begin (), end,
                       SchemaMatcher (filePath, featureDef)));

    return iter != end ? *iter : nullptr;
  }


void
OGR_SchemaDefinition::registerSchema (const OGR_SchemaDefinition* schema)
  {
    if (schema)
      {
        Lock lock(schemaSetMutex());
        SchemaSet& schemaSet(getSchemaSet());

        schemaSet.insert (schema);
      }
  }


void
OGR_SchemaDefinition::unregisterSchema (const OGR_SchemaDefinition* schema)
  {
    if (schema)
      {
        Lock lock(schemaSetMutex());
        SchemaSet& schemaSet(getSchemaSet());

        schemaSet.erase (schema);
      }
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
