#include "feature/KmlFeatureDataSource.h"

#include <queue>
#include <regex>
#include <set>

#include <kml/engine.h>
#include <kml/dom.h>

#include "core/AtakMapView.h"

#include "util/Distance.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Logging.h"
#include "util/Memory.h"

#include "feature/FeatureDefinition2.h"
#include "feature/GeometryCollection.h"
#include "feature/KMLDriverDefinition2.h"
#include "feature/LineString.h"
#include "feature/OGR_FeatureDataSource.h"
#include "feature/ParseGeometry.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/Style.h"

#include "ogr_core.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

#define TAG "KmlFeatureDataSource"

#define DefaultPointStyle "SYMBOL(id:http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png,c:#FFFFFFFF)"
#define DefaultLineColor 0xFF000000
#define DefaultSymbolColor 0xFFFFFFFF
#define STYLE_MAP_PAIR_NORMAL_TYPE 0
#define STYLE_MAP_PAIR_HIGHLIGHT_TYPE 1
#define STYLE_HEADER_SYMBOL_POS 0
const char* STYLE_HEADER_SYMBOL = "#";

const char* URL = "http";
const char* URL_CAP = "HTTP";

const double MAX_RESOLUTION = 0.0;

namespace
{

    class KmlDefinition : public FeatureDefinition2
    {
    public:
        KmlDefinition(const char* name, const atakmap::util::AttributeSet& value) NOTHROWS;
        ~KmlDefinition() NOTHROWS;

    public:
        virtual void setGeometry(GeometryPtr &&, const AltitudeMode, const double extrude) NOTHROWS;
        virtual void setStyle(StylePtr_const &&) NOTHROWS;
        virtual void setStyle(const char* styleString) NOTHROWS;

        TAK::Engine::Util::TAKErr getRawGeometry(RawData *value) NOTHROWS override;
        GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        TAK::Engine::Util::TAKErr getName(const char **value) NOTHROWS override;
        StyleEncoding getStyleCoding() NOTHROWS override;
        TAK::Engine::Util::TAKErr getRawStyle(RawData *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        TAK::Engine::Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
    private:

        void freeGeometry();
        void freeStyle();

        TAK::Engine::Port::String m_featureName;
        StyleEncoding m_styleEncoding;
        GeometryPtr m_geometry;            // Feature's geometry
        AltitudeMode m_altitudeMode;
        double m_extrude;
        // Feature's styling, per styling.
        TAK::Engine::Port::String m_ogrStyle;
        StylePtr_const m_style;

        //const void* bufferTail;
        atakmap::util::AttributeSet m_attributes;
        FeaturePtr_const m_feature;
    }; // KmlDefinition


    class KmlContent : public FeatureDataSource2::Content
    {
    public:
        KmlContent(const char* filePath) NOTHROWS;
        ~KmlContent() NOTHROWS override;
    public:
        const char *getType() const NOTHROWS override;
        const char *getProvider() const NOTHROWS override;
        TAK::Engine::Util::TAKErr moveToNextFeature() NOTHROWS override;
        TAK::Engine::Util::TAKErr moveToNextFeatureSet() NOTHROWS override;
        TAK::Engine::Util::TAKErr get(FeatureDefinition2 **feature) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getFeatureSetName(TAK::Engine::Port::String& name) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getFeatureSetVisible(bool* visible) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getMinResolution(double *value) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getMaxResolution(double *value) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getVisible(bool* visible) const NOTHROWS override;

        bool isKml() { return m_isKml; };
        bool isKmz() { return m_isKmz; };

    private:

        bool  openKmz(const char *pszFilename);
        bool  openKml(const char *pszFilename);

        kmldom::ContainerPtr GetContainerFromRoot(kmldom::KmlFactory *poKmlFactory, kmldom::ElementPtr poKmlRoot);

        atakmap::feature::Geometry* getGeometry(kmldom::GeometryPtr kmlGeometry, AltitudeMode& altitudeMode, double& extrude) const;
        atakmap::feature::Geometry* getPoint(kmldom::PointPtr kmlPoint, AltitudeMode& altitudeMode, double& extrude) const;
        atakmap::feature::Geometry* getLineString(kmldom::LineStringPtr kmlLineString, AltitudeMode& altitudeMode) const;
        atakmap::feature::Geometry* getLinearRing(kmldom::LinearRingPtr kmlLinearRing, AltitudeMode& altitudeMode) const;
        atakmap::feature::Geometry* getLinearRing(kmldom::GxLatLonQuadPtr kmlGxLatLonQuad, AltitudeMode& altitudeMode) const;
        atakmap::feature::Geometry* getPolygon(kmldom::PolygonPtr kmlPolygon, AltitudeMode& altitudeMode) const;
        atakmap::feature::Geometry* getGxTrack(kmldom::GxTrackPtr kmlGxTrack, AltitudeMode& altitudeMode) const;

        atakmap::feature::Style* getIconStyle(kmldom::IconStylePtr kmlIconStyle) const;
        atakmap::feature::Style* getLineStyle(kmldom::LineStylePtr kmlLineStyle) const;
        atakmap::feature::Style* getFillStyle(kmldom::PolyStylePtr kmlPolyStyle) const;

        std::string getKmlRootName() const;

        bool isValidFeature(kmldom::FeaturePtr feature, int& pointFeatureCount, OGREnvelope& datasetMBR) const;
        void calculateLevelofDetail();
        void populateFeatureStorage(kmldom::FeaturePtr root);
        void populateStyleStorage(kmldom::DocumentPtr feature);
        void populateStyleStorage(kmldom::StyleSelectorPtr styleSelector);
		atakmap::feature::Style* getStyle(kmldom::StyleSelectorPtr styleSelector);
        void populateStylesFromSelector(kmldom::StyleSelectorPtr styleSelector, std::vector<atakmap::feature::Style*>& styleVector);

        /***** features *****/
        double                    m_defaultMinResolution;
        std::map<std::string, double> m_featureSetMinResolution;
        TAK::Engine::Port::String			  m_filePath;
        TAK::Engine::Port::String			  m_rootNodeName;
        TAK::Engine::Port::String			  m_featureSetName;
        bool					  m_visible;
        unsigned int              m_featureNameId;
        kmldom::FeaturePtr        m_currentFeature;
        kmldom::FeaturePtr        m_currentFeatureSet;
        bool                      m_firstFeature;
        bool                      m_firstFeatureSet;
        std::set<kmldom::FeaturePtr>::iterator m_currentFeatureSetIndex;
        std::set<kmldom::FeaturePtr> m_featureSets;
        typedef std::pair<kmldom::FeaturePtr, kmldom::FeaturePtr> KmlFeature_Pair;
        std::multimap<kmldom::FeaturePtr, kmldom::FeaturePtr>::iterator m_currentFeatureIndex;
        std::multimap<kmldom::FeaturePtr, kmldom::FeaturePtr> m_features;
        typedef std::pair<std::string, StylePtr_const> Style_Pair;
        std::map<std::string, StylePtr_const> m_styles;

        std::unique_ptr<KMLDriverDefinition2> driver;

        struct StyleMapPair
        {
            StyleMapPair(int key, const char* styleUrl)
                : m_key(key), m_styleUrl(styleUrl) {};

            int m_key;
            std::string m_styleUrl;
        };
        typedef std::pair<std::string, StyleMapPair> StyleMap_Pair;
        std::multimap<std::string, StyleMapPair> m_styleMaps;

        /***** for kml files *****/
        bool                      m_isKml;
        kmldom::ContainerPtr      m_poKmlDSContainer;

        /***** for kmz files *****/
        bool                      m_isKmz;

        /***** the kml factory *****/
        kmldom::KmlFactory        *m_poKmlFactory;

        std::unique_ptr<KmlDefinition> m_featureDefinition;
    };
}

KmlFeatureDataSource::KmlFeatureDataSource() NOTHROWS
{}

TAKErr KmlFeatureDataSource::parse(FeatureDataSource2::ContentPtr &content, const char *file) NOTHROWS
{
    if (!file)
    {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "Received NULL filePath");
        return TE_InvalidArg;
    }

    content = FeatureDataSource2::ContentPtr(new KmlContent(file), Memory_deleter_const<FeatureDataSource2::Content, KmlContent>);

