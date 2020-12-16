#ifndef ATAKMAP_RASTER_GDAL_GDALLAYERINFO_H_INCLUDED
#define ATAKMAP_RASTER_GDAL_GDALLAYERINFO_H_INCLUDED

#include "raster/DatasetDescriptor.h"
#include "math/Matrix.h"
#include "core/Projection.h"

namespace atakmap {
    namespace raster {
        namespace gdal {

            class GdalLayerInfo : public DatasetDescriptor::Factory
            {
            public:
                static const char *PROVIDER_NAME;

                GdalLayerInfo();
                virtual ~GdalLayerInfo() NOTHROWS;

                //
                // Returns the parse version associated with this Factory.  The parse
                // version should be incremented every time the Factory's implementation is
                // modified to produce different results.
                //
                // The version number 0 is a reserved value that should never be returned.
                //
                virtual
                    unsigned short
                    getVersion()
                    const
                    NOTHROWS override;
                
                virtual
                bool
                probeFile (const char* file,
                           DatasetDescriptor::CreationCallback&)
                const override;

                static core::Projection *createDatasetProjection(const math::Matrix &m) throw (math::NonInvertibleTransformException);
                static std::string getURI(const char *file);

            private:

                typedef DatasetDescriptor::CreationCallback CreationCallback;
                typedef DatasetDescriptor::DescriptorSet    DescriptorSet;

                DescriptorSet*
                    createImpl(const char* filePath,       // Never NULL.
                    const char* workingDir,     // May be NULL.
                    CreationCallback*)          // May be NULL.
                    const override;

                bool test(const char *file, int *count, DatasetDescriptor::CreationCallback &callback) const;
                
                void isSupported(const char* filePath, CreationCallback*) const;

            };

        }
    }
}

#endif
