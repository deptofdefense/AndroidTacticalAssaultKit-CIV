#include "feature/FeatureHitTestControl.h"

using namespace TAK::Engine::Feature;

FeatureHitTestControl::~FeatureHitTestControl() NOTHROWS
{}

const char *TAK::Engine::Feature::FeatureHitTestControl_getType() NOTHROWS {
    return "FeatureHitTestControl";
}