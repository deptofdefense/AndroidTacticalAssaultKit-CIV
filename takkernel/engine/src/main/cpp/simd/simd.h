#ifndef ATAK_SIMD_H
#define ATAK_SIMD_H

#if defined(__arm__) && __ARM_ARCH == 7
    #include "simd/sse2neon.h"
#elif defined(__aarch64__)
    #include "simd/sse2neon.h"
#else
    #include <immintrin.h>
#endif

void *aligned_malloc( size_t size, int align );
void aligned_free( void *mem );

namespace TAK {
    namespace Engine {
        namespace Simd {
            inline __m128 cross_product(__m128 const &a, __m128 const &b) {
                __m128 tmp0 = _mm_shuffle_ps(a, a, _MM_SHUFFLE(0, 1, 3, 2));
                __m128 tmp1 = _mm_shuffle_ps(b, b, _MM_SHUFFLE(0, 2, 1, 3));
                __m128 tmp2 = _mm_shuffle_ps(a, a, _MM_SHUFFLE(0, 2, 1, 3));
                __m128 tmp3 = _mm_shuffle_ps(b, b, _MM_SHUFFLE(0, 1, 3, 2));
                __m128 out = _mm_sub_ps(_mm_mul_ps(tmp0, tmp1), _mm_mul_ps(tmp2, tmp3));
                return _mm_shuffle_ps(out, out, _MM_SHUFFLE(2, 1, 0, 3));
            }

            inline float dot_product(__m128 const &a, __m128 const &b) {
                __m128 mulRes, shufReg, sumsReg;
                mulRes = _mm_mul_ps(a, b);

                shufReg = _mm_movehdup_ps(mulRes);
                sumsReg = _mm_add_ps(mulRes, shufReg);
                shufReg = _mm_movehl_ps(shufReg, sumsReg);
                sumsReg = _mm_add_ss(sumsReg, shufReg);
                return _mm_cvtss_f32(sumsReg);
            }
        }
    }
}

#endif //ATAK_SIMD_H
