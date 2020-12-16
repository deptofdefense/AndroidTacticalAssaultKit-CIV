#include "util/AttributeSet.h"

#include "util/Logging2.h"

using namespace atakmap::util;

using namespace TAK::Engine::Util;

#define MEM_FN( fn )    "atakmap::util::AttributeSet::" fn ": "

AttributeSet::~AttributeSet()
NOTHROWS
{ }

void
AttributeSet::clear()
{
    attrItems.clear();
}


std::vector<const char*>
AttributeSet::getAttributeNames()
const
NOTHROWS
{
    std::vector<const char*> result;
    result.reserve(attrItems.size());
    auto iter = attrItems.begin();
    while (iter != attrItems.end()) {
        result.push_back(iter->first.c_str());
        ++iter;
    }

    return result;
}


void
AttributeSet::throwNotFound(const char* attributeName,
    const char* attributeType,
    const char* errHeader)
    const
{
    std::ostringstream strm;

    strm << errHeader << attributeName;
    if (containsAttribute(attributeName))
    {
        strm << " not of type " << attributeType;
    }
    else
    {
        strm << " not found";
    }

    throw std::invalid_argument(strm.str().c_str());
}


const AttributeSet&
AttributeSet::getAttributeSet(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getAttributeSet")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != ATTRIBUTE_SET)
    {
        throwNotFound(attrName, "ATTRIBUTE_SET", MEM_FN("getAttributeSet"));
    }

    return *static_cast<const BasicAttrItem<std::shared_ptr<AttributeSet>, ATTRIBUTE_SET> *>(iter->second.get())->get();
}

void
AttributeSet::getAttributeSet(std::shared_ptr<AttributeSet> &value, const char* attrName)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getAttributeSet")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != ATTRIBUTE_SET)
    {
        throwNotFound(attrName, "ATTRIBUTE_SET", MEM_FN("getAttributeSet"));
    }

    value = static_cast<const BasicAttrItem<std::shared_ptr<AttributeSet>, ATTRIBUTE_SET> *>(iter->second.get())->get();
}

AttributeSet::Type
AttributeSet::getAttributeType(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getAttributeType")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end())
    {
        std::ostringstream msg;
        msg << MEM_FN("getAttributeType") <<
            "Attribute not found: " <<
            attrName;
        throw std::invalid_argument(msg.str());
    }

    return iter->second->attrType();
}