    auto* kmlContent = dynamic_cast<KmlContent*>(content.get());
    return (((kmlContent != nullptr) && (kmlContent->isKml() || kmlContent->isKmz())) ? TE_Ok : TE_Err);
}

const char *KmlFeatureDataSource::getName() const NOTHROWS
{
    return "KML";
}

int KmlFeatureDataSource::parseVersion() const NOTHROWS
{
    return 2;
}

namespace
{

    KmlContent::KmlContent(const char* filePath) NOTHROWS
        : m_featureNameId(0), m_filePath(filePath), m_firstFeature(true), m_firstFeatureSet(true), m_isKml(false), m_isKmz(false),
        m_defaultMinResolution(0)
    {
        bool success = false;

        auto* newDriver = new KMLDriverDefinition2(filePath);
        std::unique_ptr<KMLDriverDefinition2> uniqueDriver(newDriver);
        driver = std::move(uniqueDriver);

        /***** kml *****/
        if (strstr(filePath, "kml"))
        {
            success = openKml(filePath);
        }
        /***** kmz *****/
        else if (strstr(filePath, "kmz"))
        {
            success = openKmz(filePath);
        }
    }

    KmlContent::~KmlContent() NOTHROWS
    {}

    void KmlContent::populateFeatureStorage(kmldom::FeaturePtr root)
    {
        std::queue<kmldom::FeaturePtr> Q;
        OGREnvelope datasetMBR;
        int pointFeatureCount = 0;

        std::string rootName = root->get_name().c_str();
        if (rootName.empty())
        {
            // use filename if root node has no name
            rootName = getKmlRootName();
            root->set_name(rootName);
        }
        m_rootNodeName = rootName.c_str();

        Q.push(root);
        while (!Q.empty())
        {
            kmldom::FeaturePtr parent = Q.front();

            // check if this feature defines any styles
            if (parent->IsA(kmldom::Type_Document))
            {
                kmldom::DocumentPtr document = AsDocument(parent);
                populateStyleStorage(document);
            }
            else if (parent->has_styleselector())
            {
                kmldom::StyleSelectorPtr styleSelector = parent->get_styleselector();
                populateStyleStorage(styleSelector);
            }

            m_featureSets.insert(parent);
            Q.pop();

            if (parent->IsA(kmldom::Type_Container))
            {
                const std::string parentName = parent->get_name();
                kmldom::ContainerPtr container = AsContainer(parent);
                const size_t numFts = container->get_feature_array_size();
                for (size_t n = 0; n < numFts; n++)
                {
                    kmldom::FeaturePtr child = container->get_feature_array_at(n);
                    if (child->IsA(kmldom::Type_Container))
                    {
                        // update the name of the FeatureSet object to match
                        // the naming convention expected by WinTAK
                        std::ostringstream strm;
                        strm << parentName << "/";
                        for (size_t i = 0; i < child->get_name().length(); i++) {
                            if (child->get_name()[i] == '/')
                                strm << '\\';
                            strm << child->get_name()[i];
                        }
                        child->set_name(strm.str().c_str());
                        Q.push(child);
                    }
                    else if (isValidFeature(child, pointFeatureCount, datasetMBR))
                    {
                        m_features.insert(KmlFeature_Pair(parent, child));
                    }
                }
            }
        }
    }

    inline bool isPointValid(kmldom::PointPtr kmlPoint, int& pointFeatureCount, OGREnvelope& datasetMBR)
    {
        const bool isValid = kmlPoint->has_coordinates();
        if (isValid)
        {
            kmldom::CoordinatesPtr coords = kmlPoint->get_coordinates();
            const size_t numCoords = coords->get_coordinates_array_size();
            if (numCoords > 0)
            {
                const kmlbase::Vec3 coord = coords->get_coordinates_array_at(0);
                datasetMBR.Merge(coord.get_longitude(), coord.get_latitude());
                pointFeatureCount++;
            }
        }
        return isValid;
    }

    inline bool isLineStringValid(kmldom::LineStringPtr kmlLineString, OGREnvelope& datasetMBR)
    {
        const bool isValid = (kmlLineString->has_coordinates() && kmlLineString->get_coordinates()->get_coordinates_array_size() > 0);
        if (isValid)
        {
            kmldom::CoordinatesPtr coords = kmlLineString->get_coordinates();
            const size_t numCoords = coords->get_coordinates_array_size();
            for (size_t n = 0; n < numCoords; n++)
            {
                const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                datasetMBR.Merge(coord.get_longitude(), coord.get_latitude());
            }
        }
        return isValid;
    }

    inline bool isLinearRingValid(kmldom::LinearRingPtr kmlLinearRing, OGREnvelope& datasetMBR)
    {
        const bool isValid = (kmlLinearRing->has_coordinates() && kmlLinearRing->get_coordinates()->get_coordinates_array_size() > 0);
        if (isValid)
        {
            kmldom::CoordinatesPtr coords = kmlLinearRing->get_coordinates();
            const size_t numCoords = coords->get_coordinates_array_size();
            for (size_t n = 0; n < numCoords; n++)
            {
                const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                datasetMBR.Merge(coord.get_longitude(), coord.get_latitude());
            }
        }
        return isValid;
    }

    inline bool isPolygonValid(kmldom::PolygonPtr kmlPolygon, OGREnvelope& datasetMBR)
    {
        bool isValid = false;
        if (kmlPolygon->has_outerboundaryis())
        {
            kmldom::OuterBoundaryIsPtr outerBoundardy = kmlPolygon->get_outerboundaryis();
            if (outerBoundardy->has_linearring())
            {
                kmldom::LinearRingPtr linearRing = outerBoundardy->get_linearring();
                isValid = (linearRing->has_coordinates() && linearRing->get_coordinates()->get_coordinates_array_size() > 0);
                if (isValid)
                {
                    kmldom::CoordinatesPtr coords = linearRing->get_coordinates();
                    const size_t numCoords = coords->get_coordinates_array_size();
                    for (size_t n = 0; n < numCoords; n++)
                    {
                        const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                        datasetMBR.Merge(coord.get_longitude(), coord.get_latitude());
                    }
                }
            }
        }
        return isValid;
    }

    inline bool isGxTrackValid(kmldom::GxTrackPtr kmlGxTrack, OGREnvelope& datasetMBR)
    {
        const bool isValid = (kmlGxTrack->get_gx_coord_array_size() > 0);
        if (isValid)
        {
            const size_t numCoords = kmlGxTrack->get_gx_coord_array_size();
            for (size_t n = 0; n < numCoords; n++)
            {
                const kmlbase::Vec3 coord = kmlGxTrack->get_gx_coord_array_at(n);
                datasetMBR.Merge(coord.get_longitude(), coord.get_latitude());
            }
        }
        return isValid;
    }

    inline bool isGeometryValid(kmldom::GeometryPtr kmlGeometry, int& pointFeatureCount, OGREnvelope& datasetMBR)
    {
        bool isValid = false;
        if (kmlGeometry->IsA(kmldom::Type_Point))
        {
            kmldom::PointPtr kmlPoint = AsPoint(kmlGeometry);
            isValid = isPointValid(kmlPoint, pointFeatureCount, datasetMBR);
        }
        else if (kmlGeometry->IsA(kmldom::Type_LineString))
        {
            kmldom::LineStringPtr kmlLineString = AsLineString(kmlGeometry);
            isValid = isLineStringValid(kmlLineString, datasetMBR);
        }
        else if (kmlGeometry->IsA(kmldom::Type_LinearRing))
        {
            kmldom::LinearRingPtr kmlLinearRing = AsLinearRing(kmlGeometry);
            isValid = isLinearRingValid(kmlLinearRing, datasetMBR);
        }
        else if (kmlGeometry->IsA(kmldom::Type_Polygon))
        {
            kmldom::PolygonPtr kmlPolygon = AsPolygon(kmlGeometry);
            isValid = isPolygonValid(kmlPolygon, datasetMBR);
        }
        else if (kmlGeometry->IsA(kmldom::Type_GxTrack))
        {
            kmldom::GxTrackPtr kmlGxTrack = AsGxTrack(kmlGeometry);
            isValid = isGxTrackValid(kmlGxTrack, datasetMBR);
        }
        else if (kmlGeometry->IsA(kmldom::Type_MultiGeometry))
        {
            kmldom::MultiGeometryPtr multiGeometry = AsMultiGeometry(kmlGeometry);
            const size_t numGeometries = multiGeometry->get_geometry_array_size();
            for (size_t n = 0; n < numGeometries; n++)
            {
                kmldom::GeometryPtr geoPtr = multiGeometry->get_geometry_array_at(n);
                isValid = isGeometryValid(geoPtr, pointFeatureCount, datasetMBR);

                if (!isValid)
                    break;
            }
        }
        return isValid;
    }

