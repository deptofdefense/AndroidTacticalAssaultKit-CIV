#include "db/BindArgument.h"

#include <cassert>

#include "util/Memory.h"

using namespace TAK::Engine::DB;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

BindArgument::BindArgument() NOTHROWS :
    type(Query::TEFT_Null),
    value(),
    owns(false)
{
}

BindArgument::BindArgument(int val) NOTHROWS :
    type(Query::TEFT_Integer),
    value(),
    owns(false)
{
    value.l = val;
}

BindArgument::BindArgument(int64_t val) NOTHROWS :
    type(Query::TEFT_Integer),
    value(),
    owns(false)
{
    value.l = val;
}

BindArgument::BindArgument(double val) NOTHROWS :
    type(Query::TEFT_Float),
    value(),
    owns(false)
{
    value.d = val;
}

BindArgument::BindArgument(const char *val) NOTHROWS :
    type(val ? Query::TEFT_String : Query::TEFT_Null),
    value(),
    owns(false)
{
    value.s = val;
}

BindArgument::BindArgument(const uint8_t *val, const std::size_t valLen) NOTHROWS :
    type((val && valLen) ? Query::TEFT_Blob : Query::TEFT_Null),
    value(),
    owns(false)
{
    value.b.data = val;
    value.b.len = valLen;
}

BindArgument::BindArgument(const BindArgument &other) NOTHROWS :
    type(other.type),
    value(other.value),
    owns(false)
{
    if (other.owns)
        this->own();
}

BindArgument::~BindArgument() NOTHROWS
{
    this->clear();
}

void BindArgument::set(int val) NOTHROWS
{
    if (this->owns)
        this->clear();
    this->value.l = val;
    this->type = Query::TEFT_Integer;
}

void BindArgument::set(int64_t val) NOTHROWS
{
    if (this->owns)
        this->clear();
    this->value.l = val;
    this->type = Query::TEFT_Integer;
}

void BindArgument::set(double val) NOTHROWS
{
    if (this->owns)
        this->clear();
    this->value.d = val;
    this->type = Query::TEFT_Float;
}

void BindArgument::set(const char *val) NOTHROWS
{
    if (this->owns) {
        this->clear();
        
        if (val) {
            std::size_t len = strlen(val);
            array_ptr<char> dup(new char[len+1]);
            memcpy(dup.get(), val, len);
            dup.get()[len] = '\0';
            val = dup.release();
        }
    }
    this->value.s = val;
    this->type = val ? Query::TEFT_String : Query::TEFT_Null;
}

void BindArgument::set(const uint8_t *val, const std::size_t len) NOTHROWS
{
    if (this->owns) {
        this->clear();

        if (val && len) {
            array_ptr<uint8_t> dup(new uint8_t[len]);
            memcpy(dup.get(), val, len);
            val = dup.release();
        }
    }
    this->value.b.data = val;
    this->value.b.len = len;
    this->type = (val&&len) ? Query::TEFT_Blob : Query::TEFT_Null;
}

void BindArgument::clear() NOTHROWS
{
    if (this->owns) {
        switch (this->type) {
        case Query::TEFT_String :
            delete [] this->value.s;
            break;
        case Query::TEFT_Blob :
            delete [] this->value.b.data;
            break;
        default :
            break;
        }
    }

    this->type = Query::TEFT_Null;
}

void BindArgument::own() NOTHROWS
{
    if (this->owns)
        return;

    switch (this->type) {
    case Query::TEFT_String:
    {
        std::size_t len = strlen(this->value.s);
        array_ptr<char> dup(new char[len + 1]);
        memcpy(dup.get(), this->value.s, len);
        dup.get()[len] = '\0';
        this->value.s = dup.release();
        break;
    }
    case Query::TEFT_Blob:
    {
        array_ptr<uint8_t> dup(new uint8_t[this->value.b.len]);
        memcpy(dup.get(), this->value.b.data, this->value.b.len);
        this->value.b.data = dup.release();
        break;
    }
    default:
    {
        break;
    }
    }
    this->owns = true;
}

BindArgument& BindArgument::operator=(const BindArgument &other)
{
    this->type = other.type;
    if (this->owns) {
        this->clear();
        this->owns = false;
    }
    this->value = other.value;
    if (other.owns)
        this->own();
    return *this;
}
Query::FieldType BindArgument::getType() const NOTHROWS
{
    return type;
}

BindArgument::Value BindArgument::getValue() const NOTHROWS
{
    return this->value;
}

TAKErr BindArgument::bind(Bindable &stmt, const std::size_t idx) const NOTHROWS
{
    TAKErr code;
    switch (this->type) {
    case Query::TEFT_Blob:
        code = stmt.bindBlob(idx, this->value.b.data, this->value.b.len);
        break;
    case Query::TEFT_Float :
        code = stmt.bindDouble(idx, this->value.d);
        break;
    case Query::TEFT_Integer :
        code = stmt.bindLong(idx, this->value.l);
        break;
    case Query::TEFT_Null :
        code = stmt.bindNull(idx);
        break;
    case Query::TEFT_String :
        code = stmt.bindString(idx, this->value.s);
        break;
    default :
        code = TE_IllegalState;
        break;
    }
    return code;
}

bool BindArgument::operator==(const BindArgument &other) const NOTHROWS
{
    if (this->type != other.type)
        return false;

    switch (this->type) {
    case Query::TEFT_Blob:
        return (this->value.b.len == other.value.b.len) &&
                ((this->value.b.data == other.value.b.data) ||
                 (memcmp(this->value.b.data, other.value.b.data, this->value.b.len) == 0));
    case Query::TEFT_Float:
        return (this->value.d == other.value.d);
    case Query::TEFT_Integer:
        return (this->value.l == other.value.l);
    case Query::TEFT_Null:
        return true;
    case Query::TEFT_String:
        return ((this->value.s == other.value.s) ||
                (strcmp(this->value.s, other.value.s) == 0));
    default:
        // XXX - illegal state
        assert(0);
        return false;
    }
}

TAKErr BindArgument::query(QueryPtr &cursor, Database2 &database, const char *sql, TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS
{
    TAKErr code;

    code = database.compileQuery(cursor, sql);
    TE_CHECKRETURN_CODE(code);
    code = BindArgument::bind(*cursor, args);
    TE_CHECKRETURN_CODE(code);

    return code;
}
    
TAKErr BindArgument::bind(Bindable &cursor, TAK::Engine::Port::Collection<BindArgument> &args) NOTHROWS
{
    TAKErr code;

    if (args.empty())
        return TE_Ok;

    Collection<BindArgument>::IteratorPtr iter(nullptr, nullptr);
    code = args.iterator(iter);
    TE_CHECKRETURN_CODE(code);

    std::size_t idx = 1;
    do {
        BindArgument arg;
        code = iter->get(arg);
        TE_CHECKBREAK_CODE(code);

        code = arg.bind(cursor, idx++);
        TE_CHECKBREAK_CODE(code);

        code = iter->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}