AttributeSet::Blob
AttributeSet::getBlob(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getBlob")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != BLOB)
    {
        throwNotFound(attrName, "BLOB", MEM_FN("getBlob"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return AttributeSet::Blob(NULL, NULL);
    else
        return static_cast<const BasicArrayAttrItem<uint8_t, BLOB> &>(value).get();
}


std::pair<const AttributeSet::Blob*, const AttributeSet::Blob*>
AttributeSet::getBlobArray(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getBlobArray")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != BLOB_ARRAY)
    {
        throwNotFound(attrName, "BLOB_ARRAY", MEM_FN("getBlobArray"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return std::pair<const AttributeSet::Blob *, const AttributeSet::Blob *>(NULL, NULL);
    else
        return static_cast<const BlobArrayAttrItem &>(value).get();
}



double
AttributeSet::getDouble(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getDouble")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != DOUBLE)
    {
        throwNotFound(attrName, "DOUBLE", MEM_FN("getDouble"));
    }

    return static_cast<const BasicAttrItem<double, DOUBLE> *>(iter->second.get())->get();
}



std::pair<const double*, const double*>
AttributeSet::getDoubleArray(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getDoubleArray")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != DOUBLE_ARRAY)
    {
        throwNotFound(attrName, "DOUBLE_ARRAY", MEM_FN("getDoubleArray"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return std::pair<const double *, const double *>(NULL, NULL);
    else
        return static_cast<const BasicArrayAttrItem<double, DOUBLE_ARRAY> &>(value).get();
}



int
AttributeSet::getInt(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getInt")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != INT)
    {
        throwNotFound(attrName, "INT", MEM_FN("getInt"));
    }

    return static_cast<const BasicAttrItem<int, INT> *>(iter->second.get())->get();
}



std::pair<const int*, const int*>
AttributeSet::getIntArray(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getIntArray")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != INT_ARRAY)
    {
        throwNotFound(attrName, "INT_ARRAY", MEM_FN("getIntArray"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return std::pair<const int *, const int *>(NULL, NULL);
    else
        return static_cast<const BasicArrayAttrItem<int, INT_ARRAY> &>(value).get();
}



int64_t
AttributeSet::getLong(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getLong")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != LONG)
    {
        throwNotFound(attrName, "LONG", MEM_FN("getLong"));
    }

    return static_cast<const BasicAttrItem<int64_t, LONG> *>(iter->second.get())->get();
}



std::pair<const int64_t*, const int64_t*>
AttributeSet::getLongArray(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getLongArray")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != LONG_ARRAY)
    {
        throwNotFound(attrName, "LONG_ARRAY", MEM_FN("getLongArray"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return std::pair<const int64_t *, const int64_t *>(NULL, NULL);
    else
        return static_cast<const BasicArrayAttrItem<int64_t, LONG_ARRAY> &>(value).get();
}



const char*
AttributeSet::getString(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getString")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != STRING)
    {
        throwNotFound(attrName, "STRING", MEM_FN("getString"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return nullptr;
    else
        return static_cast<const BasicAttrItem<std::string, STRING> &>(value).get().c_str();
}



std::pair<const char* const*, const char* const*>
AttributeSet::getStringArray(const char* attrName)
const
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("getStringArray")
            "Received NULL attributeName");
    }

    auto iter = attrItems.find(attrName);

    if (iter == attrItems.end() || iter->second->attrType() != STRING_ARRAY)
    {
        throwNotFound(attrName, "STRING_ARRAY", MEM_FN("getStringArray"));
    }

    const AttrItem &value = *iter->second;
    if(value.isNull())
        return std::pair<const char * const *, const char * const *>(NULL, NULL);
    else
        return static_cast<const StringArrayAttrItem &>(value).get();
}



void
AttributeSet::removeAttribute(const char* attrName)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("removeAttribute")
            "Received NULL attributeName");
    }

    auto attrIter = attrItems.find(attrName);
    if (attrIter != attrItems.end()) {
        attrItems.erase(attrIter);
    }
}



void
AttributeSet::setAttributeSet(const char* attrName,
    const AttributeSet& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setAttributeSet")
            "Received NULL attributeName");
    }

    this->attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicAttrItem<std::shared_ptr<AttributeSet>, ATTRIBUTE_SET>(std::shared_ptr<AttributeSet>(new AttributeSet(value))));
}



void
AttributeSet::setBlob(const char* attrName,
    const Blob& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setBlob")
            "Received NULL attributeName");
    }

    if(!value.first)
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<BLOB>());
    else
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicArrayAttrItem<uint8_t, BLOB>(value.first, value.second));
}



void
AttributeSet::setBlobArray(const char* attrName,
    const std::pair<const Blob*, const Blob*>& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setBlobArray")
            "Received NULL attributeName");
    }

    if(!value.first)
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<BLOB_ARRAY>());
    else
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new BlobArrayAttrItem(value.first, value.second));
}



void
AttributeSet::setDouble(const char* attrName,
    double value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setDouble")
            "Received NULL attributeName");
    }

    attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicAttrItem<double, DOUBLE>(value));
}



void
AttributeSet::setDoubleArray(const char* attrName,
    const std::pair<const double*, const double*>& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setDoubleArray")
            "Received NULL attributeName");
    }

    if(!value.first)
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<DOUBLE_ARRAY>());
    else
        attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicArrayAttrItem<double, DOUBLE_ARRAY>(value.first, value.second));
}



void
AttributeSet::setInt(const char* attrName,
    int value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setInt")
            "Received NULL attributeName");
    }

    attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicAttrItem<int, INT>(value));
}



