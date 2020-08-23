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
    this->sql_ = nullptr;
    if (!this->selection_.str().empty())
        this->selection_ << " AND ";
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::append(const char *s) NOTHROWS
{
    this->sql_ = nullptr;
    this->selection_ << s;
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::appendIn(const char *col, TAK::Engine::Port::Collection<TAK::Engine::Port::String> &vals) NOTHROWS
{
    TAKErr code;

    this->sql_ = nullptr;
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

    this->sql_ = nullptr;
    code = TE_Ok;

    int wildcards = 0;
    if (!vals.empty()) {
        Collection<BindArgument>::IteratorPtr valsIter(nullptr, nullptr);
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
    const std::size_t numVals = vals.size();
    if (wildcards == 0) {
        code = this->appendIn(col, numVals);
        TE_CHECKRETURN_CODE(code);
        STLListAdapter<BindArgument> listAdapter(this->args_);
        code = Collections_addAll(listAdapter, vals);
        TE_CHECKRETURN_CODE(code);
    } else if (numVals == 1) {
        this->selection_ << col;
        this->selection_ << " LIKE ?";
        Port::STLListAdapter<BindArgument> listAdapter(this->args_);
        code = Collections_addAll(listAdapter, vals);
        TE_CHECKRETURN_CODE(code);
    } else {
        this->selection_ << "(";
        if (wildcards == numVals) {
            this->selection_ << col;
            this->selection_ << " LIKE ?";
            for (std::size_t i = 1u; i < numVals; i++) {
                this->selection_ << " OR ";
                this->selection_ << col;
                this->selection_ << " LIKE ?";
            }
            STLListAdapter<BindArgument> listAdapter(this->args_);
            code = Collections_addAll(listAdapter, vals);
            TE_CHECKRETURN_CODE(code);
        } else {
            std::vector<BindArgument> nonWC;
            nonWC.reserve(numVals - wildcards);

            this->selection_ << "(";
            this->selection_ << col;
            this->selection_ << " LIKE ?";

            if (!vals.empty()) {
                Collection<BindArgument>::IteratorPtr valsIter(nullptr, nullptr);
                code = vals.iterator(valsIter);
                TE_CHECKRETURN_CODE(code);

                do {
                    BindArgument arg;
                    code = valsIter->get(arg);
                    TE_CHECKBREAK_CODE(code);

                    if (isWildcard(arg)) {
                        this->args_.push_back(arg);
                        wildcards--;
                        if (wildcards > 0) {
                            this->selection_ << " OR ";
                            this->selection_ << col;
                            this->selection_ << " LIKE ?";
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
            this->selection_ << ") OR (";

            code = this->appendIn(col, nonWC.size());
            TE_CHECKRETURN_CODE(code);

            this->args_.insert(this->args_.end(), nonWC.begin(), nonWC.end());
            this->selection_ << ")";
        }
        this->selection_ << ")";
    }

    return code;
}

TAKErr WhereClauseBuilder2::appendIn(const char *col, const std::size_t numArgs) NOTHROWS
{
    this->sql_ = nullptr;

    if (numArgs == 1) {
        this->selection_ << col;
        this->selection_ << " = ?";
    } else {
        this->selection_ << col;
        this->selection_ << " IN (";
        if (numArgs > 0)
            this->selection_ << "?";
        for (std::size_t i = 1u; i < numArgs; i++)
            this->selection_ << ", ?";
        this->selection_ << ")";
    }

    return TE_Ok;
}

TAKErr WhereClauseBuilder2::addArg(const BindArgument arg) NOTHROWS
{
    this->args_.push_back(arg);
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

    args_.insert(args_.end(), bindArgs.begin(), bindArgs.end());

    return code;
}

TAKErr WhereClauseBuilder2::addArgs(TAK::Engine::Port::Collection<BindArgument> &vals) NOTHROWS
{
    STLListAdapter<BindArgument> listAdapter(this->args_);
    return Collections_addAll(listAdapter, vals);
}

TAKErr WhereClauseBuilder2::getSelection(const char **retval) NOTHROWS
{
    std::string sel = this->selection_.str();
    if (sel.empty()) {
        *retval = nullptr;
        return TE_Ok;
    } else if(!this->sql_) {
        this->sql_ = sel.c_str();
    }
    *retval = this->sql_.get();
    return TE_Ok;
}

TAKErr WhereClauseBuilder2::getBindArgs(TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS
{
    STLListAdapter<BindArgument> listAdapter(this->args_);
    return Collections_addAll(args, listAdapter);
}

TAKErr WhereClauseBuilder2::clear() NOTHROWS
{
    this->args_.clear();
    this->sql_ = nullptr;
    this->selection_.str() = std::string();
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
