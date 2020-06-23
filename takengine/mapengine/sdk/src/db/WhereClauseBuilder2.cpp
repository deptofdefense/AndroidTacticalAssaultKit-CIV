#include "db/WhereClauseBuilder2.h"

#include <vector>

#include "db/BindArgument.h"
#include "port/Collections.h"
#include "port/STLListAdapter.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::DB;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

WhereClauseBuilder2::WhereClauseBuilder2() NOTHROWS
{}

TAKErr WhereClauseBuilder2::beginCondition() NOTHROWS
{
    this->sql = NULL;
    if (!this->selection.str().empty())
        this->selection << " AND ";
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::append(const char *s) NOTHROWS
{
    this->sql = NULL;
    this->selection << s;
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::appendIn(const char *col, TAK::Engine::Port::Collection<TAK::Engine::Port::String> &vals) NOTHROWS
{
    TAKErr code;

    this->sql = NULL;
    std::list<BindArgument> bindArgs;
    struct StringTransmute
    {
        BindArgument operator()(const Port::String &arg)
        {
            BindArgument retval(arg);
            retval.own();
            return retval;
        }
    };
    
    {
    STLListAdapter<BindArgument> listAdapter(bindArgs);
    code = Collections_transmute<Port::String, BindArgument, StringTransmute>(listAdapter, vals);
    TE_CHECKRETURN_CODE(code);
    }
    
    {
    STLListAdapter<BindArgument> listAdapter(bindArgs);
    code = this->appendIn(col, listAdapter);
    TE_CHECKRETURN_CODE(code);
    }
    
    return code;
}

TAKErr WhereClauseBuilder2::appendIn(const char *col, Collection<BindArgument> &vals) NOTHROWS
{
    TAKErr code;

    this->sql = NULL;
    code = TE_Ok;

    int wildcards = 0;
    if (!vals.empty()) {
        Collection<BindArgument>::IteratorPtr valsIter(NULL, NULL);
        code = vals.iterator(valsIter);
        TE_CHECKRETURN_CODE(code);

        do {
            BindArgument arg;
            code = valsIter->get(arg);
            TE_CHECKBREAK_CODE(code);

            if (isWildcard(arg))
                wildcards++;
            code = valsIter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
    }
    const  int numVals = vals.size();
    if (wildcards == 0) {
        code = this->appendIn(col, numVals);
        TE_CHECKRETURN_CODE(code);
        STLListAdapter<BindArgument> listAdapter(this->args);
        code = Collections_addAll(listAdapter, vals);
        TE_CHECKRETURN_CODE(code);
    } else if (numVals == 1) {
        this->selection << col;
        this->selection << " LIKE ?";
        Port::STLListAdapter<BindArgument> listAdapter(this->args);
        code = Collections_addAll(listAdapter, vals);
        TE_CHECKRETURN_CODE(code);
    } else {
        this->selection << "(";
        if (wildcards == numVals) {
            this->selection << col;
            this->selection << " LIKE ?";
            for (int i = 1; i < numVals; i++) {
                this->selection << " OR ";
                this->selection << col;
                this->selection << " LIKE ?";
            }
            STLListAdapter<BindArgument> listAdapter(this->args);
            code = Collections_addAll(listAdapter, vals);
            TE_CHECKRETURN_CODE(code);
        } else {
            std::vector<BindArgument> nonWC;
            nonWC.reserve(numVals - wildcards);

            this->selection << "(";
            this->selection << col;
            this->selection << " LIKE ?";

            if (!vals.empty()) {
                Collection<BindArgument>::IteratorPtr valsIter(NULL, NULL);
                code = vals.iterator(valsIter);
                TE_CHECKRETURN_CODE(code);

                do {
                    BindArgument arg;
                    code = valsIter->get(arg);
                    TE_CHECKBREAK_CODE(code);

                    if (isWildcard(arg)) {
                        this->args.push_back(arg);
                        wildcards--;
                        if (wildcards > 0) {
                            this->selection << " OR ";
                            this->selection << col;
                            this->selection << " LIKE ?";
                        }
                    } else {
                        nonWC.push_back(arg);
                    }
                    code = valsIter->next();
                    TE_CHECKBREAK_CODE(code);
                } while (true);
                if (code == TE_Done)
                    code = TE_Ok;
                TE_CHECKRETURN_CODE(code);
            }
            this->selection << ") OR (";

            code = this->appendIn(col, nonWC.size());
            TE_CHECKRETURN_CODE(code);

            this->args.insert(this->args.end(), nonWC.begin(), nonWC.end());
            this->selection << ")";
        }
        this->selection << ")";
    }

    return code;
}

TAKErr WhereClauseBuilder2::appendIn(const char *col, const std::size_t numArgs) NOTHROWS
{
    this->sql = NULL;

    if (numArgs == 1) {
        this->selection << col;
        this->selection << " = ?";
    } else {
        this->selection << col;
        this->selection << " IN (";
        if (numArgs > 0)
            this->selection << "?";
        for (int i = 1; i < numArgs; i++)
            this->selection << ", ?";
        this->selection << ")";
    }

    return TE_Ok;
}

TAKErr WhereClauseBuilder2::addArg(const BindArgument arg) NOTHROWS
{
    this->args.push_back(arg);
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::addArgs(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &vals) NOTHROWS
{
    TAKErr code;

    std::list<BindArgument> bindArgs;
    struct StringTransmute
    {
        BindArgument operator()(const Port::String &arg)
        {
            BindArgument retval(arg);
            retval.own();
            return retval;
        }
    };
    STLListAdapter<BindArgument> listAdapter(bindArgs);
    code = Collections_transmute<Port::String, BindArgument, StringTransmute>(listAdapter, vals);
    TE_CHECKRETURN_CODE(code);

    args.insert(args.end(), bindArgs.begin(), bindArgs.end());

    return code;
}

TAKErr WhereClauseBuilder2::addArgs(TAK::Engine::Port::Collection<BindArgument> &vals) NOTHROWS
{
    STLListAdapter<BindArgument> listAdapter(this->args);
    return Collections_addAll(listAdapter, vals);
}

TAKErr WhereClauseBuilder2::getSelection(const char **retval) NOTHROWS
{
    std::string sel = this->selection.str();
    if (sel.empty()) {
        *retval = NULL;
        return TE_Ok;
    } else if(!this->sql) {
        this->sql = sel.c_str();
    }
    *retval = this->sql.get();
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::getBindArgs(TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS
{
    STLListAdapter<BindArgument> listAdapter(this->args);
    return Collections_addAll(args, listAdapter);
}

TAKErr WhereClauseBuilder2::clear() NOTHROWS
{
    this->args.clear();
    this->sql = NULL;
    this->selection.str() = std::string();
    return TE_Ok;
}

bool WhereClauseBuilder2::isWildcard(const char *arg) NOTHROWS
{
    return isWildcard(BindArgument(arg));
}

bool WhereClauseBuilder2::isWildcard(const BindArgument &arg) NOTHROWS
{
    if(arg.getType() != Query::TEFT_String)
        return false;
    
    std::string s(arg.getValue().s);
    return (s.find('%') != std::string::npos);
}
