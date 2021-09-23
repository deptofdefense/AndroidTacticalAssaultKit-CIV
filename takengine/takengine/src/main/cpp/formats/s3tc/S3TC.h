#ifndef TAK_ENGINE_FORMATS_S3TC_S3TC_H_INCLUDED
#define TAK_ENGINE_FORMATS_S3TC_S3TC_H_INCLUDED

#include <vector>
#include "port/Platform.h"
#include "renderer/Bitmap2.h"
#include "util/Error.h"
#include "util/IO2.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace S3TC {
                enum S3TCAlgorithm
                {
                    TECA_DXT1,
                    TECA_DXT5,
                };

				class S3TCCompressData {
				public:


				private:
					S3TCAlgorithm alg;
					std::vector<uint8_t> bytes;
				};

                ENGINE_API Util::TAKErr S3TC_compress(Util::DataOutput2 &value, std::size_t *compressedSize, S3TCAlgorithm *alg, const Renderer::Bitmap2 &bitmap) NOTHROWS;
                ENGINE_API std::size_t S3TC_getCompressedSize(const Renderer::Bitmap2 &bitmap) NOTHROWS;
                ENGINE_API Util::TAKErr S3TC_getCompressedSize(std::size_t *value, const S3TCAlgorithm alg, const std::size_t width, const std::size_t height) NOTHROWS;
                ENGINE_API S3TCAlgorithm S3TC_getDefaultAlgorithm(const Renderer::Bitmap2::Format fmt) NOTHROWS;
            }
        }
    }
}

#endif
