#pragma once
#include "elevation/AbstractElevationData.h"
#include "elevation/ElevationDataSpi.h"

class QMElevationData final : TAK::Engine::Elevation::AbstractElevationData {
public:
    QMElevationData()
        : AbstractElevationData(MODEL_TERRAIN, "QM", 0) {
    }

    
    TAK::Engine::Util::TAKErr getElevation(double* value, const double latitude, const double longitude) NOTHROWS override;

};
