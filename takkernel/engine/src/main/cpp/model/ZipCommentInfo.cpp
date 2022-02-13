#include "ZipCommentInfo.h"

#include <memory>
#include <cmath>
#include <ctime>
#include <cassert>

#ifdef __ANDROID__
namespace std {
    template<class T>
    inline std::string to_string(const T &v)
    {
        std::ostringstream strm;
        strm << v;
        return strm.str();
    }
    inline float strtof(const char *str, char **endptr)
    {
        return ::strtof(str, endptr);
    }
    inline double strtold(const char *str, char **endptr)
    {
        return ::strtold(str, endptr);
    }
    inline long long int strtoll(const char *str, char **endptr, int base = 10)
    {
        return ::strtoll(str, endptr, base);
    }
    inline unsigned long long int strtoull(const char *str, char **endptr, int base = 10)
    {
        return ::strtoull(str, endptr, base);
    }
    inline int stoi(const std::string &str, std::size_t *numRead, int base = 10)
    {
        const char *cstr = str.c_str();
        char *endptr;
        const int result = strtol(cstr, &endptr, base);
        if(numRead)
            *numRead = (endptr-cstr);
        return result;
    }
    inline int snprintf(char *s, size_t m, const char *fmt, ...)
    {
        va_list args;
        va_start(args, fmt);
        int result = ::vsnprintf(s, m , fmt, args);
        va_end(args);
        return result;
    }
}
#endif

#include <tinygltf/json.hpp>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;
using json = nlohmann::json;

namespace {
    constexpr uint32_t schemaVersionCurrent = 1;
    constexpr uint32_t schemaVersion1 = 1;
    constexpr char schemaVersionKey[] = "schemaVersion";

    constexpr size_t maxZipCommentSize = (1L << 16) - 1;

    double handleDouble(const json& j) { 
        return j.is_null() ? NAN : j.get<double>();
    }

    template<class Iface, class Impl>
    void deleteImpl(const Iface *ptr) {
        if (!ptr) return;
        const Impl *impl = static_cast<const Impl *>(ptr);
        delete impl;
    }

    template<typename T>
    std::vector<T> makeVector(const T *begin, const T *end) {
        std::vector<T> v;
        const size_t count = end - begin;
        for (int i = 0; i < count; ++i) {
            v.push_back(begin[i]);
        }
        return v;
    }
}  // namespace

namespace TAK {
    namespace Engine {
        namespace Core {
            // map AltitudeReference values to JSON as strings
            NLOHMANN_JSON_SERIALIZE_ENUM(TAK::Engine::Core::AltitudeReference, {
                {TAK::Engine::Core::AltitudeReference::UNKNOWN, "unknown"},
                {TAK::Engine::Core::AltitudeReference::INDICATED, "indicated"},
                {TAK::Engine::Core::AltitudeReference::AGL, "AGL"},
                {TAK::Engine::Core::AltitudeReference::HAE, "HAE"}
            })
            void to_json(json& j, const TAK::Engine::Core::GeoPoint2 &point) {
                j = json{
                    {"longitude", point.longitude},
                    {"latitude", point.latitude},
                    {"altitude", point.altitude},
                    {"altitudeRef", point.altitudeRef},
                    {"ce90", point.ce90},
                    {"le90", point.le90}};
            }
            void from_json(const json &j, TAK::Engine::Core::GeoPoint2 &point) {
                point.longitude = handleDouble(j.at("longitude"));
                point.latitude = handleDouble(j.at("latitude"));
                point.altitude = handleDouble(j.at("altitude"));
                j.at("altitudeRef").get_to(point.altitudeRef);
                point.ce90 = handleDouble(j.at("ce90"));
                point.le90 = handleDouble(j.at("le90"));
            }
        } // namespace Core
        namespace Feature {
            // map AltitudeMode values to JSON as strings
            NLOHMANN_JSON_SERIALIZE_ENUM(TAK::Engine::Feature::AltitudeMode, {
                {TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround, "ClampToGround"},
                {TAK::Engine::Feature::AltitudeMode::TEAM_Relative, "Relative"},
                {TAK::Engine::Feature::AltitudeMode::TEAM_Absolute, "Absolute"}
            })
            void to_json(json& j, const TAK::Engine::Feature::Envelope2 &envelope) {
                j = json{
                    {"maxX", envelope.maxX}, {"maxY", envelope.maxY}, {"maxZ", envelope.maxZ},
                    {"minX", envelope.minX}, {"minY", envelope.minY}, {"minZ", envelope.minZ}};
            }
            void from_json(const json &j, TAK::Engine::Feature::Envelope2 &envelope) {
                envelope.maxX = handleDouble(j.at("maxX"));
                envelope.maxY = handleDouble(j.at("maxY"));
                envelope.maxZ = handleDouble(j.at("maxZ"));
                envelope.minX = handleDouble(j.at("minX"));
                envelope.minY = handleDouble(j.at("minY"));
                envelope.minZ = handleDouble(j.at("minZ"));
            }
        } // namespace Feature
        namespace Math {
            void to_json(json& j, const TAK::Engine::Math::Matrix2 &matrix) {
                double mx[16];
                TAKErr code = matrix.get(mx);
                if (code == TE_Ok)
                    j = json{{"matrix", mx}};
                else
                    j = json{{"matrix", nullptr}};
            }
            void from_json(const json &j, TAK::Engine::Math::Matrix2 &matrix) {
                if (j.at("matrix").is_array()) {
                    double mx[16];
                    json::const_iterator it = j.at("matrix").begin();
                    for (int i = 0; i < 16; i++, it++) {
                        mx[i] = *it;
                    }
                    TAK::Engine::Math::Matrix2 matrixNew(
                        mx[0], mx[1], mx[2], mx[3], mx[4], mx[5], mx[6], mx[7], mx[8], 
                        mx[9], mx[10], mx[11], mx[12], mx[13], mx[14], mx[15]);
                    matrix.set(matrixNew);
                }
            }
        } // namespace Math
    } // namespace Engine
} // namespace TAK