void
AttributeSet::setIntArray(const char* attrName,
    const std::pair<const int*, const int*>& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setIntArray")
            "Received NULL attributeName");
    }
    if(!value.first)
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<INT_ARRAY>());
    else
        attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicArrayAttrItem<int, INT_ARRAY>(value.first, value.second));
}



void
AttributeSet::setLong(const char* attrName,
    int64_t value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setLong")
            "Received NULL attributeName");
    }

    attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicAttrItem<int64_t, LONG>(value));
}



void
AttributeSet::setLongArray(const char* attrName,
    const std::pair<const int64_t*, const int64_t*>& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setLongArray")
            "Received NULL attributeName");
    }
    if(!value.first)
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<LONG_ARRAY>());
    else
        attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicArrayAttrItem<int64_t, LONG_ARRAY>(value.first, value.second));
}



void
AttributeSet::setString(const char* attrName,
    const char* value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setString")
            "Received NULL attributeName");
    }

    if (value)
        attrItems[attrName] = std::shared_ptr<AttrItem>(new BasicAttrItem<std::string, STRING>(value));
    else
        attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<STRING>());
}



void
AttributeSet::setStringArray(const char* attrName,
    const std::pair<const char* const*, const char* const*>& value)
{
    if (!attrName)
    {
        throw std::invalid_argument(MEM_FN("setStringArray")
            "Received NULL attributeName");
    }
    if(!value.first)
        this->attrItems[attrName] = std::shared_ptr<AttrItem>(new NullAttrItem<STRING_ARRAY>());
    else
        attrItems[attrName] = std::shared_ptr<AttrItem>(new StringArrayAttrItem(value.first, value.second));
}

AttributeSet::StringArrayAttrItem::~StringArrayAttrItem() { }

AttributeSet::StringArrayAttrItem::StringArrayAttrItem(const char * const *begin, const char * const *end) :
    nullValue(!begin)
{
    if(!nullValue) {
        ptrs.reserve(end - begin);
        strs.reserve(end - begin);
        while (begin != end) {
            if(*begin) {
                strs.push_back(*begin);
                ptrs.push_back(strs.back().c_str());
            } else {
                ptrs.push_back(nullptr);
            }
            ++begin;
        }
    }
}

AttributeSet::Type AttributeSet::StringArrayAttrItem::attrType() const { return STRING_ARRAY; }

std::pair<const char * const*, const char * const*> AttributeSet::StringArrayAttrItem::get() const {
    return std::make_pair(ptrs.data(),
        ptrs.data() + ptrs.size());
}

AttributeSet::BlobArrayAttrItem::~BlobArrayAttrItem() { }

AttributeSet::BlobArrayAttrItem::BlobArrayAttrItem(const Blob *begin, const Blob *end) :
    nullValue(!begin)
{
    if(!nullValue) {
        ptrs.reserve(end - begin);
        blobs.reserve(end - begin);
        while (begin != end) {
            if(begin->first) {
                std::vector<uint8_t> blob;
                size_t size = begin->second - begin->first;
                blob.reserve(size);
                blob.insert(blob.begin(), begin->first, begin->second);
                blobs.push_back(std::move(blob));
                ptrs.push_back(std::make_pair(blobs.back().data(), blobs.back().data() + size));
            } else {
                ptrs.push_back(std::make_pair((const unsigned char *)nullptr, (const unsigned char *)nullptr));
            }
            ++begin;
        }
    }
}

AttributeSet::Type AttributeSet::BlobArrayAttrItem::attrType() const { return BLOB_ARRAY; }

std::pair<const AttributeSet::Blob *, const AttributeSet::Blob *> AttributeSet::BlobArrayAttrItem::get() const {
    return std::make_pair(ptrs.data(),
        ptrs.data() + ptrs.size());
}

//==================================
//  PRIVATE IMPLEMENTATION
//==================================


bool
AttributeSet::invalidBlob(const Blob& blob)
{
    return !blob.first || !blob.second;
}

bool
AttributeSet::isNULL(const void* ptr)
{
    return !ptr;
}

#undef MEM_FN
