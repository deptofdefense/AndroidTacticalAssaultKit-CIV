////============================================================================
////
////    FILE:           WhereClauseBuilder.cpp
////
////    DESCRIPTION:    Implementation of WhereClauseBuilder class.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "db/WhereClauseBuilder.h"


#define MEM_FN( fn )    "atakmap::db::WhereClauseBuilder::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;


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

namespace                               // Open unnamed namespace.
{

}                                       // Close unnamed namespace.

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


inline
bool
isNULL (const char* arg)
  { return !arg; }


inline
bool
isWildcard (const char* arg)
  { return std::strchr (arg, '%') != nullptr; }


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


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{

WhereClauseBuilder&
WhereClauseBuilder::addArg(const TAK::Engine::Port::String& arg)
{
    return addArg(static_cast<const char*> (arg));
}


WhereClauseBuilder&
WhereClauseBuilder::addArgs(const std::vector<TAK::Engine::Port::String>& args)
{
    return addArgs(std::vector<const char*>(args.begin(), args.end()));
}


WhereClauseBuilder&
WhereClauseBuilder::appendIn(const char* columnName,
    const std::vector<TAK::Engine::Port::String>& args)
{
    return appendIn(columnName,
        std::vector<const char*>(args.begin(), args.end()));
}


WhereClauseBuilder&
WhereClauseBuilder::newConjunct()
{
    if (whereStrm.tellp())
    {
        whereStrm << " AND ";
    }
    return *this;
}


void
WhereClauseBuilder::clear()
{
    whereStrm.str(std::string());
    whereArgs.clear();
}


std::vector<const char*>
WhereClauseBuilder::getArgs()
    const
{
    std::vector<const char*> result(whereArgs.size());

    std::copy(whereArgs.begin(), whereArgs.end(), result.begin());
    return result;
}



WhereClauseBuilder&
WhereClauseBuilder::addArg (const char* arg)
  {
    if (!arg)
      {
        throw std::invalid_argument (MEM_FN ("addArg") "Received NULL arg");
      }
    whereArgs.push_back (arg);
    return *this;
  }


WhereClauseBuilder&
WhereClauseBuilder::addArgs (const std::vector<const char*>& args)
  {
    auto end (args.end ());

    if (std::find_if (args.begin (), end, std::ptr_fun (isNULL)) != end)
      {
        throw std::invalid_argument (MEM_FN ("addArgs") "Received NULL arg");
      }
    std::copy (args.begin (), end, std::back_inserter (whereArgs));
    return *this;
  }


WhereClauseBuilder&
WhereClauseBuilder::append (const char* subClause)
  {
    if (!subClause)
      {
        throw std::invalid_argument (MEM_FN ("append")
                                     "Received NULL subClause");
      }
    whereStrm << subClause;
    return *this;
  }


WhereClauseBuilder&
WhereClauseBuilder::appendIn (const char* columnName,
                              const std::vector<const char*>& args)
  {
    auto end (args.end ());

    if (std::find_if (args.begin (), end, std::ptr_fun (isNULL)) != end)
      {
        throw std::invalid_argument (MEM_FN ("appendIn") "Received NULL arg");
      }
    if (!columnName)
      {
        throw std::invalid_argument (MEM_FN ("appendIn")
                                     "Received NULL columnName");
      }

    std::size_t argCount (args.size ());
    std::size_t wildCount (std::count_if (args.begin (), end,
                                          std::ptr_fun (isWildcard)));

    if (!argCount)
      {
        throw std::invalid_argument (MEM_FN ("appendIn")
                                     "Received empty list of arguments");
      }
    if (!wildCount)
      {
        appendIn (columnName, argCount);
        std::copy (args.begin (), end, std::back_inserter (whereArgs));
      }
    else if (argCount == 1)
      {
        whereStrm << columnName << " LIKE ?";
        std::copy (args.begin (), end, std::back_inserter (whereArgs));
      }
    else if (wildCount == argCount)
      {
        whereStrm << "(" << columnName << " LIKE ?";
        for (std::size_t i (1); i < argCount; ++i)
          {
            whereStrm << " OR " << columnName << " LIKE ?";
          }
        whereStrm << ")";
        std::copy (args.begin (), end, std::back_inserter (whereArgs));
      }
    else                                // A subset of args are wildcards.
      {
        //
        // We are adding a disjunction of the form:
        //
        // ((colName LIKE ? OR colName LIKE ?) OR colName IN (?, ?))
        //
        // where the first term of the disjunction will refer to wildcard args
        // and the second term will refer to the remaining "tame" args.
        //
        std::vector<const char*> tame (argCount - wildCount);

        whereStrm << "((";
        for (auto iter (args.begin ());
             iter != end;
             ++iter)
          {
            if (wildCount && isWildcard (*iter))
              {
                whereStrm << columnName
                          << (--wildCount ? " LIKE ? OR " : " LIKE ?");
                whereArgs.push_back (*iter);
              }
            else
              {
                tame.push_back (*iter);
              }
          }
        whereStrm << ") OR (";
        appendIn (columnName, tame.size ());
        whereStrm << "))";
        std::copy (tame.begin (), tame.end (), std::back_inserter (whereArgs));
      }

    return *this;
  }


}                                       // Close db namespace.
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


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


void
WhereClauseBuilder::appendIn (const char* columnName,
                              std::size_t argCount)
  {
    if (!columnName)
      {
        throw std::invalid_argument (MEM_FN ("appendIn")
                                     "Received NULL columnName");
      }
    if (!argCount)
      {
        throw std::invalid_argument (MEM_FN ("appendIn")
                                     "Received empty list of arguments");
      }
    whereStrm << columnName;
    if (argCount == 1)
      {
        whereStrm << " = ?";
      }
    else
      {
        whereStrm << " IN (?";
        for (std::size_t i (1); i < argCount; ++i)
          {
            whereStrm << ", ?";
          }
        whereStrm << ")";
      }
  }


}                                       // Close db namespace.
}                                       // Close atakmap namespace.

