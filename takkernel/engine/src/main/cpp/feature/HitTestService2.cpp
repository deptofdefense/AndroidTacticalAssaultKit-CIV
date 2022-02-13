#include "feature/HitTestService2.h"

using namespace TAK::Engine::Feature;

const char * const HitTestService2::SERVICE_TYPE = "Feature.HitTestService2";

HitTestService2::~HitTestService2() NOTHROWS
{}

const char *HitTestService2::getType() const NOTHROWS
{
    return SERVICE_TYPE;
}
