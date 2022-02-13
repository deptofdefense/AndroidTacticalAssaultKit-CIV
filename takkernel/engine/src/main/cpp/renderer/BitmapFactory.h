
#ifndef ATAKMAP_RENDERER_BITMAPFACTORY_H_INCLUDED
#define ATAKMAP_RENDERER_BITMAPFACTORY_H_INCLUDED

#include <vector>
#include "port/String.h"
#include "renderer/Bitmap.h"
#include "thread/Mutex.h"
#include "util/IO.h"
#include "util/FutureTask.h"

namespace atakmap {
    namespace renderer {
        
        struct BitmapOptions {
            int format;
            int dataType;
            int width;
            int height;
        };
        
        struct BitmapDecodeOptions {
            
            inline BitmapDecodeOptions()
            : emplaceData(false) { }
            
            /**
             * An informal hint as to which image format the input data is. Format hints
             * should be in the form of a mime-type.
             */
            TAK::Engine::Port::String formatHint;
            
            /**
             * An optional cancelation token for decoders that can support it
             */
            util::CancelationToken cancelationToken;
            
            /**
             * If true, the decoder will write the decoded bitmap image to the existing
             * data in the format and size specified within the destination bitmap. The decoder 
             * may return BitmapFactory::Unsupported if this is not supported.
             */
            bool emplaceData;
        };
        
        class BitmapFactory {
        public:
            enum DecodeResult {
                Success,
                Unsupported,
                CorruptInput,
                // XXX-- flesh out more
            };
            
            // throws if unsupported
            static Bitmap create(const BitmapOptions &options);
            
            /**
             *
             */
            static DecodeResult decode(util::DataInput &input, Bitmap &dst, const BitmapDecodeOptions *opts);
        };
        
    }
}

#endif