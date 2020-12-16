#include "pch.h"

#include "raster/ImageDatasetDescriptor.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

namespace takenginetests {

	TEST(DatasetDescriptorTests, testImageEncodeDecode) {
        atakmap::raster::ImageDatasetDescriptor imagePreEncode("test",
            "URI",
            "provider",
            "dtype",
            "itype",
            100,
            200,
            6.3,
            1,
            atakmap::core::GeoPoint(1, 2),
            atakmap::core::GeoPoint(3, 4), 
            atakmap::core::GeoPoint(5, 6), 
            atakmap::core::GeoPoint(7, 8), 
            1234,
            false,
            true,
            "wdir",
            std::map<TAK::Engine::Port::String, TAK::Engine::Port::String, TAK::Engine::Port::StringLess>());

        atakmap::raster::DatasetDescriptor::ByteBuffer buffer = imagePreEncode.encode(7);

        atakmap::raster::DatasetDescriptor *desc = atakmap::raster::DatasetDescriptor::decode(buffer);
        atakmap::raster::ImageDatasetDescriptor *decodedImageDesc = dynamic_cast<atakmap::raster::ImageDatasetDescriptor *>(desc);

        ASSERT_NE(nullptr, decodedImageDesc);
        ASSERT_STREQ(imagePreEncode.getName(), desc->getName());
        ASSERT_STREQ(imagePreEncode.getURI(), desc->getURI());
        ASSERT_STREQ(imagePreEncode.getProvider(), desc->getProvider());
        ASSERT_STREQ(imagePreEncode.getDatasetType(), desc->getDatasetType());
        ASSERT_STREQ(imagePreEncode.getImageryType(), decodedImageDesc->getImageryType());
        ASSERT_EQ(imagePreEncode.getWidth(), decodedImageDesc->getWidth());
        ASSERT_EQ(imagePreEncode.getHeight(), decodedImageDesc->getHeight());
        ASSERT_EQ(imagePreEncode.getMinResolution(), decodedImageDesc->getMinResolution());
        ASSERT_EQ(imagePreEncode.getMaxResolution(), decodedImageDesc->getMaxResolution());
        ASSERT_EQ(imagePreEncode.getUpperLeft().latitude, decodedImageDesc->getUpperLeft().latitude);
        ASSERT_EQ(imagePreEncode.getUpperLeft().longitude, decodedImageDesc->getUpperLeft().longitude);
        ASSERT_EQ(imagePreEncode.getUpperRight().latitude, decodedImageDesc->getUpperRight().latitude);
        ASSERT_EQ(imagePreEncode.getUpperRight().longitude, decodedImageDesc->getUpperRight().longitude);
        ASSERT_EQ(imagePreEncode.getLowerLeft().latitude, decodedImageDesc->getLowerLeft().latitude);
        ASSERT_EQ(imagePreEncode.getLowerLeft().longitude, decodedImageDesc->getLowerLeft().longitude);
        ASSERT_EQ(imagePreEncode.getLowerRight().latitude, decodedImageDesc->getLowerRight().latitude);
        ASSERT_EQ(imagePreEncode.getLowerRight().longitude, decodedImageDesc->getLowerRight().longitude);
        ASSERT_EQ(imagePreEncode.getSpatialReferenceID(), decodedImageDesc->getSpatialReferenceID());
        ASSERT_EQ(imagePreEncode.getIsPrecisionImagery(), decodedImageDesc->getIsPrecisionImagery());
        ASSERT_STREQ(imagePreEncode.getWorkingDirectory(), decodedImageDesc->getWorkingDirectory());
    
        ASSERT_STREQ("test", desc->getName());
        ASSERT_STREQ("URI", desc->getURI());
        ASSERT_STREQ("provider", desc->getProvider());
        ASSERT_STREQ("dtype", desc->getDatasetType());
        ASSERT_STREQ("itype", decodedImageDesc->getImageryType());
        ASSERT_EQ((size_t)100, decodedImageDesc->getWidth());
        ASSERT_EQ((size_t)200, decodedImageDesc->getHeight());
        ASSERT_EQ(6.3, decodedImageDesc->getMinResolution());
        ASSERT_EQ(6.3, decodedImageDesc->getMaxResolution());
        ASSERT_EQ(1.0, decodedImageDesc->getUpperLeft().latitude);
        ASSERT_EQ(2.0, decodedImageDesc->getUpperLeft().longitude);
        ASSERT_EQ(3.0, decodedImageDesc->getUpperRight().latitude);
        ASSERT_EQ(4.0, decodedImageDesc->getUpperRight().longitude);
        ASSERT_EQ(7.0, decodedImageDesc->getLowerLeft().latitude);
        ASSERT_EQ(8.0, decodedImageDesc->getLowerLeft().longitude);
        ASSERT_EQ(5.0, decodedImageDesc->getLowerRight().latitude);
        ASSERT_EQ(6.0, decodedImageDesc->getLowerRight().longitude);
        ASSERT_EQ(1234, decodedImageDesc->getSpatialReferenceID());
        ASSERT_EQ(true, decodedImageDesc->getIsPrecisionImagery());
        ASSERT_STREQ("wdir", decodedImageDesc->getWorkingDirectory());
    }
}