    //! Returns true if the KML/KMZ feature is supported.
    bool KmlContent::isValidFeature(kmldom::FeaturePtr feature, int& pointFeatureCount, OGREnvelope& datasetMBR) const
    {
        bool isValid = false;

        //! Tests a feature to see if it has some of the prerequisites for loading in WinTAK	
        if (feature->IsA(kmldom::Type_Placemark))
        {
            kmldom::PlacemarkPtr placemark = AsPlacemark(feature);
            if (placemark->has_geometry() && (!placemark->get_geometry()->IsA(kmldom::Type_Model)))
            {
                kmldom::GeometryPtr kmlGeometry = placemark->get_geometry();
                if (kmlGeometry->IsA(kmldom::Type_MultiGeometry))
                {
                    kmldom::MultiGeometryPtr multiGeometry = AsMultiGeometry(kmlGeometry);
                    const size_t numGeometries = multiGeometry->get_geometry_array_size();
                    for (size_t n = 0; n < numGeometries; n++)
                    {
                        kmldom::GeometryPtr geoPtr = multiGeometry->get_geometry_array_at(n);
                        isValid = isGeometryValid(geoPtr, pointFeatureCount, datasetMBR);

                        if (!isValid)
                            break;
                    }
                }
                else if (kmlGeometry->IsA(kmldom::Type_GxMultiTrack))
                {
                    kmldom::GxMultiTrackPtr gxMultiTrack = AsGxMultiTrack(kmlGeometry);
                    const size_t numGxTracks = gxMultiTrack->get_gx_track_array_size();
                    for (size_t n = 0; n < numGxTracks; n++)
                    {
                        kmldom::GxTrackPtr gxTrackPtr = gxMultiTrack->get_gx_track_array_at(n);
                        isValid = isGxTrackValid(gxTrackPtr, datasetMBR);

                        if (!isValid)
                            break;
                    }
                }
                else
                {
                    isValid = isGeometryValid(kmlGeometry, pointFeatureCount, datasetMBR);
                }
            }
        }
        else if (feature->IsA(kmldom::Type_GroundOverlay))
        {
            kmldom::GroundOverlayPtr groundOverlay = AsGroundOverlay(feature);
            if (groundOverlay->has_gx_latlonquad())
            {
                kmldom::GxLatLonQuadPtr gxLatLonQuad = groundOverlay->get_gx_latlonquad();
                isValid = (gxLatLonQuad->has_coordinates() && (gxLatLonQuad->get_coordinates()->get_coordinates_array_size() > 0));
                if (isValid)
                {
                    kmldom::CoordinatesPtr coords = gxLatLonQuad->get_coordinates();
                    const size_t numCoords = coords->get_coordinates_array_size();
                    for (size_t n = 0; n < numCoords; n++)
                    {
                        const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                        datasetMBR.Merge(coord.get_longitude(), coord.get_latitude());
                    }
                }
            }
        }

        return isValid;
    }

    void KmlContent::calculateLevelofDetail()
    {
        // reset level of detail variables
        m_defaultMinResolution = 0;



        // compute the default resolution
        {
            OGREnvelope datasetMBR;
            int pointFeatureCount = 0;
            size_t featureCount = 0;
            std::set<kmldom::FeaturePtr>::iterator fsIt;
            std::set<std::string> nonLodRegionFeatureSets;
            for (fsIt = m_featureSets.begin(); fsIt != m_featureSets.end(); fsIt++)
            {
                // check if the kml feature defines level of detail information
                if ((*fsIt)->has_region())
                {
                    double kmlResolution = 0;
                    kmldom::RegionPtr region = (*fsIt)->get_region();
                    if (region->has_latlonaltbox() && region->has_lod())
                    {
                        kmldom::LatLonAltBoxPtr latlonbox = region->get_latlonaltbox();
                        kmldom::LodPtr lod = region->get_lod();
                        if (latlonbox->has_east() && latlonbox->has_west() && latlonbox->has_north() &&
                            latlonbox->has_south() && lod->has_minlodpixels())
                        {
                            const double midLatDelta = (latlonbox->get_north() - latlonbox->get_south()) / 2;
                            atakmap::core::GeoPoint from(latlonbox->get_north() - midLatDelta, latlonbox->get_east());
                            atakmap::core::GeoPoint to(latlonbox->get_north() - midLatDelta, latlonbox->get_west());
                            const double distance = atakmap::util::distance::calculateRange(from, to);
                            const double resolution = distance / lod->get_minlodpixels();
                            if (kmlResolution < resolution)
                                kmlResolution = resolution;
                        }
                    }

                    std::string fsname = (*fsIt)->get_name();
                    if (kmlResolution > 0)
                    {
                        m_featureSetMinResolution[(*fsIt)->get_name()] = kmlResolution;
                    }
                    else
                    {
                        nonLodRegionFeatureSets.insert(fsname);
                    }
                }
                else
                {
                    nonLodRegionFeatureSets.insert((*fsIt)->get_name());

                }

                // add to the statistics on the dataset as a whole to compute the
                // default minimum resolution
                auto itEnd = m_features.end();
                auto it = m_features.find(*fsIt);

                for (; (it != itEnd) && (it->first == *fsIt); ++it, featureCount++)
                {
                    isValidFeature(it->second, pointFeatureCount, datasetMBR);
                }
            }
            //
            //
            //
            if (!m_featureSetMinResolution.empty())
            {
                std::map<std::string, double> inheritedMinRes;
                for (auto it = nonLodRegionFeatureSets.begin(); it != nonLodRegionFeatureSets.end(); it++)
                {
                    std::string s = *it + "/";
                    auto lb = m_featureSetMinResolution.upper_bound(*it);
                    while (true)
                    {
                        if (lb == m_featureSetMinResolution.begin())
                            break;
                        lb--;
                        std::string lbs = lb->first + "/";
                        if (s.compare(0, lbs.length(), lbs) == 0)
                        {
                            inheritedMinRes[*it] = lb->second;
                            break;
                        }
                    }
                }
                m_featureSetMinResolution.insert(inheritedMinRes.begin(), inheritedMinRes.end());
            }
            //
            // Don't calculate level of detail for no features or just a single point.
            //
            size_t levelOfDetail = 0;
            if (featureCount > 1 || pointFeatureCount > 1)
            {
                const size_t areaThreshold = atakmap::feature::OGR_FeatureDataSource::ComputeAreaThreshold(static_cast<unsigned int>(std::ceil(atakmap::core::AtakMapView::DENSITY)));
                levelOfDetail = atakmap::feature::OGR_FeatureDataSource::ComputeLevelOfDetail(areaThreshold, datasetMBR);
                if (featureCount > 5000)        // Maximum feature density is 5000.
                {
                    //
                    // Apply a fudge factor to the level of detail.
                    //
                    static const double log_2(std::log(2));
                    levelOfDetail += 2 + static_cast<std::size_t>(std::ceil(std::log(featureCount / 5000.0) / log_2));
                }
            }

            m_defaultMinResolution = (levelOfDetail < 32 ? 156543.034 / (1 << levelOfDetail) : 0.0);
        }
    }

    void KmlContent::populateStyleStorage(kmldom::DocumentPtr feature)
    {
        const size_t numStyles = feature->get_styleselector_array_size();
        for (size_t n = 0; n < numStyles; n++)
        {
            kmldom::StyleSelectorPtr styleSelector = feature->get_styleselector_array_at(n);
            populateStyleStorage(styleSelector);
        }
    }

