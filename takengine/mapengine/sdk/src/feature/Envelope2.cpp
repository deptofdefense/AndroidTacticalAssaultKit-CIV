#include "feature/Envelope2.h"

#include <cmath>

using namespace TAK::Engine::Feature;

Envelope2::Envelope2() NOTHROWS :
    minX(NAN),
    minY(NAN),
    minZ(NAN),
    maxX(NAN),
    maxY(NAN),
    maxZ(NAN)
{}

Envelope2::Envelope2(const double minX_, const double minY_, const double maxX_, const double maxY_) NOTHROWS :
    minX(minX_),
    minY(minY_),
    minZ(0.0),
    maxX(maxX_),
    maxY(maxY_),
    maxZ(0.0)
{}

Envelope2::Envelope2(const double minX_, const double minY_, const double minZ_, const double maxX_, const double maxY_, const double maxZ_) NOTHROWS :
    minX(minX_),
    minY(minY_),
    minZ(minZ_),
    maxX(maxX_),
    maxY(maxY_),
    maxZ(maxZ_)
{}
