////============================================================================
////
////    FILE:           WhereClauseBuilder.h
////
////    DESCRIPTION:    Concrete class for building SQL where clauses and
////                    associated argument lists.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Feb 3, 2015   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_DB_WHERE_CLAUSE_BUILDER_H_INCLUDED
#define ATAKMAP_DB_WHERE_CLAUSE_BUILDER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <functional>
#include <iomanip>
#include <iterator>
#include <sstream>
#include <vector>

#include "port/String.h"


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
///  class atakmap::db::WhereClauseBuilder
///
///     Concrete class for building SQL where clauses and argument lists.
///
///=============================================================================


class WhereClauseBuilder
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~WhereClauseBuilder ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    template <typename T>
    WhereClauseBuilder&
    addArg (T arg);

    //
    // Throws std::invalid_argument if the supplied arg is NULL.
    //
    WhereClauseBuilder&
    addArg (const char* arg);

    WhereClauseBuilder&
    addArg (const TAK::Engine::Port::String&);

    template <typename T>
    WhereClauseBuilder&
    addArgs (const std::vector<T>&);

    //
    // Throws std::invalid_argument if one of the supplied args is NULL.
    //
    WhereClauseBuilder&
    addArgs (const std::vector<const char*>&);

    WhereClauseBuilder&
    addArgs (const std::vector<TAK::Engine::Port::String>&);

    //
    // Throws std::invalid_argument if the supplied subClause is NULL.
    //
    WhereClauseBuilder&
    append (const char* subClause);

    //
    // Throws std::invalid_argument if the supplied columnName is NULL.
    //
    template <typename T>
    WhereClauseBuilder&
    appendIn (const char* columnName,
              const std::vector<T>& args);

    //
    // Throws std::invalid_argument if the supplied columnName or one of the
    // supplied args is NULL.
    //
    WhereClauseBuilder&
    appendIn (const char* columnName,
              const std::vector<const char*>& args);

    WhereClauseBuilder&
    appendIn (const char* columnName,
              const std::vector<TAK::Engine::Port::String>& args);

    void
    clear ();

    bool
    empty ()
        const
      { return !whereStrm.tellp (); }

    //
    // The vector contents are valid until any non-const member function of
    // WhereClauseBuilder is called.
    //
    std::vector<const char*>
    getArgs ()
        const;

    TAK::Engine::Port::String
    getClause ()
        const
      { return whereStrm.str ().c_str (); }

    WhereClauseBuilder&
    newConjunct ();                     // Begins a new operand to conjunction.


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    void
    appendIn (const char* columnName,
              std::size_t valCount);

    template <typename T>
    static
    TAK::Engine::Port::String
    toString (T val)
      {
        std::ostringstream strm;

        strm << std::setprecision (16) << val;
        return strm.str ().c_str ();
      }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    mutable std::ostringstream whereStrm;
    std::vector<TAK::Engine::Port::String> whereArgs;
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


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


#define MEM_FN( fn )    "atakmap::db::WhereClauseBuilder::" fn ": "


template <typename T>
inline
WhereClauseBuilder&
WhereClauseBuilder::addArg (T arg)
  {
    whereArgs.push_back (toString (arg));
    return *this;
  }

template <typename T>
inline
WhereClauseBuilder&
WhereClauseBuilder::addArgs (const std::vector<T>& args)
  {
    std::transform (args.begin (), args.end (),
                    std::back_inserter (whereArgs),
                    std::ptr_fun (toString<T>));
    return *this;
  }


template <typename T>
inline
WhereClauseBuilder&
WhereClauseBuilder::appendIn (const char* columnName,
                              const std::vector<T>& args)
  {
    appendIn (columnName, args.size ());
    std::transform (args.begin (), args.end (),
                    std::back_inserter (whereArgs),
                    std::ptr_fun (toString<T>));
    return *this;
  }

#undef MEM_FN


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


#endif  // #ifndef ATAKMAP_DB_WHERE_CLAUSE_BUILDER_H_INCLUDED