namespace atakmap {
    namespace util {
        // map AttributeSet::Type values to JSON as strings
        NLOHMANN_JSON_SERIALIZE_ENUM(AttributeSet::Type, {
            {AttributeSet::STRING, "STRING"},
            {AttributeSet::INT, "INT"},
            {AttributeSet::DOUBLE, "DOUBLE"},
            {AttributeSet::LONG, "LONG"},
            {AttributeSet::BLOB, "BLOB"},
            {AttributeSet::ATTRIBUTE_SET, "ATTRIBUTE_SET"},
            {AttributeSet::INT_ARRAY, "INT_ARRAY"},
            {AttributeSet::LONG_ARRAY, "LONG_ARRAY"},
            {AttributeSet::DOUBLE_ARRAY, "DOUBLE_ARRAY"},
            {AttributeSet::STRING_ARRAY, "STRING_ARRAY"},
            {AttributeSet::BLOB_ARRAY, "BLOB_ARRAY"}
        })
        void to_json(json &j, const AttributeSet &attrs) {
            std::vector<json> jsonObjects;
            for (const char *attrName : attrs.getAttributeNames()) {
                const AttributeSet::Type attrType = attrs.getAttributeType(attrName);
                switch (attrType) {
                    case AttributeSet::STRING:
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", attrs.getString(attrName)}});
                        break;
                    case AttributeSet::INT:
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", attrs.getInt(attrName)}});
                        break;
                    case AttributeSet::DOUBLE:
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", attrs.getDouble(attrName)}});
                        break;
                    case AttributeSet::LONG:
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", attrs.getLong(attrName)}});
                        break;
                    case AttributeSet::ATTRIBUTE_SET:
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", attrs.getAttributeSet(attrName)}});
                        break;
                    case AttributeSet::INT_ARRAY: {
                        AttributeSet::IntArray ptrs = attrs.getIntArray(attrName);
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", makeVector<int>(ptrs.first, ptrs.second)}});
                        break;
                    }
                    case AttributeSet::LONG_ARRAY: {
                        AttributeSet::LongArray ptrs = attrs.getLongArray(attrName);
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", makeVector<int64_t>(ptrs.first, ptrs.second)}});
                        break;
                    }
                    case AttributeSet::DOUBLE_ARRAY: {
                        AttributeSet::DoubleArray ptrs = attrs.getDoubleArray(attrName);
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", makeVector<double>(ptrs.first, ptrs.second)}});
                        break;
                    }
                    case AttributeSet::STRING_ARRAY: {
                        AttributeSet::StringArray ptrs = attrs.getStringArray(attrName);
                        const size_t count = ptrs.second - ptrs.first;
                        std::vector<std::string> strArray;
                        for (int i = 0; i < count; ++i) {
                            const char* str = ptrs.first[i];
                            const size_t len = strlen(str);
                            strArray.push_back(std::string(str, len));
                        }
                        jsonObjects.push_back(json{{"name", attrName}, {"type", attrType}, {"value", strArray}});
                        break;
                    }
                    case AttributeSet::BLOB:
                    case AttributeSet::BLOB_ARRAY:
                        // Need to decide on an appropriate JSON representation of binary data.
                    default:
                        Logger_log(TELL_Debug, "Unsupported AttributeSet type.");
                        assert(false);
                        break;
                }
            }
            j = jsonObjects;
        }
        void from_json(const json &j, AttributeSet &attrs) {
            using namespace atakmap::util;
            attrs.clear();
            if (j.is_array()) {
                for (json::const_iterator it = j.begin(); it != j.end(); it++) {
                    const AttributeSet::Type attrType = it->at("type");
                    const std::string attrName = it->at("name");
                    switch (attrType) {
                        case AttributeSet::STRING:
                            attrs.setString(attrName.c_str(), it->at("value").get<std::string>().c_str());
                            break;
                        case AttributeSet::INT:
                            attrs.setInt(attrName.c_str(), it->at("value").get<const int>());
                            break;
                        case AttributeSet::DOUBLE:
                            attrs.setDouble(attrName.c_str(), handleDouble(it->at("value")));
                            break;
                        case AttributeSet::LONG:
                            attrs.setLong(attrName.c_str(), it->at("value").get<const long>());
                            break;
                        case AttributeSet::ATTRIBUTE_SET:
                            attrs.setAttributeSet(attrName.c_str(), it->at("value").get<AttributeSet>());
                            break;
                        case AttributeSet::INT_ARRAY: {
                            const json jsonArray = it->at("value");
                            if (jsonArray.is_array()) {
                                const size_t count = jsonArray.size();
                                std::unique_ptr<int[]> valArray(new int[count]());
                                json::const_iterator it2 = jsonArray.begin();
                                for (int i = 0; i < count; i++, it2++) {
                                    valArray[i] = *it2;
                                }
                                AttributeSet::IntArray intArray(valArray.get(), valArray.get() + count);
                                attrs.setIntArray(attrName.c_str(), intArray);
                            }
                            break;
                        }
                        case AttributeSet::LONG_ARRAY: {
                            const json jsonArray = it->at("value");
                            if (jsonArray.is_array()) {
                                const size_t count = jsonArray.size();
                                std::unique_ptr<int64_t[]> valArray(new int64_t[count]());
                                json::const_iterator it2 = jsonArray.begin();
                                for (int i = 0; i < count; i++, it2++) {
                                    valArray[i] = *it2;
                                }
                                AttributeSet::LongArray longArray(valArray.get(), valArray.get() + count);
                                attrs.setLongArray(attrName.c_str(), longArray);
                            }
                            break;
                        }
                        case AttributeSet::DOUBLE_ARRAY: {
                            const json jsonArray = it->at("value");
                            if (jsonArray.is_array()) {
                                const size_t count = jsonArray.size();
                                std::unique_ptr<double[]> valArray(new double[count]());
                                json::const_iterator it2 = jsonArray.begin();
                                for (int i = 0; i < count; i++, it2++) {
                                    valArray[i] = handleDouble(*it2);
                                }
                                AttributeSet::DoubleArray doubleArray(valArray.get(), valArray.get() + count);
                                attrs.setDoubleArray(attrName.c_str(), doubleArray);
                            }
                            break;
                        }
                        case AttributeSet::STRING_ARRAY: {
                            const json jsonArray = it->at("value");
                            if (jsonArray.is_array()) {
                                const size_t count = jsonArray.size();
                                std::unique_ptr<char*, std::function<void(char**)>> valArray(
                                    new char*[count](),
                                    [count](char** x) {
                                        std::for_each(x, x + count, std::default_delete<char[]>());
                                        delete[] x;
                                    }
                                );
                                json::const_iterator it2 = jsonArray.begin();
                                for (int i = 0; i < count; i++, it2++) {
                                    std::string str = (*it2).get<std::string>();
                                    valArray.get()[i] = new char[str.length() + 1]();
                                    strncpy(valArray.get()[i], str.c_str(), str.length());
                                }
                                AttributeSet::StringArray stringArray(valArray.get(), valArray.get() + count);
                                attrs.setStringArray(attrName.c_str(), stringArray);
                            }
                            break;
                        }
                        case AttributeSet::BLOB:
                        case AttributeSet::BLOB_ARRAY:
                            // Need to decide on an appropriate JSON representation of binary data.
                        default:
                            Logger_log(TELL_Debug, "Unsupported AttributeSet type.");
                            assert(false);
                            break;
                    }
                }
            }
        }
    } // namespace util
} // namespace atakmap

