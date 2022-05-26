#include "raster/mobac/CustomMultiLayerMobacMapSource.h"

#ifdef MSVC
#include <msclr/gcroot.h>
#endif

#include "raster/mobac/MobacMapTile.h"

using namespace atakmap::raster::mobac;

namespace
{
    int _getMinZoom(std::vector<std::pair<MobacMapSource *, float>> &layers)
    {
        std::vector<std::pair<MobacMapSource *, float>>::iterator it;

        it = layers.begin();
        int min = (*it).first->getMinZoom();
        for ( ; it != layers.end(); it++) {
            if ((*it).first->getMinZoom() < min)
                min = (*it).first->getMinZoom();
        }
        return min;
    }

    int _getMaxZoom(std::vector<std::pair<MobacMapSource *, float>> &layers)
    {
        std::vector<std::pair<MobacMapSource *, float>>::iterator it;

        it = layers.begin();
        int max = (*it).first->getMinZoom();
        for (; it != layers.end(); it++) {
            if ((*it).first->getMaxZoom() > max)
                max = (*it).first->getMaxZoom();
        }
        return max;
    }
}

CustomMultiLayerMobacMapSource::CustomMultiLayerMobacMapSource(const char *n, std::vector<std::pair<MobacMapSource *, float>> &l, int bg) :
    name(NULL),
    layers(l),
    minZoom(_getMinZoom(l)),
    maxZoom(_getMaxZoom(l)),
    backgroundColor(bg)
{
    char *str = new char[strlen(n) + 1];
    sprintf(str, "%s", n);
    name = str;
}

CustomMultiLayerMobacMapSource::~CustomMultiLayerMobacMapSource()
{
    std::vector<std::pair<MobacMapSource *, float>>::iterator it;
    for (it = layers.begin(); it != layers.end(); it++)
        delete (*it).first;
    layers.clear();
}

void CustomMultiLayerMobacMapSource::clearAuthFailed()
{
    std::vector<std::pair<MobacMapSource *, float>>::iterator it;
    for (it = layers.begin(); it != layers.end(); it++)
        (*it).first->clearAuthFailed();
}

const char *CustomMultiLayerMobacMapSource::getName()
{
    return this->name;
}

int CustomMultiLayerMobacMapSource::getTileSize()
{
    return this->layers[0].first->getTileSize();
}

int CustomMultiLayerMobacMapSource::getMinZoom()
{
    return this->minZoom;
}

int CustomMultiLayerMobacMapSource::getMaxZoom()
{
    return this->maxZoom;
}

const char *CustomMultiLayerMobacMapSource::getTileType()
{
    return "JPG";
}

bool CustomMultiLayerMobacMapSource::getBounds(atakmap::feature::Envelope *bnds)
{
    return false;
}

void CustomMultiLayerMobacMapSource::checkConnectivity()
{
    std::vector<std::pair<MobacMapSource *, float>>::iterator it;
    for (it = layers.begin(); it != layers.end(); it++)
        (*it).first->checkConnectivity();
}

bool CustomMultiLayerMobacMapSource::loadTile(MobacMapTile *tile, int zoom, int x, int y/*, BitmapFactory.Options opts*/) /*throws IOException*/
{
    // XXX - no means to composite via SDK Bitmap API

#ifdef MSVC
    int64_t expiration = INT64_MAX;

    System::Drawing::Bitmap ^composite = gcnew System::Drawing::Bitmap(this->getTileSize(),
                                                                       this->getTileSize());

    System::Drawing::Graphics ^canvas = System::Drawing::Graphics::FromImage(composite);
    if (this->backgroundColor != 0)
        canvas->Clear(System::Drawing::Color::FromArgb(this->backgroundColor));

    System::Drawing::Imaging::ImageAttributes alphaPaint;

    bool gotTile;
    
    std::vector<std::pair<MobacMapSource *, float>>::iterator it;
    for (it = layers.begin(); it != layers.end(); it++) {
        if (zoom < (*it).first->getMinZoom() || zoom >(*it).first->getMaxZoom())
            continue;

        MobacMapTile layerTile;
        try {
            gotTile = (*it).first->loadTile(&layerTile, zoom, x, y/*, opts*/);
            if (gotTile)
                continue;
            if (tile->expiration < expiration)
                expiration = tile->expiration;
            // XXX - float or [0,255] int ???
            System::Drawing::Imaging::ColorMatrix colorMatrix;
            colorMatrix.Matrix33 = (*it).second;
            alphaPaint.SetColorMatrix(%colorMatrix);

            canvas->DrawImage(*static_cast<msclr::gcroot<System::Drawing::Bitmap ^> *>(layerTile.bitmap.opaque),
                              System::Drawing::Rectangle(0, 0, layerTile.bitmap.width, layerTile.bitmap.height),
                              0, 0,
                              layerTile.bitmap.width,
                              layerTile.bitmap.height,
                              System::Drawing::GraphicsUnit::Pixel,
                              %alphaPaint);
        } finally {
            if (gotTile) {
                if (layerTile.bitmap.releaseData)
                    layerTile.bitmap.releaseData;
                if (layerTile.releaseData)
                    layerTile.releaseData(layerTile);
            }
        }
    }
    canvas->Flush();
    delete canvas;

    array<System::Byte> ^data = nullptr;

    System::IO::MemoryStream ^os = gcnew System::IO::MemoryStream();
    try {
        composite->Save(os, System::Drawing::Imaging::ImageFormat::Jpeg);
        data = os->ToArray();
    } finally {
        os->Close();
    }

    //return gcnew MobacMapTile(composite, data, expiration);
    return false;
#else
    return false;
#endif
}

void CustomMultiLayerMobacMapSource::setConfig(MobacMapSource::Config c)
{
    config = c;
}
