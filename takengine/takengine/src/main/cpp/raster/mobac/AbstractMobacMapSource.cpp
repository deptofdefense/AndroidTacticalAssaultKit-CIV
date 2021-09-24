#include "raster/mobac/AbstractMobacMapSource.h"

#include "raster/mobac/MobacMapTile.h"
#include "renderer/Bitmap.h"
#include "util/HttpClient.h"

#include "string/String.hh"

#include "renderer/BitmapFactory.h"

#ifdef MSVC
#include <msclr/gcroot.h>
#include "renderer/Bitmap_CLI.h"
#endif

using namespace atakmap::raster::mobac;

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::util;

namespace
{
    void loadTileMmtRelease(MobacMapTile tile);
    void loadTileBmpRelease(Bitmap bitmap);
}

AbstractMobacMapSource::AbstractMobacMapSource(const char *n, int s, int min, int max, const char *t) :
    name(n),
    tileType(t),
    tileSize(s),
    minZoom(min),
    maxZoom(max)
{ }

AbstractMobacMapSource::~AbstractMobacMapSource() {

}

const char *AbstractMobacMapSource::getName()
{
    return this->name;
}

int AbstractMobacMapSource::getTileSize()
{
    return this->tileSize;
}

int AbstractMobacMapSource::getMinZoom()
{
    return this->minZoom;
}

int AbstractMobacMapSource::getMaxZoom()
{
    return this->maxZoom;
}

const char *AbstractMobacMapSource::getTileType()
{
    return this->tileType;
}

bool AbstractMobacMapSource::getBounds(Envelope *bnds)
{
    return false;
}

void AbstractMobacMapSource::setConfig(MobacMapSource::Config c)
{
    config = c;
}

bool AbstractMobacMapSource::load(MobacMapTile *tile, HttpClient *conn, const char *uri/*, BitmapFactory.Options opts*/) /*throws IOException*/
{
    if (!conn->openConnection(uri))
        return false;

    size_t capacity = 256 * 1024;
    std::auto_ptr<uint8_t> buf(new uint8_t[capacity]);
    uint8_t *pBuf = buf.get();

    int numRead;
    do {
        numRead = conn->read(pBuf, capacity - (pBuf - buf.get()));
        pBuf += numRead;
        if (!(capacity - (pBuf - buf.get()))) {
            pBuf = new uint8_t[capacity * 2];
            memcpy(pBuf, buf.get(), capacity);
            buf.reset(pBuf);
            pBuf += capacity;
            capacity *= 2;
        }
    } while (numRead);

    tile->dataLength = (pBuf - buf.get());
    tile->data = buf.get(); // 'get' here, 'release' before returning true
    tile->releaseData = loadTileMmtRelease;
    pBuf = NULL;
#ifdef MSVC
    System::IO::Stream ^stream = nullptr;
    try {
        array<System::Byte> ^data = gcnew array<System::Byte>(tile->dataLength);
        pin_ptr<unsigned char> dataPtr = &data[0];
        memcpy(dataPtr, tile->data, tile->dataLength);
        
        stream = gcnew System::IO::MemoryStream(data);

        atakmap::cpp_cli::renderer::Bitmap::toNative(gcnew System::Drawing::Bitmap(stream), &tile->bitmap);
        tile->bitmap.releaseData = loadTileBmpRelease;

        buf.release();
        return true;
    }
    catch (System::Exception ^e)
    {
        return false;
    }
    finally {
        if (stream != nullptr)
            stream->Close();
    }
#else
    MemoryInput memInput;
    memInput.open(tile->data, tile->dataLength);
    BitmapFactory::DecodeResult result = BitmapFactory::decode(memInput, tile->bitmap, NULL);
    buf.release();
    return result == BitmapFactory::Success;
#endif
}

namespace
{
    void loadTileMmtRelease(MobacMapTile tile)
    {
        if (tile.data)
            delete[] tile.data;
    }

#ifdef MSVC
    void loadTileBmpRelease(Bitmap b)
    {
        delete[] b.data;
        msclr::gcroot<System::Drawing::Bitmap ^> *managedRef = static_cast<msclr::gcroot<System::Drawing::Bitmap ^> *>(b.opaque);
        delete managedRef;
    }
#endif

}