class ZipCommentInfoV1 : public ZipCommentInfo {
public:
    ZipCommentInfoV1() NOTHROWS {
        location.altitudeRef = TAK::Engine::Core::AltitudeReference::UNKNOWN;
        altitudeMode = TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround;
        projection.srid = -1;
        displayResolution.max =
        displayResolution.min = NAN;
        resolution = NAN;
    }
    ~ZipCommentInfoV1() NOTHROWS {}

    TAK::Engine::Feature::Envelope2 envelope;
    TAK::Engine::Core::GeoPoint2 location;
    TAK::Engine::Feature::AltitudeMode altitudeMode;
    struct _projection {
        int srid;
        std::string wkt;
        bool operator==(const _projection &other) const { 
            return srid == other.srid && wkt == other.wkt;
        }
    } projection;
    TAK::Engine::Math::Matrix2 localFrame;
    struct _displayResolution {
        double max;
        double min;
        bool operator==(const _displayResolution &other) const {
            return max == other.max && min == other.min;
        }
    } displayResolution;
    double resolution;
    std::string date;  // YYYYMMDDhhmmss UTC
    atakmap::util::AttributeSet metadata;

    TAKErr ToString(TAK::Engine::Port::String& out) NOTHROWS override {
        json j;
        j[schemaVersionKey] = schemaVersion1;
        j["envelope"] = envelope;
        j["location"] = location;
        j["altitudeMode"] = altitudeMode;
        j["projection"] = {{"srid", projection.srid}, {"wkt", projection.wkt}};
        j["localFrame"] = localFrame;
        j["displayResolution"] = {{"max", displayResolution.max}, {"min", displayResolution.min}};
        j["resolution"] = resolution;
        j["metadata"] = metadata;

        std::vector<char> dateStr(strlen("YYYYMMDDhhmmss UTC") + 1, '\0');
        const std::time_t curTime = std::time(nullptr);
        const std::size_t bytesWritten = std::strftime(dateStr.data(), dateStr.size(), "%Y%m%d%H%M%S UTC", std::gmtime(&curTime));
        assert(bytesWritten != 0);
        j["date"] = dateStr.data();

        out = j.dump(4).c_str();

        const size_t outSize = strlen(out);
        if (outSize > maxZipCommentSize) {
            Logger_log(TELL_Info, "Zip comment size, %zu bytes, exceeds maximum allowed size of %zu bytes.", outSize, maxZipCommentSize);
            assert(outSize <= maxZipCommentSize);
        }

        return TE_Ok;
    }