    void KmlContent::populateStyleStorage(kmldom::StyleSelectorPtr styleSelector)
    {
        if (styleSelector != nullptr)
        {
            if (styleSelector->IsA(kmldom::Type_Style))
            {
                kmldom::StylePtr kmlStyle = AsStyle(styleSelector);
                const std::string styleId = kmlStyle->get_id();
                StylePtr_const style(getStyle(styleSelector), atakmap::feature::Style::destructStyle);

                if (style.get() != nullptr)
                    m_styles.insert(Style_Pair(styleId, std::move(style)));
            }
            else if (styleSelector->IsA(kmldom::Type_StyleMap))
            {
                kmldom::StyleMapPtr kmlStyleMap = AsStyleMap(styleSelector);
                const std::string styleMapId = kmlStyleMap->get_id();
                const size_t numStylePairs = kmlStyleMap->get_pair_array_size();
                for (size_t n = 0; n < numStylePairs; n++)
                {
                    kmldom::PairPtr kmlStylePair = kmlStyleMap->get_pair_array_at(n);
                    StyleMapPair styleMapPair(kmlStylePair->get_key(), kmlStylePair->get_styleurl().c_str());
                    m_styleMaps.insert(StyleMap_Pair(styleMapId, styleMapPair));
                }
            }
        }
    }

	atakmap::feature::Style* KmlContent::getStyle(kmldom::StyleSelectorPtr styleSelector)
	{
		atakmap::feature::CompositeStyle* compositeStyle = nullptr;
		std::vector<atakmap::feature::Style*> styleVector;

		populateStylesFromSelector(styleSelector, styleVector);

		if (styleVector.size() == 1)
			return styleVector[0];
		else if (!styleVector.empty())
			compositeStyle = new atakmap::feature::CompositeStyle(styleVector);

		return compositeStyle;
	}

    void KmlContent::populateStylesFromSelector(kmldom::StyleSelectorPtr styleSelector, std::vector<atakmap::feature::Style*>& styleVector)
    {
		if (styleSelector == nullptr)
			return;
        kmldom::StylePtr kmlStyle = AsStyle(styleSelector);
        if (!kmlStyle.get())
            return;

        kmldom::IconStylePtr iconStyle;
        kmldom::LineStylePtr lineStyle;
        kmldom::PolyStylePtr polyStyle;
        if (kmlStyle->has_iconstyle())
            iconStyle = kmlStyle->get_iconstyle();
        if (kmlStyle->has_linestyle())
            lineStyle = kmlStyle->get_linestyle();
        if (kmlStyle->has_polystyle())
            polyStyle = kmlStyle->get_polystyle();

        // create the icon style
        if (kmlStyle->has_iconstyle())
        {
            atakmap::feature::Style* style = getIconStyle(iconStyle);

            if (style != nullptr)
                styleVector.push_back(style);
        }

        // NOTE: for the outline/fill properties:
        // if 'has_xxx' is 'true', the corresponding field has been explicitly
        // set -- evaluate 'get_xxx' to see if enabled or not.  if 'has_xxx'
        // is 'false', the field is not explicitly set and is interpreted as
        // 'true' by default.

        // create the line style. if there's a polystyle and outline is
        // disabled, don't include the line style.
        if (kmlStyle->has_linestyle() &&
            (!kmlStyle->has_polystyle() || !polyStyle->has_outline() || polyStyle->get_outline()))
        {
            atakmap::feature::Style* style = getLineStyle(lineStyle);

            if (style != nullptr)
                styleVector.push_back(style);
        }

        // create the fill style
        if (kmlStyle->has_polystyle() && polyStyle->has_color() && (!polyStyle->has_fill() || polyStyle->get_fill()))
        {
            atakmap::feature::Style* style = getFillStyle(polyStyle);

            if (style != nullptr)
                styleVector.push_back(style);
        }

        // TODO: support other kml styles
        /*if (kmlStyle->has_labelstyle())
        kmlStyle->get_labelstyle();
        if (kmlStyle->has_liststyle())
        kmlStyle->get_liststyle();
        if (kmlStyle->has_balloonstyle())
        kmlStyle->get_balloonstyle();*/
    }

    /******************************************************************************
    method to open a kml file

    Args:          pszFilename file to open
    bUpdate     update mode

    Returns:       True on success, false on failure

    ******************************************************************************/

    bool KmlContent::openKml(const char *pszFilename)
    {
        std::string oKmlKml;
        char szBuffer[1024 + 1];
        FILE* fp;

        errno_t err = fopen_s(&fp, pszFilename, "r");
        if (err != 0)
        {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "Cannot open %s", pszFilename);
            return false;
        }
        int nRead;
        while ((nRead = static_cast<int>(fread(szBuffer, sizeof(char), 1024, fp))) != 0)
        {
            try
            {
                oKmlKml.append(szBuffer, nRead);
            }
            catch (const std::bad_alloc&)
            {
                fclose(fp);
                return false;
            }
        }
        fclose(fp);

        std::string oKmlErrors;

        kmldom::ElementPtr poKmlRoot = kmldom::Parse(oKmlKml, &oKmlErrors);

