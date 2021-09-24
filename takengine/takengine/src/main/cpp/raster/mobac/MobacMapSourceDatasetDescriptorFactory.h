#ifndef ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCEDATASETDESCRIPTORFACTORY_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCEDATASETDESCRIPTORFACTORY_H_INCLUDED

#include "raster/DatasetDescriptor.h"

namespace atakmap {
    namespace raster {
        namespace mobac {
         
            class MobacMapSourceDatasetDescriptorFactory : public raster::DatasetDescriptor::Factory {
            public:
                MobacMapSourceDatasetDescriptorFactory();
                virtual ~MobacMapSourceDatasetDescriptorFactory() throw ();
                
                //
                // Returns the parse version associated with this Factory.  The parse
                // version should be incremented every time the Factory's implementation is
                // modified to produce different results.
                //
                // The version number 0 is a reserved value that should never be returned.
                //
                virtual unsigned short getVersion() const throw();
                
            private:
                
                typedef DatasetDescriptor::CreationCallback CreationCallback;
                typedef DatasetDescriptor::DescriptorSet    DescriptorSet;
                
                DescriptorSet*
                createImpl (const char* filePath,       // Never NULL.
                            const char* workingDir,     // May be NULL.
                            CreationCallback*)          // May be NULL.
                const;
                
                virtual
                bool
                probeFile (const char* file,
                           CreationCallback&)
                const;
            };
        }
    }
}


#endif