    bool operator==(const ZipCommentInfo& otherBase) const NOTHROWS override {
        const ZipCommentInfoV1* other = dynamic_cast<const ZipCommentInfoV1*>(&otherBase);
        bool result = other != nullptr;
        result = result && envelope == other->envelope;
        result = result && location == other->location;
        result = result && altitudeMode == other->altitudeMode;
        result = result && projection == other->projection;
        result = result && localFrame == other->localFrame;
        result = result && displayResolution == other->displayResolution;
        result = result && resolution == other->resolution;
        return result;
    }

    bool operator!=(const ZipCommentInfo& otherBase) const NOTHROWS override {
        return !operator==(otherBase);
    }

    TAKErr GetEnvelope(TAK::Engine::Feature::Envelope2 &_envelope) const NOTHROWS override {
        _envelope = envelope;
        return TE_Ok;
    }

    TAKErr GetLocation(TAK::Engine::Core::GeoPoint2 &_location) const NOTHROWS override {
        _location = location;
        return TE_Ok;
    }

    TAKErr GetAltitudeMode(TAK::Engine::Feature::AltitudeMode& _altitudeMode) const NOTHROWS override {
        _altitudeMode = this->altitudeMode;
        return TE_Ok;
    }

    TAKErr GetProjectionSrid(int& srid) const NOTHROWS override {
        srid = projection.srid;
        return TE_Ok;
    }

    TAKErr GetProjectionWkt(TAK::Engine::Port::String &wkt) const NOTHROWS override {
        wkt = projection.wkt.c_str();
        return TE_Ok;
    }

    TAKErr GetLocalFrame(TAK::Engine::Math::Matrix2& _localFrame) const NOTHROWS override {
        _localFrame = localFrame;
        return TE_Ok;
    }

