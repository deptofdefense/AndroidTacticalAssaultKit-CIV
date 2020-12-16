#include "raster/mobac/MobacMapSource.h"

using namespace atakmap::raster::mobac;

MobacMapSource::Config::Config() :
    dnsLookupTimeout(1L)
{}

MobacMapSource::~MobacMapSource() { }