        if (!poKmlRoot)
        {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "ERROR parsing kml %s :%s", pszFilename, oKmlErrors.c_str());
            return false;
        }

        /***** get the container from root  *****/
        m_poKmlDSContainer = GetContainerFromRoot(m_poKmlFactory, poKmlRoot);
        if (m_poKmlDSContainer.get() == nullptr)
        {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "ERROR parsing kml %s :%s %s", pszFilename, "This file does not fit the OGR model,", "there is no container element at the root.");
            return false;
        }

        m_isKml = true;

        populateFeatureStorage(m_poKmlDSContainer);

        return true;
    }

    /******************************************************************************
    method to open a kmz file

    Args:          pszFilename file to open
    bUpdate     update mode

    Returns:       True on success, false on failure

    ******************************************************************************/


    bool KmlContent::openKmz(const char *pszFilename)
    {
        kmlengine::KmzFilePtr poKmlKmzfile;
        try
        {
            poKmlKmzfile = kmlengine::KmzFile::OpenFromFile(pszFilename);
        }
        catch (...)
        {
            return false;
        }

        if (poKmlKmzfile.get() == nullptr)
        {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "%s is not a valid kmz file", pszFilename);
            return false;
        }

        /***** read the doc.kml *****/

        std::string oKmlKml;
        std::string oKmlKmlPath;
        if (!poKmlKmzfile->ReadKmlAndGetPath(&oKmlKml, &oKmlKmlPath))
        {
            return false;
        }

        /***** parse the kml into the DOM *****/

        std::string oKmlErrors;
        kmldom::ElementPtr poKmlDocKmlRoot = kmldom::Parse(oKmlKml, &oKmlErrors);

        if (!poKmlDocKmlRoot)
        {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "ERROR parsing kml layer %s from %s :%s", oKmlKmlPath.c_str(), pszFilename, oKmlErrors.c_str());
            return false;
        }

        /***** Get the child container from root. *****/

        m_poKmlDSContainer = GetContainerFromRoot(m_poKmlFactory, poKmlDocKmlRoot);

        if (m_poKmlDSContainer.get() == nullptr)
        {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "ERROR parsing %s from %s :%s", oKmlKmlPath.c_str(), pszFilename, "kml contains no Containers");
            return false;
        }

        m_isKmz = true;

        populateFeatureStorage(m_poKmlDSContainer);

        return true;
    }

    /******************************************************************************
    function to get the container from the kmlroot

    Args:          poKmlRoot   the root element

    Returns:       root if its a container, if its a kml the container it
    contains, or NULL

    ******************************************************************************/

    kmldom::ContainerPtr KmlContent::GetContainerFromRoot(
        kmldom::KmlFactory *poKmlFactory, kmldom::ElementPtr poKmlRoot)
    {
        kmldom::ContainerPtr poKmlContainer = nullptr;

        int bReadGroundOverlay = 1;// CPLTestBool(CPLGetConfigOption("LIBKML_READ_GROUND_OVERLAY", "YES"));

        if (poKmlRoot) {

            /***** skip over the <kml> we want the container *****/

            if (poKmlRoot->IsA(kmldom::Type_kml)) {

                kmldom::KmlPtr poKmlKml = AsKml(poKmlRoot);

                if (poKmlKml->has_feature()) {
                    kmldom::FeaturePtr poKmlFeat = poKmlKml->get_feature();

                    if (poKmlFeat->IsA(kmldom::Type_Container))
                        poKmlContainer = AsContainer(poKmlFeat);
                    else if (poKmlFeat->IsA(kmldom::Type_Placemark) ||
                        (bReadGroundOverlay && poKmlFeat->IsA(kmldom::Type_GroundOverlay)))
                    {
                        poKmlContainer = poKmlFactory->CreateDocument();
                        poKmlContainer->add_feature(kmldom::AsFeature(kmlengine::Clone(poKmlFeat)));
                    }
                }
            }

            else if (poKmlRoot->IsA(kmldom::Type_Container))
                poKmlContainer = AsContainer(poKmlRoot);
        }

        return poKmlContainer;
    }

    const char *KmlContent::getType() const NOTHROWS
    {
        return "kml";
    }

    const char *KmlContent::getProvider() const NOTHROWS
    {
        return "KML";
    }

    TAKErr KmlContent::moveToNextFeature() NOTHROWS
    {
        TAKErr code = TE_Ok;

        if (!m_firstFeature)
            ++m_currentFeatureIndex;
        else
            m_firstFeature = false;

        if ((m_currentFeatureIndex != m_features.end()) && (m_currentFeatureIndex->first == m_currentFeatureSet))
        {
            m_currentFeature = m_currentFeatureIndex->second;
            if (m_currentFeature != nullptr)
            {
                // set attributes
                atakmap::util::AttributeSet attributes;
                m_visible = true;

                TAK::Engine::Port::String name(nullptr);
                if (m_currentFeature->has_name())
                {
                    name = m_currentFeature->get_name().c_str();
                }

                if (m_currentFeature->has_description())
                    attributes.setString("description", m_currentFeature->get_description().c_str());
                if (m_currentFeature->has_visibility())
                {
                    m_visible = m_currentFeature->get_visibility();
                    attributes.setInt("visibility", m_visible ? 1 : 0);
                }
                if (m_currentFeature->has_extendeddata())
                {
                    kmldom::ExtendedDataPtr extendedData = m_currentFeature->get_extendeddata();
                    const size_t numData = extendedData->get_data_array_size();
                    for (size_t n = 0; n < numData; n++)
                    {
                        kmldom::DataPtr data = extendedData->get_data_array_at(n);
                        attributes.setString(data->get_name().c_str(), data->get_value().c_str());
                    }
                } 

                std::unique_ptr<KmlDefinition> featureDefinition(new KmlDefinition(name, attributes));

				std::vector<atakmap::feature::Style*> styleVector;
                // set style values
                // check for inline style
                if (m_currentFeature->has_styleselector())
                {
                    kmldom::StyleSelectorPtr styleSelector = m_currentFeature->get_styleselector();
					populateStylesFromSelector(styleSelector, styleVector);
                }
                // check for shared style
                if (m_currentFeature->has_styleurl())
                {
                    // remove '#' from beginning of string
                    std::string styleUrl = m_currentFeature->get_styleurl();
                    if (styleUrl.compare(STYLE_HEADER_SYMBOL_POS, 1, STYLE_HEADER_SYMBOL) == 0)
                        styleUrl.erase(styleUrl.begin());

                    // check if it references a singular style
                    auto featureStyle = m_styles.find(styleUrl);
                    if (featureStyle != m_styles.end())
                    {
						styleVector.push_back(featureStyle->second->clone());
                    }
                    else
                    {
                        // check if it references a style map
                        auto styleMapIt = m_styleMaps.find(styleUrl);
                        auto styleMapItEnd = m_styleMaps.end();
                        if (styleMapIt != styleMapItEnd)
                        {
                            for (; (styleMapIt != styleMapItEnd) && (styleMapIt->first == styleUrl); ++styleMapIt)
                            {
                                // use normal style
                                if (styleMapIt->second.m_key == STYLE_MAP_PAIR_NORMAL_TYPE)
                                {
                                    // remove '#' from beginning of string
                                    std::string styleMapPairUrl = styleMapIt->second.m_styleUrl;
                                    if (styleMapPairUrl.compare(STYLE_HEADER_SYMBOL_POS, 1, STYLE_HEADER_SYMBOL) == 0)
                                        styleMapPairUrl.erase(styleMapPairUrl.begin());

                                    featureStyle = m_styles.find(styleMapPairUrl);
                                    if (featureStyle != m_styles.end())
                                    {
										styleVector.push_back(featureStyle->second->clone());
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                // no styling was determined, apply default
                if (styleVector.empty())
                {
					bool styleSet = false;
					if (m_currentFeature->has_styleurl())
					{
						// check if there are styles that cannot be read by libkml
						TAK::Engine::Port::String styleString = m_currentFeature->get_styleurl().c_str();
						code = driver->getStyle(styleString);
						if (styleString && styleString[0] != '#') {
							featureDefinition->setStyle(styleString.get());
							styleSet = true;
						}
					}
					if (!styleSet)
					{
						featureDefinition->setStyle(DefaultPointStyle);
					}
                }
				else if (styleVector.size() == 1)
				{
					StylePtr_const style(styleVector[0], atakmap::feature::Style::destructStyle);
					if (style.get() != nullptr) {
						featureDefinition->setStyle(std::move(style));
					}
				}
				else
				{
					auto* compositeStyle = new atakmap::feature::CompositeStyle(styleVector);
					StylePtr_const style(compositeStyle, atakmap::feature::Style::destructStyle);
					if (style.get() != nullptr) {
						featureDefinition->setStyle(std::move(style));
					}
				}

                if (m_currentFeature->IsA(kmldom::Type_Placemark))
                {
                    // set geometry values
                    kmldom::PlacemarkPtr placemark = AsPlacemark(m_currentFeature);
                    if (placemark->has_geometry())
                    {
                        kmldom::GeometryPtr kmlGeometry = placemark->get_geometry();
                        if (kmlGeometry->IsA(kmldom::Type_MultiGeometry)) {
                            AltitudeMode altitudeMode(TEAM_ClampToGround);
                            double extrude = 0.0;
                            std::unique_ptr<atakmap::feature::GeometryCollection> collection(new atakmap::feature::GeometryCollection(atakmap::feature::Geometry::_2D));
                            kmldom::MultiGeometryPtr multiGeometry = AsMultiGeometry(kmlGeometry);
                            const size_t numGeometries = multiGeometry->get_geometry_array_size();
                            for (size_t n = 0; n < numGeometries; n++)
                            {
                                kmldom::GeometryPtr geoPtr = multiGeometry->get_geometry_array_at(n);

                                if (geoPtr->IsA(kmldom::KmlDomType::Type_MultiGeometry))
                                {
                                    kmldom::MultiGeometryPtr childMultiGeometry = AsMultiGeometry(geoPtr);
                                    const size_t numChildGeometries = childMultiGeometry->get_geometry_array_size();
                                    for (size_t m = 0; m < numChildGeometries; m++)
                                    {
                                        kmldom::GeometryPtr childGeoPtr = childMultiGeometry->get_geometry_array_at(m);
                                        std::unique_ptr<atakmap::feature::Geometry> geometry(getGeometry(childGeoPtr, altitudeMode, extrude));
                                        if (geometry.get() != nullptr)
                                            collection->add(geometry.get());
                                    }
                                }
                                else
                                {
                                    // GeometryCollection stores a clone internally, so make sure we cleanup
                                    std::unique_ptr<atakmap::feature::Geometry> geometry(getGeometry(geoPtr, altitudeMode, extrude));
                                    if (geometry.get() != nullptr)
                                        collection->add(geometry.get());
                                }
                            }
                            if (!collection->empty())
                                featureDefinition->setGeometry(GeometryPtr(collection.release(), Memory_deleter_const<atakmap::feature::Geometry>), altitudeMode, extrude);
                            else
                                code = TE_IllegalState;
                        }
                        else if (kmlGeometry->IsA(kmldom::Type_GxMultiTrack))
                        {
                            std::unique_ptr<atakmap::feature::GeometryCollection> collection(new atakmap::feature::GeometryCollection(atakmap::feature::Geometry::_2D));
                            kmldom::GxMultiTrackPtr gxMultiTrack = AsGxMultiTrack(kmlGeometry);
                            const size_t numGxTracks = gxMultiTrack->get_gx_track_array_size();
                            AltitudeMode altitudeMode = AltitudeMode::TEAM_ClampToGround;
                            double extrude = 0.0;
                            for (size_t n = 0; n < numGxTracks; n++)
                            {
                                kmldom::GxTrackPtr gxTrackPtr = gxMultiTrack->get_gx_track_array_at(n);

                                // GeometryCollection stores a clone internally, so make sure we cleanup
                                std::unique_ptr<atakmap::feature::Geometry> geometry(getGxTrack(gxTrackPtr, altitudeMode));
                                if (geometry.get() != nullptr)
                                    collection->add(geometry.get());
                            }
                            if (!collection->empty())
                                featureDefinition->setGeometry(GeometryPtr(collection.release(), Memory_deleter_const<atakmap::feature::Geometry>), altitudeMode, extrude);
                            else
                                code = TE_IllegalState;
                        }
                        else {
                            AltitudeMode altitudeMode;
                            double extrude = 0.0;
                            std::unique_ptr<atakmap::feature::Geometry> geometry(getGeometry(kmlGeometry, altitudeMode, extrude));
                            if (geometry != nullptr)
                                featureDefinition->setGeometry(GeometryPtr(geometry.release(), Memory_deleter_const<atakmap::feature::Geometry>), altitudeMode, extrude);
                            else
                                code = TE_IllegalState;
                        }
                    }
                }
                else if (m_currentFeature->IsA(kmldom::Type_GroundOverlay))
                {
                    // set geometry values
                    kmldom::GroundOverlayPtr groundOverlay = AsGroundOverlay(m_currentFeature);
                    if (groundOverlay->has_gx_latlonquad())
                    {
                        AltitudeMode altitudeMode;
                        double extrude = 0.0;
                        kmldom::GxLatLonQuadPtr kmlGxLatLonQuad = groundOverlay->get_gx_latlonquad();
                        std::unique_ptr<atakmap::feature::Geometry> geometry(getLinearRing(kmlGxLatLonQuad, altitudeMode));
                        if (geometry != nullptr)
                            featureDefinition->setGeometry(GeometryPtr(geometry.release(), Memory_deleter_const<atakmap::feature::Geometry>), altitudeMode, extrude);
                        else
                            code = TE_IllegalState;
                    }
                }

                m_featureDefinition.reset();
                m_featureDefinition = std::move(featureDefinition);
            }
        }
        else
        {
            code = TE_Done;
            m_firstFeature = true;
        }

        return code;
    }

    TAKErr KmlContent::moveToNextFeatureSet() NOTHROWS
    {
        TAKErr code = TE_Ok;

        if (!m_firstFeatureSet)
        {
            ++m_currentFeatureSetIndex;
        }
        else
        {
            m_currentFeatureSetIndex = m_featureSets.begin();
            m_firstFeatureSet = false;

            // calculate the FeatureSet level of detail
            calculateLevelofDetail();
        }

        auto featureSetEnd = m_featureSets.end();
        if (m_currentFeatureSetIndex != featureSetEnd)
        {
            m_currentFeatureSet = *m_currentFeatureSetIndex;

            m_currentFeatureIndex = m_features.find(m_currentFeatureSet);
            if ((m_currentFeatureIndex != m_features.end()) && (m_currentFeatureIndex->first == m_currentFeatureSet))
            {
                m_firstFeature = true;
                m_featureNameId = 0;
            }

            m_featureSetName = m_currentFeatureSet->get_name().c_str();
        }
        else if (m_currentFeatureSetIndex == featureSetEnd)
        {
            m_firstFeatureSet = true;
            code = TE_Done;
        }

        return code;
    }

    TAKErr KmlContent::get(FeatureDefinition2 **feature) const NOTHROWS
    {
        if (!m_featureDefinition.get())
            return TE_IllegalState;
        *feature = m_featureDefinition.get();
        return TE_Ok;
    }

    atakmap::feature::Geometry* KmlContent::getGeometry(kmldom::GeometryPtr kmlGeometry, AltitudeMode& altitudeMode, double& extrude) const
    {
        atakmap::feature::Geometry* geometry = nullptr;
        if (kmlGeometry != nullptr && kmlGeometry->IsA(kmldom::Type_Geometry))
        {
            if (kmlGeometry->IsA(kmldom::Type_Point))
            {
                kmldom::PointPtr kmlPoint = AsPoint(kmlGeometry);
                geometry = getPoint(kmlPoint, altitudeMode, extrude);
            }
            else if (kmlGeometry->IsA(kmldom::Type_LineString))
            {
                kmldom::LineStringPtr kmlLineString = AsLineString(kmlGeometry);
                geometry = getLineString(kmlLineString, altitudeMode);
            }
            else if (kmlGeometry->IsA(kmldom::Type_LinearRing))
            {
                kmldom::LinearRingPtr kmlLinearRing = AsLinearRing(kmlGeometry);
                geometry = getLinearRing(kmlLinearRing, altitudeMode);
            }
            else if (kmlGeometry->IsA(kmldom::Type_Polygon))
            {
                kmldom::PolygonPtr kmlPolygon = AsPolygon(kmlGeometry);
                geometry = getPolygon(kmlPolygon, altitudeMode);
            }
            else if (kmlGeometry->IsA(kmldom::Type_GxTrack))
            {
                kmldom::GxTrackPtr kmlGxTrack = AsGxTrack(kmlGeometry);
                geometry = getGxTrack(kmlGxTrack, altitudeMode);
            }

            // Note: current unhandled kml geometries
            // Type_Model
        }
        return geometry;
    }

    atakmap::feature::Geometry* KmlContent::getPoint(kmldom::PointPtr kmlPoint, AltitudeMode& altitudeMode, double& extrude) const
    {
        atakmap::feature::Point* point = nullptr;
        if (kmlPoint != nullptr && kmlPoint->IsA(kmldom::Type_Point))
        {
            double x = 0.0, y = 0.0;
            if (kmlPoint->has_coordinates())
            {
                kmldom::CoordinatesPtr coords = kmlPoint->get_coordinates();
                const size_t numCoords = coords->get_coordinates_array_size();
                if (numCoords > 0)
                {
                    const kmlbase::Vec3 coord = coords->get_coordinates_array_at(0);
                    x = coord.get_longitude();
                    y = coord.get_latitude();
                    point = new atakmap::feature::Point(x, y);
                }
            }
            if (kmlPoint->has_altitudemode())
            {
                switch (kmlPoint->get_altitudemode()) {
                    case kmldom::ALTITUDEMODE_ABSOLUTE:
                        altitudeMode = AltitudeMode::TEAM_Absolute;
                        break;
                    case kmldom::ALTITUDEMODE_CLAMPTOGROUND:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::ALTITUDEMODE_RELATIVETOGROUND:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else if (kmlPoint->has_gx_altitudemode()) {
                switch (kmlPoint->get_gx_altitudemode()) {
                    case kmldom::GX_ALTITUDEMODE_CLAMPTOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::GX_ALTITUDEMODE_RELATIVETOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else {
                altitudeMode = AltitudeMode::TEAM_ClampToGround;
            }
            if (kmlPoint->has_extrude()) {
                extrude = kmlPoint->get_extrude() ? -1 : 0.0;
            }
        }
        return point;
    }

    atakmap::feature::Geometry* KmlContent::getLineString(kmldom::LineStringPtr kmlLineString, AltitudeMode& altitudeMode) const
    {
        atakmap::feature::LineString* lineString = nullptr;
        if (kmlLineString != nullptr && kmlLineString->IsA(kmldom::Type_LineString))
        {
            if (kmlLineString->has_coordinates())
            {
                kmldom::CoordinatesPtr coords = kmlLineString->get_coordinates();
                const size_t numCoords = coords->get_coordinates_array_size();
                if (numCoords > 0)
                {
                    lineString = new atakmap::feature::LineString();
                    for (size_t n = 0; n < numCoords; n++)
                    {
                        const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                        lineString->addPoint(coord.get_longitude(), coord.get_latitude());
                    }
                }
            }
            if (kmlLineString->has_altitudemode()) {
                switch (kmlLineString->get_altitudemode()) {
                    case kmldom::ALTITUDEMODE_ABSOLUTE:
                        altitudeMode = AltitudeMode::TEAM_Absolute;
                        break;
                    case kmldom::ALTITUDEMODE_CLAMPTOGROUND:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::ALTITUDEMODE_RELATIVETOGROUND:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else if (kmlLineString->has_gx_altitudemode()) {
                switch (kmlLineString->get_gx_altitudemode()) {
                    case kmldom::GX_ALTITUDEMODE_CLAMPTOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::GX_ALTITUDEMODE_RELATIVETOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else {
                altitudeMode = AltitudeMode::TEAM_ClampToGround;
            }
        }
        return lineString;
    }

    atakmap::feature::Geometry* KmlContent::getLinearRing(kmldom::LinearRingPtr kmlLinearRing, AltitudeMode& altitudeMode) const
    {
        atakmap::feature::Polygon* polygon = nullptr;
        if (kmlLinearRing != nullptr && kmlLinearRing->IsA(kmldom::Type_LinearRing))
        {
            if (kmlLinearRing->has_coordinates())
            {
                kmldom::CoordinatesPtr coords = kmlLinearRing->get_coordinates();
                const size_t numCoords = coords->get_coordinates_array_size();
                if (numCoords > 0)
                {
                    atakmap::feature::LineString lineString;
                    for (size_t n = 0; n < numCoords; n++)
                    {
                        const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                        lineString.addPoint(coord.get_longitude(), coord.get_latitude());
                    }
                    polygon = new atakmap::feature::Polygon(lineString);
                }
            }
            if (kmlLinearRing->has_altitudemode()) {
                switch (kmlLinearRing->get_altitudemode()) {
                    case kmldom::ALTITUDEMODE_ABSOLUTE:
                        altitudeMode = AltitudeMode::TEAM_Absolute;
                        break;
                    case kmldom::ALTITUDEMODE_CLAMPTOGROUND:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::ALTITUDEMODE_RELATIVETOGROUND:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else if (kmlLinearRing->has_gx_altitudemode()) {
                switch (kmlLinearRing->get_gx_altitudemode()) {
                    case kmldom::GX_ALTITUDEMODE_CLAMPTOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::GX_ALTITUDEMODE_RELATIVETOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else {
                altitudeMode = AltitudeMode::TEAM_ClampToGround;
            }
        }
        return polygon;
    }

    atakmap::feature::Geometry* KmlContent::getLinearRing(kmldom::GxLatLonQuadPtr kmlGxLatLonQuad, AltitudeMode& altitudeMode) const
    {
        atakmap::feature::Polygon* polygon = nullptr;
        if (kmlGxLatLonQuad != nullptr && kmlGxLatLonQuad->IsA(kmldom::Type_GxLatLonQuad))
        {
            if (kmlGxLatLonQuad->has_coordinates())
            {
                kmldom::CoordinatesPtr coords = kmlGxLatLonQuad->get_coordinates();
                const size_t numCoords = coords->get_coordinates_array_size();
                if (numCoords > 0)
                {
                    atakmap::feature::LineString lineString;
                    for (size_t n = 0; n < numCoords; n++)
                    {
                        const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                        lineString.addPoint(coord.get_longitude(), coord.get_latitude());
                    }
                    polygon = new atakmap::feature::Polygon(lineString);
                }
            }

            altitudeMode = AltitudeMode::TEAM_ClampToGround;
        }
        return polygon;
    }

    atakmap::feature::Geometry* KmlContent::getPolygon(kmldom::PolygonPtr kmlPolygon, AltitudeMode& altitudeMode) const
    {
        atakmap::feature::Polygon* polygon = nullptr;
        if (kmlPolygon != nullptr && kmlPolygon->IsA(kmldom::Type_Polygon))
        {
            // retrieve the outer boundary of the polygon
            if (kmlPolygon->has_outerboundaryis())
            {
                kmldom::OuterBoundaryIsPtr outerBoundardy = kmlPolygon->get_outerboundaryis();
                if (outerBoundardy->has_linearring())
                {
                    kmldom::LinearRingPtr linearRing = outerBoundardy->get_linearring();
                    if (linearRing->has_coordinates())
                    {
                        kmldom::CoordinatesPtr coords = linearRing->get_coordinates();
                        const size_t numCoords = coords->get_coordinates_array_size();
                        if (numCoords > 0)
                        {
                            atakmap::feature::LineString outerLineString;
                            for (size_t n = 0; n < numCoords; n++)
                            {
                                const kmlbase::Vec3 coord = coords->get_coordinates_array_at(n);
                                outerLineString.addPoint(coord.get_longitude(), coord.get_latitude());
                            }
                            polygon = new atakmap::feature::Polygon(outerLineString);
                        }
                    }
                }

                if (polygon != nullptr)
                {
                    // retrieve the inner boundaries of the polygon
                    const size_t numInnerBoundary = kmlPolygon->get_innerboundaryis_array_size();
                    for (size_t n = 0; n < numInnerBoundary; n++)
                    {
                        kmldom::InnerBoundaryIsPtr innerBoundardy = kmlPolygon->get_innerboundaryis_array_at(n);
                        if (innerBoundardy->has_linearring())
                        {
                            kmldom::LinearRingPtr linearRing = innerBoundardy->get_linearring();
                            if (linearRing->has_coordinates())
                            {
                                atakmap::feature::LineString innerLineString;
                                kmldom::CoordinatesPtr coords = linearRing->get_coordinates();
                                const size_t numCoords = coords->get_coordinates_array_size();
                                for (size_t c = 0; c < numCoords; c++)
                                {
                                    const kmlbase::Vec3 coord = coords->get_coordinates_array_at(c);
                                    innerLineString.addPoint(coord.get_longitude(), coord.get_latitude());
                                }
                                polygon->addRing(innerLineString);
                            }
                        }
                    }
                }
            }
            if (kmlPolygon->has_altitudemode()) {
                switch (kmlPolygon->get_altitudemode()) {
                    case kmldom::ALTITUDEMODE_ABSOLUTE:
                        altitudeMode = AltitudeMode::TEAM_Absolute;
                        break;
                    case kmldom::ALTITUDEMODE_CLAMPTOGROUND:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::ALTITUDEMODE_RELATIVETOGROUND:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else if (kmlPolygon->has_gx_altitudemode()) {
                switch (kmlPolygon->get_gx_altitudemode()) {
                    case kmldom::GX_ALTITUDEMODE_CLAMPTOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::GX_ALTITUDEMODE_RELATIVETOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else {
                altitudeMode = AltitudeMode::TEAM_ClampToGround;
            }
        }
        return polygon;
    }

    atakmap::feature::Geometry* KmlContent::getGxTrack(kmldom::GxTrackPtr kmlGxTrack, AltitudeMode& altitudeMode) const
    {
        atakmap::feature::LineString* lineString = nullptr;
        if (kmlGxTrack != nullptr && kmlGxTrack->IsA(kmldom::Type_GxTrack))
        {
            const size_t numCoords = kmlGxTrack->get_gx_coord_array_size();
            if (numCoords > 0)
            {
                lineString = new atakmap::feature::LineString();
                for (size_t n = 0; n < numCoords; n++)
                {
                    const kmlbase::Vec3 coord = kmlGxTrack->get_gx_coord_array_at(n);
                    lineString->addPoint(coord.get_longitude(), coord.get_latitude());
                }
            }
            if (kmlGxTrack->has_altitudemode()) {
                switch (kmlGxTrack->get_altitudemode()) {
                    case kmldom::ALTITUDEMODE_ABSOLUTE:
                        altitudeMode = AltitudeMode::TEAM_Absolute;
                        break;
                    case kmldom::ALTITUDEMODE_CLAMPTOGROUND:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::ALTITUDEMODE_RELATIVETOGROUND:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else if (kmlGxTrack->has_gx_altitudemode()) {
                switch (kmlGxTrack->get_gx_altitudemode()) {
                    case kmldom::GX_ALTITUDEMODE_CLAMPTOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_ClampToGround;
                        break;
                    case kmldom::GX_ALTITUDEMODE_RELATIVETOSEAFLOOR:
                        altitudeMode = AltitudeMode::TEAM_Relative;
                        break;
                }
            } else {
                altitudeMode = AltitudeMode::TEAM_ClampToGround;
            }
        }
        return lineString;
    }

    atakmap::feature::Style* KmlContent::getIconStyle(kmldom::IconStylePtr kmlIconStyle) const
    {
        atakmap::feature::IconPointStyle* iconStyle = nullptr;
        if (kmlIconStyle != nullptr && kmlIconStyle->IsA(kmldom::Type_IconStyle))
        {
            // icon style must have either a color or and icon uri to be valid
            const bool hasURI = (kmlIconStyle->has_icon() && kmlIconStyle->get_icon()->has_href());
            if (kmlIconStyle->has_color() || hasURI)
            {
                unsigned int color = 0;   // 0xAARRGGBB
                double scale = 1;
                double rotation = 0;
                std::string iconURI;

                color = (kmlIconStyle->has_color() ? kmlIconStyle->get_color().get_color_argb() : DefaultSymbolColor);

                if (kmlIconStyle->has_scale())
                    scale = kmlIconStyle->get_scale();

                if (kmlIconStyle->has_heading())
                    rotation = kmlIconStyle->get_heading();

                if (kmlIconStyle->has_icon())
                {
                    kmldom::IconStyleIconPtr iconSytleIcon = kmlIconStyle->get_icon();
                    if (iconSytleIcon->has_href())
                    {
                        iconURI = iconSytleIcon->get_href();
                        if (!iconURI.empty())
                        {

                            // test if this is an Url address
                            const bool isAbsolute = (iconURI.find("://") != std::string::npos) || // absolute URI
                                (iconURI[0] == '/') || // absolute path on UNIX
                                std::regex_match(iconURI, std::regex("^[a-zA-Z]\\:")) || // windows drive
                                std::regex_match(iconURI, std::regex("^\\\\")); // windows network drive
                            if (!isAbsolute)
                            {
                                // TODO: test if file exists in archive for KMZ
                                std::ostringstream strm;
                                if (m_isKmz) {
                                    strm << "zip://" << m_filePath << "!/";
                                }
                                else {
                                    TAK::Engine::Port::String parentDir;
                                    if (IO_getParentFile(parentDir, m_filePath) == TE_Ok)
                                        strm << "file://" << parentDir << '/';
                                }
                                strm << iconURI;

                                iconURI = strm.str();
                            }
                        }
                    }
                }

                iconStyle = new atakmap::feature::IconPointStyle(color, iconURI.c_str(), static_cast<float>(scale),
                    atakmap::feature::IconPointStyle::H_CENTER, atakmap::feature::IconPointStyle::V_CENTER, static_cast<float>(rotation), true);
            }
        }
        return iconStyle;
    }

    atakmap::feature::Style* KmlContent::getLineStyle(kmldom::LineStylePtr kmlLineStyle) const
    {
        atakmap::feature::BasicStrokeStyle* lineStyle = nullptr;
        if (kmlLineStyle != nullptr && kmlLineStyle->IsA(kmldom::Type_LineStyle))
        {
            unsigned int color = 0;   // 0xAARRGGBB
            double width = 1;

            color = (kmlLineStyle->has_color() ? kmlLineStyle->get_color().get_color_argb() : DefaultLineColor);

            if (kmlLineStyle->has_width())
                width = kmlLineStyle->get_width();

            lineStyle = new atakmap::feature::BasicStrokeStyle(color, static_cast<float>(width));
        }
        return lineStyle;
    }

    atakmap::feature::Style* KmlContent::getFillStyle(kmldom::PolyStylePtr kmlPolyStyle) const
    {
        atakmap::feature::BasicFillStyle* fillStyle = nullptr;
        if (kmlPolyStyle != nullptr && kmlPolyStyle->has_color())
        {
            fillStyle = new atakmap::feature::BasicFillStyle(kmlPolyStyle->get_color().get_color_argb());
        }
        return fillStyle;
    }

    std::string KmlContent::getKmlRootName() const
    {
        std::string filename = m_filePath.get();
        if (!filename.empty())
        {
            filename = atakmap::util::getFileName(filename.c_str());
            const size_t index = filename.find_first_of(".");
            filename = filename.substr(0, index - 1);
        }
        return filename;
    }

    TAKErr KmlContent::getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS
    {
        name = m_featureSetName;
        return TE_Ok;
    }

    TAKErr KmlContent::getFeatureSetVisible(bool* visible) const NOTHROWS 
    {
        *visible = m_visible;
        return TE_Ok;
    }

    TAKErr KmlContent::getMinResolution(double *value) const NOTHROWS
    {
        std::map<std::string, double>::const_iterator entry;
        entry = m_featureSetMinResolution.find(std::string(m_featureSetName));
        if (entry == m_featureSetMinResolution.end())
            *value = m_defaultMinResolution;
        else
            *value = entry->second;
        return TE_Ok;
    }

    TAKErr KmlContent::getMaxResolution(double *value) const NOTHROWS
    {
        *value = MAX_RESOLUTION;
	    return TE_Ok;
    }

    TAKErr KmlContent::getVisible(bool *visible) const NOTHROWS
    {
        *visible = m_visible;
        return TE_Ok;
    }

    KmlDefinition::KmlDefinition(const char* name, const atakmap::util::AttributeSet& value) NOTHROWS :
        m_featureName(name),
        m_attributes(value),
        m_styleEncoding(StyleStyle),
        m_geometry(nullptr, nullptr),
        m_altitudeMode(TEAM_ClampToGround),
        m_extrude(0.0),
        m_ogrStyle(nullptr),
        m_style(nullptr, nullptr),
        m_feature(nullptr, nullptr) {}

    KmlDefinition::~KmlDefinition() NOTHROWS
    {
        freeGeometry();
        freeStyle();
    }

    void KmlDefinition::freeGeometry()
    {
        m_geometry.reset();
    }


    void KmlDefinition::freeStyle()
    {
        m_ogrStyle = nullptr;
        m_style.reset();
    }

    void KmlDefinition::setGeometry(GeometryPtr &&geometry, const AltitudeMode altitudeMode, const double extrude) NOTHROWS
    {
        if (!geometry.get())
        {
            //throw std::invalid_argument(MEM_FN("setGeometry") "Received NULL Geometry");
            atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "Received NULL Geometry");
        }
        freeGeometry();
        m_geometry = std::move(geometry);
        m_altitudeMode = altitudeMode;
        m_extrude = extrude;
    }

    TAK::Engine::Util::TAKErr KmlDefinition::getRawGeometry(RawData *value) NOTHROWS
    {
        value->object = m_geometry.get();
        return TE_Ok;
    }

    FeatureDefinition2::GeometryEncoding KmlDefinition::getGeomCoding() NOTHROWS
    {
        return GeomGeometry;
    }

    AltitudeMode KmlDefinition::getAltitudeMode() NOTHROWS 
    {
        return m_altitudeMode;
    }
    
    double KmlDefinition::getExtrude() NOTHROWS 
    {
        return m_extrude;
    }

    TAK::Engine::Util::TAKErr KmlDefinition::getName(const char **value) NOTHROWS
    {
        *value = m_featureName;
        return TE_Ok;
    }

    void KmlDefinition::setStyle(StylePtr_const &&style) NOTHROWS
    {
        freeStyle();
        m_style = std::move(style);
        m_styleEncoding = StyleStyle;
    }

    void KmlDefinition::setStyle(const char* styleString) NOTHROWS
    {
        freeStyle();
        m_ogrStyle = styleString;
        m_styleEncoding = StyleOgr;
    }

    FeatureDefinition2::StyleEncoding KmlDefinition::getStyleCoding() NOTHROWS
    {
        return m_styleEncoding;
    }

    TAK::Engine::Util::TAKErr KmlDefinition::getRawStyle(RawData *value) NOTHROWS
    {
        switch (m_styleEncoding)
        {
        case StyleOgr:
        {
            value->text = m_ogrStyle;
            break;
        }
        case StyleStyle:
        {
            value->object = m_style.get();
            break;
        }
        default:
            return TE_IllegalState;
        }

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr KmlDefinition::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
    {
        *value = &m_attributes;
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr KmlDefinition::get(const Feature2 **feature) NOTHROWS
    {
        m_feature.reset();
        TAKErr code = Feature_create(m_feature, *this);
        TE_CHECKRETURN_CODE(code);

        *feature = m_feature.get();

        return TE_Ok;
    }

}