    TAKErr GetDisplayResolutions(double& max, double& min) const NOTHROWS override {
        max = displayResolution.max;
        min = displayResolution.min;
        return TE_Ok;
    }

    TAKErr GetResolution(double &_resolution) const NOTHROWS override {
        _resolution = resolution;
        return TE_Ok;
    }

    TAKErr GetMetadata(atakmap::util::AttributeSet &_metadata) const NOTHROWS override {
        _metadata = metadata;
        return TE_Ok;
    }

    void SetEnvelope(const TAK::Engine::Feature::Envelope2 &_envelope) NOTHROWS override {
        envelope = _envelope;
    }

    void SetLocation(const TAK::Engine::Core::GeoPoint2 &_location) NOTHROWS override {
        location = _location;
    }

    void SetAltitudeMode(const TAK::Engine::Feature::AltitudeMode _altitudeMode) NOTHROWS override {
        altitudeMode = _altitudeMode;
    }

    void SetProjectionSrid(const int srid) NOTHROWS override {
        projection.srid = srid;
    }

    void SetProjectionWkt(const TAK::Engine::Port::String &wkt) NOTHROWS override {
        projection.wkt = wkt == nullptr ? "" : wkt.get();
    }

    void SetLocalFrame(const TAK::Engine::Math::Matrix2 &_localFrame) NOTHROWS override {
        localFrame = _localFrame;
    }

    void SetDisplayResolutions(const double& max, const double &min) NOTHROWS override {
        displayResolution.max = max;
        displayResolution.min = min;
    }

    void SetResolution(const double &_resolution) NOTHROWS override {
        resolution = _resolution;
    }

    void SetMetadata(const atakmap::util::AttributeSet &_metadata) NOTHROWS override {
        metadata = _metadata;
    }
};

typedef std::unique_ptr<ZipCommentInfoV1, void(*)(const ZipCommentInfo*)> ZipCommentInfoV1Ptr;

ZipCommentInfo::ZipCommentInfo() NOTHROWS {
}

ZipCommentInfo::~ZipCommentInfo() NOTHROWS {
}

TAKErr ZipCommentInfo::Create(ZipCommentInfoPtr &zipCommentInfoPtr) NOTHROWS { 
    zipCommentInfoPtr = ZipCommentInfoPtr(new (std::nothrow) ZipCommentInfoV1(), deleteImpl<ZipCommentInfo, ZipCommentInfoV1>);
    return TE_Ok;
}

TAKErr ZipCommentInfo::Create(ZipCommentInfoPtr &zipCommentInfoPtr, const char *zipCommentStr) NOTHROWS {
    if (*zipCommentStr != '\0') {
        try {
            json j = json::parse(zipCommentStr);
            const uint32_t schemaVersion = j.at(schemaVersionKey);

            if (schemaVersion == schemaVersion1) {
                ZipCommentInfoV1Ptr zipCommentInfoV1(new (std::nothrow) ZipCommentInfoV1(), deleteImpl<ZipCommentInfo, ZipCommentInfoV1>);
                j.at("envelope").get_to(zipCommentInfoV1->envelope);
                j.at("location").get_to(zipCommentInfoV1->location);
                j.at("altitudeMode").get_to(zipCommentInfoV1->altitudeMode);
                j.at("projection").at("srid").get_to(zipCommentInfoV1->projection.srid);
                j.at("projection").at("wkt").get_to(zipCommentInfoV1->projection.wkt);
                j.at("localFrame").get_to(zipCommentInfoV1->localFrame);
                j.at("displayResolution").at("max").get_to(zipCommentInfoV1->displayResolution.max);
                j.at("displayResolution").at("min").get_to(zipCommentInfoV1->displayResolution.min);
                j.at("resolution").get_to(zipCommentInfoV1->resolution);
                j.at("date").get_to(zipCommentInfoV1->date);
                j.at("metadata").get_to(zipCommentInfoV1->metadata);

                zipCommentInfoPtr = ZipCommentInfoPtr(zipCommentInfoV1.get(), zipCommentInfoV1.get_deleter());
                zipCommentInfoV1.release();

                return TE_Ok;
            } else {
                Logger_log(TELL_Debug, "Unsupported schema version %d", schemaVersion);
            }
        } catch (json::exception e) {
            Logger_log(TELL_Info, "Exception parsing zip comment string as JSON. %s", e.what());
        }
    }

    return TE_Unsupported;
}
