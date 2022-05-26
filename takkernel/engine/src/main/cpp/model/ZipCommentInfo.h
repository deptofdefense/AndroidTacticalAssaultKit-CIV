#ifndef TAK_ENGINE_MODEL_ZIPCOMMENTLOCATION_H_INCLUDED
#define TAK_ENGINE_MODEL_ZIPCOMMENTLOCATION_H_INCLUDED

#include "util/Error.h"
#include "port/Platform.h"
#include "port/String.h"
#include "core/GeoPoint2.h"
#include "feature/AltitudeMode.h"
#include "feature/Envelope2.h"
#include "math/Matrix2.h"
#include "util/AttributeSet.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ZipCommentInfo;
            typedef std::unique_ptr<ZipCommentInfo, void(*)(const ZipCommentInfo*)> ZipCommentInfoPtr;

            class ENGINE_API ZipCommentInfo {
            public:
                static TAK::Engine::Util::TAKErr Create(ZipCommentInfoPtr &zipCommentInfoPtr) NOTHROWS;
                static TAK::Engine::Util::TAKErr Create(ZipCommentInfoPtr &zipCommentInfoPtr, const char *zipCommentStr) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr ToString(TAK::Engine::Port::String& out) NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr GetEnvelope(TAK::Engine::Feature::Envelope2 &_envelope) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetLocation(TAK::Engine::Core::GeoPoint2 &_location) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetAltitudeMode(TAK::Engine::Feature::AltitudeMode &altitudeMode) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetProjectionSrid(int &srid) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetProjectionWkt(Port::String &wkt) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetLocalFrame(TAK::Engine::Math::Matrix2 &_localFrame) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetDisplayResolutions(double &max, double &min) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetResolution(double &_resolution) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr GetMetadata(atakmap::util::AttributeSet &metadata) const NOTHROWS = 0;

                virtual void SetEnvelope(const TAK::Engine::Feature::Envelope2 &_envelope) NOTHROWS = 0;
                virtual void SetLocation(const TAK::Engine::Core::GeoPoint2 &_location) NOTHROWS = 0;
                virtual void SetAltitudeMode(const TAK::Engine::Feature::AltitudeMode _altitudeMode) NOTHROWS = 0;
                virtual void SetProjectionSrid(const int srid) NOTHROWS = 0;
                virtual void SetProjectionWkt(const Port::String &wkt) NOTHROWS = 0;
                virtual void SetLocalFrame(const TAK::Engine::Math::Matrix2 &_localFrame) NOTHROWS = 0;
                virtual void SetDisplayResolutions(const double &max, const double &min) NOTHROWS = 0;
                virtual void SetResolution(const double &_resolution) NOTHROWS = 0;
                virtual void SetMetadata(const atakmap::util::AttributeSet &metadata) NOTHROWS = 0;

                /*
                 * Only compares the fields that affect location and layout.
                 */
                virtual bool operator==(const ZipCommentInfo &otherBase) const NOTHROWS = 0;
                virtual bool operator!=(const ZipCommentInfo &otherBase) const NOTHROWS = 0;

            protected:
                ZipCommentInfo() NOTHROWS;
                virtual ~ZipCommentInfo() NOTHROWS;
            };
        }  // namespace Model
    }  // namespace Engine
}  // namespace TAK

#endif
