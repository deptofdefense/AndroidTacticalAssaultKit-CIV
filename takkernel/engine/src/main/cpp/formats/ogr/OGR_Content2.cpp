#include "OGR_Content2.h"


#include <gdal_priv.h>
#include <ogr_feature.h>
#include <ogr_spatialref.h>

#include <iostream>

#include <cmath>
#include <cstddef>
#include <cstring>
#include <iostream>
#include <inttypes.h>
#include <memory>
#include <stack>
#include <stdexcept>

#include "feature/DefaultDriverDefinition.h"
#include "feature/DefaultDriverDefinition2.h"
#include "feature/DefaultSchemaDefinition.h"
#include "feature/OGRDriverDefinition2.h"
#include "feature/OGR_SchemaDefinition.h"
#include "feature/ParseGeometry.h"
#include "util/ConfigOptions.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Memory.h"


#include "feature/FeatureDataSource2.h"
#include "OGRUtils.h"

using namespace atakmap;
using namespace TAK::Engine::Util;

#define MEM_FN( fn )    "TAK::formats::ogr::OGR_Content2::" fn ": "

//=====================================
///  OGR_Content2
namespace {
    typedef std::unique_ptr<OGRSpatialReference, decltype (&OGRSpatialReference::DestroySpatialReference)> OGR_SpatialReferencePtr;
    typedef std::unique_ptr<OGRGeometry, void(*)(OGRGeometry *)> OGRGeometryPtr;

    OGRSpatialReference* createEPSG_4326 ()
    {
        auto* result(new OGRSpatialReference);
        OGRErr err (result->importFromEPSG (4326));

        if (err != OGRERR_NONE)
        {
            std::cerr << "\n"
            MEM_FN ("createEPSG_4326") "importFromEPSG failed.";
        }

        return result;
    }

