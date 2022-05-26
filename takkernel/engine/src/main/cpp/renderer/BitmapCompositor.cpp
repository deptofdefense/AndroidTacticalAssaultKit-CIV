
#include "renderer/BitmapCompositor.h"

using namespace atakmap::renderer;

BitmapCompositor::BitmapCompositor() { }

BitmapCompositor::~BitmapCompositor() { }

void BitmapCompositor::composite(float destX, float destY, float destW, float destH, float srcX, float srcY, float srcW, float srcH, const atakmap::renderer::Bitmap &src) {
    this->compositeImpl(destX, destY, destW, destH, srcX, srcY, srcW, srcH, src);
}

void BitmapCompositor::debugText(float destX, float destY, float destW, float destH, const char *text) {
    this->debugTextImpl(destX, destY, destW, destH, text);
}