    std::size_t getDeepGeometryCount (const OGRGeometry* geometry) {
        std::size_t count (0);

        if (geometry && !geometry->IsEmpty ()) {
#ifdef __ANDROID__
            if(OGR_GT_IsSubClassOf(geometry->getGeometryType(), wkbGeometryCollection)) {
                const OGRGeometryCollection *collection (static_cast<const OGRGeometryCollection *>(geometry));
#else
                const auto* collection (dynamic_cast<const OGRGeometryCollection*> (geometry));

                if (collection) {
#endif
                    int elementCount (collection->getNumGeometries ());
                    for (int i (0); i < elementCount; ++i) {
                        count += getDeepGeometryCount (collection->getGeometryRef (i));
                    }
                } else {
                    count = 1;
                }
            }

            return count;
        }


        inline OGRSpatialReference* getEPSG_4326 () {
            static OGR_SpatialReferencePtr result (createEPSG_4326 (), OGRSpatialReference::DestroySpatialReference);
            return result.get ();
        }

        int getSpatialRefID (const OGRSpatialReference& spatialRef)
        {
            int result (0);
            if (!TAK::Engine::Port::String_strcasecmp (spatialRef.GetAuthorityName (nullptr), "EPSG")) {
                const char* value (spatialRef.GetAuthorityCode (nullptr));
                if (value) {
                    std::istringstream strm (value);
                    strm >> result;
                }
            }

            return result;
        }


        OGRCoordinateTransformation* getLayerTransform (OGRLayer& layer) {
            OGRCoordinateTransformation* result (nullptr);

            // Check that the spatial reference ID is WGS84 (4326).  If not, but the
            // layer has a projection, return a coordinate transformation to WGS84.
            OGRSpatialReference* spatialRef (layer.GetSpatialRef ());
            if (spatialRef && getSpatialRefID (*spatialRef) != 4326) {
                result = OGRCreateCoordinateTransformation (spatialRef, getEPSG_4326 ());
            }
            return result;
        }

        bool isClosed(const OGRPolygon &poly) NOTHROWS
{
    const OGRLinearRing *extRing = poly.getExteriorRing();
    if(!extRing)
        return false;
    int numPoints = extRing->getNumPoints();
    if(numPoints < 1)
        return false;
    try {
        return (extRing->getX(0u) == extRing->getX(numPoints-1)) &&
               (extRing->getY(0u) == extRing->getY(numPoints-1));
    } catch(...) {
        return false;
    }
}
        void massage(OGRGeometryPtr &geom)
        {
            switch(geom->getGeometryType())
            {
                case wkbLineString :
                case wkbLineString25D :
                {
#ifdef __ANDROID__
                    const OGRLineString *linestring (static_cast<OGRLineString *>(geom.get()));
#else
                    const auto *linestring = dynamic_cast<const OGRLineString *>(geom.get());
#endif
                    if (linestring != nullptr && linestring->getNumPoints() == 1u) {
                        geom = OGRGeometryPtr(OGRGeometryFactory::createGeometry(wkbPoint), OGRGeometryFactory::destroyGeometry);
                        linestring->getPoint(0, static_cast<OGRPoint *>(geom.get()));
                    }
                }
                break;
                case wkbPolygon :
                case wkbPolygon25D :
                {
#ifdef __ANDROID__
                    const OGRPolygon *polygon (static_cast<OGRPolygon *>(geom.get()));
#else
                    const auto *polygon = dynamic_cast<const OGRPolygon *>(geom.get());
#endif

                    const OGRLinearRing *extRing = polygon->getExteriorRing();
                    if(!extRing)
                        return;
                    const std::size_t pointCount = extRing->getNumPoints();
                    if(pointCount == 1) {
                        geom = OGRGeometryPtr(OGRGeometryFactory::createGeometry(wkbPoint), OGRGeometryFactory::destroyGeometry);
                        extRing->getPoint(0, static_cast<OGRPoint *>(geom.get()));
                    } else if(pointCount < 4 || (pointCount == 4 && !isClosed(*polygon))) {
                        geom = OGRGeometryPtr(OGRGeometryFactory::createGeometry(extRing->getGeometryType()), OGRGeometryFactory::destroyGeometry);
                        static_cast<OGRLineString &>(*geom).addSubLineString(extRing);
                    }
                }
                break;
            }
        }
    }

using namespace TAK::Engine::Formats::OGR;

OGR_Content2::OGR_Content2(const char* filePath, char **openOptions, std::size_t areaThreshold)
    : curResult(NULL, NULL),
      curLegacyResult(),
      curVisible(true),
      filePath(filePath),
      areaThreshold(areaThreshold),
      dataSource(nullptr, nullptr),
      levelOfDetail(0),
      driver(nullptr, nullptr),
      layerIndex(-1),
      state(Feature),
      currentLayer(nullptr),
      layerTransform(nullptr, OGRCoordinateTransformation::DestroyCT),
      currentFeature(nullptr, OGRFeature::DestroyFeature),
      currentGeometry(nullptr, nullptr),
      geometryCount(0) {
    TAK::Engine::Port::String openPath(filePath);
    TAK::Engine::Port::String gdalVsiPrefix;
    TAK::Engine::Util::ConfigOptions_getOption(gdalVsiPrefix, "gdal-vsi-prefix");
    if (gdalVsiPrefix) {
        std::ostringstream strm;
        strm << gdalVsiPrefix;
        strm << openPath;
        openPath = strm.str().c_str();
    } else if (strstr(openPath, ".zip")) {
        std::ostringstream strm;
        strm << "/vsizip";
        if (openPath[0] != '/')
            strm << "/";
        strm << openPath;
        openPath = strm.str().c_str();
    }

    dataSource = GDAL_DatasetPtr(static_cast<GDALDataset*>
                                 (GDALOpenEx(openPath,
                                             GDAL_OF_VECTOR | GDAL_OF_READONLY | GDAL_OF_VERBOSE_ERROR | GDAL_OF_INTERNAL,
                                             nullptr, openOptions, nullptr)),
                                 [](GDALDataset* ds)
                                 {
                                     GDALClose(static_cast<GDALDatasetH>(ds));
                                 });
    if (!dataSource.get()) {
        std::ostringstream err;
        err << MEM_FN("OGR_Content2::OGR_Content2") <<
            "Unable to create DataSource from file: " <<
            filePath;
        throw std::invalid_argument(err.str());
    }

    layerCount = dataSource->GetLayerCount();

    if (layerCount < 1) {
        std::ostringstream err;
        err << MEM_FN("OGR_Content2::OGR_Content2") <<
            "No Layers found in DataSource from file: " <<
            filePath;
        throw std::invalid_argument(err.str());
    }

    const char* driverName(dataSource->GetDriver()->GetDescription());

    TAK::Engine::Feature::OGRDriverDefinition2_create(driver, filePath, driverName);

    if (!driver.get()) {
        using namespace TAK::Engine::Feature;
        using namespace TAK::Engine::Util;

        driver = OGRDriverDefinition2Ptr(new DefaultDriverDefinition2(driverName,
                                                                      driverName,
                                                                      1),
                                         Memory_deleter_const<OGRDriverDefinition2, DefaultDriverDefinition2>);
    }

    if (!preprocessDataset()) {
        std::cerr << "\nNo valid features found in file: " << filePath;
        layerIndex = layerCount; // Nothing to see here, move along.
    }
}

OGR_Content2::~OGR_Content2() NOTHROWS
{
}

TAKErr TAK::Engine::Formats::OGR::OGR_Content2_create(Feature::FeatureDataSource2::ContentPtr &content, const char *filePath, char **openOptions, std::size_t sizeThreshold) NOTHROWS
{
    try {
        content = Feature::FeatureDataSource2::ContentPtr(new OGR_Content2(filePath, openOptions, sizeThreshold), Memory_deleter_const<Feature::FeatureDataSource2::Content, OGR_Content2>);
        return TE_Ok;
    } catch (std::invalid_argument &) {
        return TE_Err;
    }
}


const char *OGR_Content2::getType() const NOTHROWS {
    return this->driver->getType();
}


const char *OGR_Content2::getProvider() const NOTHROWS {
#ifdef __ANDROID__
        return "ogr";
#else
        return "OGR";
#endif
}


TAKErr OGR_Content2::moveToNextFeature() NOTHROWS
{
    // Reset feature definitions
    curResult.reset();
    curLegacyResult.reset();
    
    // Load up next feature's data
    bool result = moveToNextFeatureImpl();

    if (result) {
        // Create feature definitions for the current feature
        TAKErr code = createLegacyResult(curLegacyResult, &curVisible);
        if (code == TE_Ok)
            code = TAK::Engine::Feature::LegacyAdapters_adapt(curResult, *curLegacyResult);

        return code;
    } else {
        return TAK::Engine::Util::TE_Done;
    }
}

bool OGR_Content2::moveToNextFeatureImpl() NOTHROWS
{
    bool result (false);
    bool terminate (false);             // Terminate state machine.

    do {
        switch (state)
        {
        case Feature:
            bool skipFeature;
            do {
                if (currentLayer)
                    currentFeature.reset (currentLayer->GetNextFeature ());
                else
                    currentFeature.reset();
                if (!currentFeature.get())
                    break;
                skipFeature = true;
                driver->skipFeature(&skipFeature, *currentFeature);
            } while (skipFeature);
            if (currentFeature.get ()) {
                setCurrentFeatureName ();
                currentGeometry = OGRGeometryPtr(currentFeature->GetGeometryRef (), Memory_leaker<OGRGeometry>);
                geometryCount = 0;

                if (currentGeometry.get()) {
                    // if the feature does not have any non-empty
                    // geometries, skip
                    if (getDeepGeometryCount(currentGeometry.get()) < 1) {
                        //Log.w(TAG, "Skipping empty geometry " + this->featureName);
                        continue;
                    }

#ifdef __ANDROID__
                    if(OGR_GT_IsSubClassOf(currentGeometry->getGeometryType(), wkbGeometryCollection)) {
                        OGRGeometryCollection *collection (static_cast<OGRGeometryCollection *>(currentGeometry.get()));
#else
                    auto* collection (dynamic_cast<OGRGeometryCollection*> (currentGeometry.get()));

                    if (collection) {
#endif
                        geoStack.push (GeoNode (collection));
                        currentGeometry.reset();
                        state = Geometry;
                    } else {
                        if (layerTransform.get ()) {
                            currentGeometry->transform (layerTransform.get ());
                        }
                        terminate = result = true;
                    }
                }
            } else {
                // Exhausted this layer's Features.
                return false;
            }
            currentFeatureAttributes.reset ();
            break;
        case Geometry:
            currentGeometry = OGRGeometryPtr(geoStack.top ().getNextChild (), Memory_leaker<OGRGeometry>);

            if (currentGeometry.get()) {
#ifdef __ANDROID__
                if(OGR_GT_IsSubClassOf(currentGeometry->getGeometryType(), wkbGeometryCollection)) {
                    OGRGeometryCollection *collection (static_cast<OGRGeometryCollection *>(currentGeometry.get()));
#else
                auto* collection (dynamic_cast<OGRGeometryCollection*> (currentGeometry.get()));
                if (collection) {
#endif

                    geoStack.push (GeoNode (collection));
                    currentGeometry = nullptr;
                } else {
                    massage(currentGeometry);
                    if (currentGeometry->IsEmpty())
                        continue;
                    if (layerTransform.get ()) {
                        currentGeometry->transform (layerTransform.get ());
                    }
                    ++geometryCount;
                    terminate = result = true;
                }
            } else {
                // Exhausted GeometryCollection.
                geoStack.pop ();
                if (geoStack.empty ()) {
                        // Exhausted feature's Geometries.
                    state = Feature;
                }
            }
            break;
        }
    } while (!terminate);

    return result;
}

TAKErr OGR_Content2::moveToNextFeatureSet() NOTHROWS
{
    do {
        if (currentLayer)
            currentLayer = nullptr;
        layerIndex++;
        if (layerIndex >= layerCount)
            break; // no more layers
        currentLayer = dataSource->GetLayer(layerIndex);
        if (currentLayer) {
            bool skipLayer = false;
            driver->skipLayer(&skipLayer, *currentLayer);
            if (skipLayer)
                continue;
            // we have the next layer and it should not be skipped, break
            break;
        }
    } while (true);

    if (currentLayer) {
        currentLayerName = currentLayer->GetName();
        if (this->driver->layerNameIsPath()) {
            currentFeatureSetName = currentLayerName;
        } else if (this->layerCount > 1) {
            TAK::Engine::Port::String filename;
            TAK::Engine::Util::IO_getName(filename, this->filePath);

            std::ostringstream strm;
            strm << filename << "/" << this->currentLayerName;
            currentFeatureSetName = strm.str().c_str();
        } else {
            TAK::Engine::Util::IO_getName(currentFeatureSetName, this->filePath);
        }

        currentLayerDef = currentLayer->GetLayerDefn();

        const feature::OGR_SchemaDefinition* schema (feature::OGR_SchemaDefinition::getSchema(filePath, *currentLayerDef));
        if (!schema)
        {
            schema = feature::DefaultSchemaDefinition::get();
        }
        layerNameFields = schema->getNameFields(filePath, *currentLayerDef);
        layerTransform.reset(getLayerTransform(*currentLayer));
        currentLayer->ResetReading();
        state = Feature;
    }
    return (layerIndex < layerCount) ? TAK::Engine::Util::TE_Ok : TAK::Engine::Util::TE_Done;
}


TAKErr OGR_Content2::get(TAK::Engine::Feature::FeatureDefinition2 **feature) const NOTHROWS {
    if (!curResult.get())
        return TE_IllegalState;

    *feature = curResult.get();
    return TE_Ok;
}

TAKErr OGR_Content2::getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS {
    name = currentFeatureSetName;
    return TE_Ok;
}

TAKErr OGR_Content2::getFeatureSetVisible(bool *visible) const NOTHROWS {
    *visible = true;
    return TE_Ok;
}

TAKErr OGR_Content2::getMinResolution(double *value) const NOTHROWS
{
    *value = levelOfDetail < 32 ? 156543.034 / (1 << levelOfDetail) : 0.0;
    return TE_Ok;
}

TAKErr OGR_Content2::getMaxResolution(double *value) const NOTHROWS
{
    *value = 0.0;
    return TE_Ok;
}

TAKErr OGR_Content2::getVisible(bool *visible) const NOTHROWS
{
    *visible = curVisible;
    return TE_Ok;
}

const util::AttributeSet& TAK::Engine::Formats::OGR::OGR_Content2::getCurrentFeatureAttributes () const
{
    if (!currentFeatureAttributes.get ()) {
       int fieldCount (currentLayerDef->GetFieldCount ());
        std::unique_ptr<util::AttributeSet> attributes (new util::AttributeSet);

        for (int i (0); i < fieldCount; ++i) {
            OGRFieldDefn* fieldDef (currentLayerDef->GetFieldDefn (i));
            if (fieldDef && currentFeature->IsFieldSet (i)) {
                const char* attrName (fieldDef->GetNameRef ());
                switch (fieldDef->GetType ())
                {
                case OFTDate:
                case OFTTime:
                case OFTDateTime:
                case OFTString:
                    attributes->setString (attrName, currentFeature->GetFieldAsString (i));
                    break;
                case OFTInteger:
                    attributes->setInt (attrName, currentFeature->GetFieldAsInteger (i));
                    break;
                case OFTInteger64:
                    attributes->setLong (attrName, currentFeature->GetFieldAsInteger64 (i));
                    break;
                case OFTReal:
                    attributes->setDouble (attrName, currentFeature->GetFieldAsDouble (i));
                    break;
                case OFTBinary:
                    {
                        int length (0);
                        unsigned char* start (currentFeature->GetFieldAsBinary (i, &length));
                        attributes->setBlob (attrName, std::make_pair (start, start + length));
                    }
                    break;
                case OFTStringList:
                    {
#if 0
                        const char* const* head (currentFeature->GetFieldAsStringList (i));
                        const char* const* tail (head);
#else
                        const char* const *head = currentFeature->GetFieldAsStringList(i);
                        const char* const *tail = head;
#endif
                        while (*tail) {
                            ++tail;
                        }
                        attributes->setStringArray (attrName, std::make_pair (head, tail));
                    }
                    break;
                case OFTIntegerList:
                    {
                        int length (0);
                        const int* start (currentFeature->GetFieldAsIntegerList (i, &length));
                        attributes->setIntArray (attrName, std::make_pair (start, start + length));
                    }
                    break;
                case OFTInteger64List:
                    {
                        int length (0);
                        const int64_t* start
// XXX--
#if defined(__APPLE__) || (defined(__ANDROID__) && defined(__aarch64__)) || defined(__LINUX__)
                        (reinterpret_cast<const int64_t *>(currentFeature->GetFieldAsInteger64List (i, &length)));
#else
                        (currentFeature->GetFieldAsInteger64List (i, &length));
#endif
                        attributes->setLongArray (attrName, std::make_pair (start, start + length));
                    }
                    break;
                case OFTRealList:
                    {
                        int length (0);
                        const double* start (currentFeature->GetFieldAsDoubleList (i, &length));
                        attributes->setDoubleArray (attrName, std::make_pair (start, start + length));
                    }
                    break;
                default:
                    std::cerr << "\nUnexpected OGRFieldType: " << fieldDef->GetType ();
                }
            }
        }

#ifdef __ANDROID__
        if(attributes->containsAttribute("description") && attributes->getAttributeType("description") == atakmap::util::AttributeSet::STRING) {
            attributes->setString("html", attributes->getString("description"));
        }
#endif

        currentFeatureAttributes = std::move (attributes); // Transfer ownership.
    }

    return *currentFeatureAttributes;
}
        
std::size_t OGR_Content2::preprocessDataset ()
{
    std::size_t pointCount (0);
    std::size_t featureCount (0);
    OGREnvelope datasetMBR;
    bool haveDatasetMBR (false);

    // Iterate over the layers in the dataset.
    for (int i (0); i < layerCount; ++i) {
        OGRLayer* layer (dataSource->GetLayer (i));

        bool skipLayer = !layer;
        if (!skipLayer) {
            // we've got a layer to process, see if it should be skipped
            driver->skipLayer(&skipLayer, *layer);
        }
        if (!skipLayer)
        {
            OGR_CoordinateTransformationPtr transform (getLayerTransform (*layer), OGRCoordinateTransformation::DestroyCT);

            layer->ResetReading ();

            OGREnvelope layerMBR;
            bool haveLayerMBR (false);

            // Scan the layer to determine its MBR.
            for (OGR_FeaturePtr feature (layer->GetNextFeature (), OGRFeature::DestroyFeature); feature.get (); feature.reset (layer->GetNextFeature ())) {
                bool skipFeature = false;
                driver->skipFeature(&skipFeature, *feature);
                if (!skipFeature) {
                    OGRGeometry* geometry (feature->GetGeometryRef ());
                    if (geometry && !geometry->IsEmpty ()) {
                        OGREnvelope envelope;

                        geometry->getEnvelope (&envelope);
                        if (!haveLayerMBR) {
                            layerMBR = envelope;
                            haveLayerMBR = true;
                        } else {
                            layerMBR.MinX = std::min (layerMBR.MinX, envelope.MinX);
                            layerMBR.MinY = std::min (layerMBR.MinY, envelope.MinY);
                            layerMBR.MaxX = std::max (layerMBR.MaxX, envelope.MaxX);
                            layerMBR.MaxY = std::max (layerMBR.MaxY, envelope.MaxY);
                        }
                        switch (geometry->getGeometryType ())
                        {
                        case wkbPoint:
                        case wkbPoint25D:     ++pointCount;   break;
                        default:                              break;
                        }
                        featureCount += getDeepGeometryCount (geometry);
                    }
                }
            }

            if (haveLayerMBR) {
                if (transform.get ()) {
                    transform->Transform (1, &layerMBR.MinX, &layerMBR.MinY);
                    transform->Transform (1, &layerMBR.MaxX, &layerMBR.MaxY);

                    // The pre-transform projection may not have been geodetic,
                    // so the post-transform min values may not be less than the
                    // max values.
                    if (!haveDatasetMBR) {
                        datasetMBR.MinX = std::min (layerMBR.MinX, layerMBR.MaxX);
                        datasetMBR.MinY = std::min (layerMBR.MinY, layerMBR.MaxY);
                        datasetMBR.MaxX = std::max (layerMBR.MinX, layerMBR.MaxX);
                        datasetMBR.MaxY = std::max (layerMBR.MinY, layerMBR.MaxY);
                        haveDatasetMBR = true;
                    } else {
                        datasetMBR.MinX = std::min (datasetMBR.MinX, std::min (layerMBR.MinX, layerMBR.MaxX));
                        datasetMBR.MinY = std::min (datasetMBR.MinY, std::min (layerMBR.MinY, layerMBR.MaxY));
                        datasetMBR.MaxX = std::max (datasetMBR.MaxX, std::max (layerMBR.MinX, layerMBR.MaxX));
                        datasetMBR.MaxY = std::max (datasetMBR.MaxY, std::max (layerMBR.MinY, layerMBR.MaxY));
                    }
                } else if (!haveDatasetMBR) {
                    datasetMBR = layerMBR;
                    haveDatasetMBR = true;
                } else {
                    datasetMBR.MinX = std::min (datasetMBR.MinX, layerMBR.MinX);
                    datasetMBR.MinY = std::min (datasetMBR.MinY, layerMBR.MinY);
                    datasetMBR.MaxX = std::max (datasetMBR.MaxX, layerMBR.MaxX);
                    datasetMBR.MaxY = std::max (datasetMBR.MaxY, layerMBR.MaxY);
                }
            }
        }
    }

    // Don't calculate level of detail for no features or just a single point.
    if (featureCount > 1 || featureCount > pointCount) {
              levelOfDetail = OGRUtils_ComputeLevelOfDetail(areaThreshold, datasetMBR);
        // Maximum feature density is 5000.
        if (featureCount > 5000) {
            // Apply a fudge factor to the level of detail.
            static const double log_2 (std::log (2));
            levelOfDetail += 2 + static_cast<std::size_t>(std::ceil (std::log (featureCount / 5000.0) / log_2));
        }
    }

    return featureCount;
}


void OGR_Content2::setCurrentFeatureName ()
{
    currentFeatureName = static_cast<const char *>(nullptr);
    if (currentFeature->GetFieldCount ()) {
        StringVector::const_iterator end (layerNameFields.end ());
        for (StringVector::const_iterator iter (layerNameFields.begin ()); !currentFeatureName && iter != end; ++iter) {
            // GetFieldIndex returns -1 for an unknown field.
            // GetFieldDefn returns NULL for an invalid index.
            int index (currentLayerDef->GetFieldIndex (*iter));
            if (index >= 0) {
                OGRFieldDefn* fieldDef (currentLayerDef->GetFieldDefn (index));
                if (fieldDef && fieldDef->GetType () == OFTString) {
                    currentFeatureName = currentFeature->GetFieldAsString(index);
                    Port::String trimmed;
                    if (!TAK::Engine::Port::String_trim(trimmed, currentFeatureName)) {
                        currentFeatureName = static_cast<const char*>(nullptr);
                    } else if (std::strstr(currentFeatureName, "\n")) {
                        std::string name(currentFeatureName);
                        currentFeatureName = name.substr(0, name.find('\n') - 1).c_str();
                    }
                }
            }
        }
    }

    if (!currentFeatureName) {
        std::string layerName(currentLayer->GetName());
        std::size_t pathSeparatorIdx = layerName.find_last_of('/');
        while (pathSeparatorIdx && pathSeparatorIdx != std::string::npos) {
            // if the path separator character was escaped, find the previous
            if (pathSeparatorIdx && layerName[pathSeparatorIdx - 1] == '\\')
                pathSeparatorIdx = layerName.find_last_of('/', pathSeparatorIdx - 1);
            else
                break;
        }

        // if a path separator character is present, take the substring
        // starting with the character following the separator
        if (pathSeparatorIdx != std::string::npos)
            layerName = layerName.substr(pathSeparatorIdx + 1);

        std::ostringstream strm;
        strm << layerName << "." << currentFeature->GetFID ();
        currentFeatureName = strm.str().c_str ();
    }
}

TAKErr OGR_Content2::createLegacyResult(std::unique_ptr<feature::FeatureDataSource::FeatureDefinition> &legacyResultOut, bool *vis) const {
    typedef feature::FeatureDataSource::FeatureDefinition FeatureDefinition;
    std::unique_ptr<FeatureDefinition> legacyResult;
    bool visible = true;

    legacyResult.reset(new FeatureDefinition(currentFeatureName, getCurrentFeatureAttributes()));

    if (driver->setGeometry(legacyResult, *currentFeature, *currentGeometry) != TE_Ok) {
        Logger_log(LogLevel::TELL_Error, MEM_FN("OGR_Content2::get") "Unsupported geometry encoding");
        return TE_Err;
    }

    for (int i = 0; i < currentFeature->GetFieldCount(); ++i) {
        OGRFieldDefn* ogrfdn = currentFeature->GetFieldDefnRef(i);
        const char* name = ogrfdn->GetNameRef();
        const OGRFieldType ogrFieldtype = ogrfdn->GetType();
        if (!TAK::Engine::Port::String_strcasecmp(name, "gx:altitudeMode"))
            name = "altitudeMode";
        if (!TAK::Engine::Port::String_strcasecmp(name, "altitudeMode")) {
            const char* value = currentFeature->GetFieldAsString(i);
            int altitudeMode = 0;
            if (!TAK::Engine::Port::String_strcasecmp(value, "clampToGround"))
                altitudeMode = 0;
            else if (!TAK::Engine::Port::String_strcasecmp(value, "clampToSeaFloor"))
                altitudeMode = 0;
            else if (!TAK::Engine::Port::String_strcasecmp(value, "relativeToGround"))
                altitudeMode = 1;
            else if (!TAK::Engine::Port::String_strcasecmp(value, "relativeToSeaFloor"))
                altitudeMode = 1;
            else if (!TAK::Engine::Port::String_strcasecmp(value, "absolute"))
                altitudeMode = 2;
            legacyResult->setAltitudeMode(altitudeMode);
        } else if (!TAK::Engine::Port::String_strcasecmp(name, "extrude")) {
            legacyResult->setExtrude(currentFeature->GetFieldAsInteger(i) ? -1.0 : 0.0);
        } else if (!TAK::Engine::Port::String_strcasecmp(name, "visibility")) {
            visible = currentFeature->GetFieldAsInteger(i) != 0;
        }
    }

    TAK::Engine::Port::String styleString;
    driver->getStyle(styleString, *currentFeature, *currentGeometry);

    if (styleString && styleString[0] == '@') {
        OGRStyleTable* styleTable = dataSource->GetStyleTable();
        if (styleTable) {
            styleTable = currentLayer->GetStyleTable();
        }

        if (styleTable) {
            const char* tableStyle = styleTable->Find(styleString);
            tableStyle = nullptr;
        }

        styleString = static_cast<const char*>(nullptr);
    }
    legacyResult->setStyle(styleString);

    legacyResultOut = std::move(legacyResult);
    if (vis)
        *vis = visible;
    return TE_Ok;
}




            


