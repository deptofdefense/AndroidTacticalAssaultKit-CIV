#include "feature/OGDIFeatureDataSource.h"

#include <cmath>
#include <cstddef>
#include <iostream>
#include <memory>
#include <stack>
#include <queue>
#include <regex>

#include "core/AtakMapView.h"
#include "feature/FeatureDefinition2.h"
#include "feature/OGR_FeatureDataSource.h"
#include "feature/ParseGeometry.h"
#include "feature/Polygon.h"
#include "feature/OGRDriverDefinition2.h"
#include "feature/OGDISchemaDefinition.h"

#include "port/String.h"
#include "port/StringBuilder.h"

#include "util/IO.h"
#include "util/IO2.h"
#include "util/Logging.h"
#include "util/Memory.h"

#include "ogr_api.h"
#include "ogr_feature.h"
#include "ogr_spatialref.h"
#include "ogrsf_frmts.h"
#include "ogr_core.h"

#include "OGR_SchemaDefinition.h"
#include "DefaultSchemaDefinition.h"

#define MEM_FN( fn )    "TAK::Engine::Feature::OGDIFeatureDataSource::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#define TAG "OGDIFeatureDataSource"

const double MAX_RESOLUTION = 0.0;

namespace 
{
	typedef std::unique_ptr<GDALDataset, void(*) (GDALDataset*)> GDAL_DatasetPtr;

	typedef std::unique_ptr<OGRCoordinateTransformation, decltype (&OGRCoordinateTransformation::DestroyCT)> OGR_CoordinateTransformationPtr;

	typedef std::unique_ptr<OGRFeature, decltype (&OGRFeature::DestroyFeature)> OGR_FeaturePtr;

	typedef std::unique_ptr<OGRSpatialReference, decltype (&OGRSpatialReference::DestroySpatialReference)> OGR_SpatialReferencePtr;

	class GeoNode
	{
		//====================================//
	public:                               //                      PUBLIC        //
										  //====================================//


		GeoNode(OGRGeometryCollection* parent)
			: parent(parent),
			childCount(parent->getNumGeometries()),
			childIndex(0)
		{ }

		//
		// The compiler-generated copy constructor, destructor, and assignment
		// operator are acceptable.
		//

		OGRGeometry*
			getNextChild()
		{
			return childIndex < childCount
				? parent->getGeometryRef(childIndex++)
				: nullptr;
		}


		//====================================//
	protected:                            //                      PROTECTED     //
										  //====================================//

										  //====================================//
	private:                              //                      PRIVATE       //
										  //====================================//


	  //==================================
	  //  PRIVATE REPRESENTATION
	  //==================================


		OGRGeometryCollection* parent;
		int childCount;
		int childIndex;
	};

	class OGDIContent 
		: public FeatureDataSource2::Content
	{
	public:
		OGDIContent(const char* filePath, std::size_t areaThreshold) NOTHROWS;
		~OGDIContent() NOTHROWS override;
	public:
		const char* getType() const NOTHROWS override;
		const char* getProvider() const NOTHROWS override;
		TAK::Engine::Util::TAKErr moveToNextFeature() NOTHROWS override;
		TAK::Engine::Util::TAKErr moveToNextFeatureSet() NOTHROWS override;
		TAK::Engine::Util::TAKErr get(FeatureDefinition2** feature) const NOTHROWS override;
		TAK::Engine::Util::TAKErr getFeatureSetName(TAK::Engine::Port::String& name) const NOTHROWS override;
		TAK::Engine::Util::TAKErr getFeatureSetVisible(bool* visible) const NOTHROWS override;
		TAK::Engine::Util::TAKErr getMinResolution(double* value) const NOTHROWS override;
		TAK::Engine::Util::TAKErr getMaxResolution(double* value) const NOTHROWS override;
		TAK::Engine::Util::TAKErr getVisible(bool* visible) const NOTHROWS override;

		bool isValid() { return m_isValid; };

	private:
		bool  openOgdi(const char* pszFilename);
		const atakmap::util::AttributeSet& getCurrentFeatureAttributes() const;
		std::size_t preprocessDataset();
		void setCurrentFeatureName();

	private:

		enum State
		{
			Feature,
			Geometry
		};

		typedef std::vector<TAK::Engine::Port::String>   StringVector;

	private:
                bool m_isValid;
                TAK::Engine::Port::String m_path;
                std::size_t m_areaThreshold;
                GDAL_DatasetPtr m_dataSource;
                int m_layerCount;
                std::size_t m_currentLevelOfDetail;
                TAK::Engine::Feature::OGRDriverDefinition2Ptr m_driver;
                int m_layerIndex;
                State m_state;
                OGRLayer* m_currentLayer;
                OGRFeatureDefn* m_currentLayerDef;
                const char* m_currentLayerName;
                TAK::Engine::Port::String m_currentFeatureSetName;
                StringVector m_layerNameFields;
                OGR_CoordinateTransformationPtr m_layerTransform;
                OGR_FeaturePtr m_currentFeature;
                std::string m_currentFeatureName;
                mutable std::unique_ptr<atakmap::util::AttributeSet> m_currentFeatureAttributes;
                std::stack<GeoNode> m_geoStack;
                OGRGeometry* m_currentGeometry;
                std::size_t m_geometryCount;
                mutable std::unique_ptr<FeatureDefinition2> m_currentDef;
	};

	class OGDIDefinition : public FeatureDefinition2
	{
	public:
		OGDIDefinition(const char* name, const atakmap::util::AttributeSet& value) NOTHROWS;
		~OGDIDefinition() NOTHROWS;

	public:
		virtual void setGeometry(GeometryPtr &&) NOTHROWS;
		virtual void setStyle(StylePtr_const &&) NOTHROWS;
		virtual void setStyle(const char* styleString) NOTHROWS;

		TAK::Engine::Util::TAKErr getRawGeometry(RawData* value) NOTHROWS override;
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
		// Feature's styling, per styling.
		TAK::Engine::Port::String m_ogrStyle;
		StylePtr_const m_style;

		//const void* bufferTail;
		atakmap::util::AttributeSet m_attributes;
		FeaturePtr_const m_feature;
	};
}

OGDIFeatureDataSource::OGDIFeatureDataSource() NOTHROWS
{}

TAKErr OGDIFeatureDataSource::parse(FeatureDataSource2::ContentPtr& content, const char* file) NOTHROWS
{
	if (!file)
	{
		atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "Received NULL filePath");
		return TE_InvalidArg;
	}

	content = FeatureDataSource2::ContentPtr(new OGDIContent(file, ComputeAreaThreshold(static_cast<unsigned int>(std::ceil(atakmap::core::AtakMapView::DENSITY)))),
		Memory_deleter_const<FeatureDataSource2::Content, OGDIContent>);

	auto* ogdiContent = dynamic_cast<OGDIContent*>(content.get());
	return (((ogdiContent != nullptr) && (ogdiContent->isValid())) ? TE_Ok : TE_Err);
}

const char* OGDIFeatureDataSource::getName() const NOTHROWS
{
	return "OGDI";
}

int OGDIFeatureDataSource::parseVersion() const NOTHROWS
{
	return 2;
}

inline
std::size_t OGDIFeatureDataSource::ComputeAreaThreshold(unsigned int DPI)
{
    return static_cast<std::size_t>(64 * std::ceil(DPI * DPI / (96.0 * 96.0)));
}

namespace
{
	OGRSpatialReference*
		createEPSG_4326()
	{
		auto* result(new OGRSpatialReference);
		OGRErr err(result->importFromEPSG(4326));

		if (err != OGRERR_NONE)
		{
			std::cerr << "\n" MEM_FN("createEPSG_4326") "importFromEPSG failed.";
		}

		return result;
	}


	std::size_t
		getDeepGeometryCount(const OGRGeometry* geometry)
	{
		std::size_t count(0);

		if (geometry && !geometry->IsEmpty())
		{
			const auto* collection
			(dynamic_cast<const OGRGeometryCollection*> (geometry));

			if (collection)
			{
				int elementCount(collection->getNumGeometries());

				for (int i(0); i < elementCount; ++i)
				{
					count += getDeepGeometryCount(collection->getGeometryRef(i));
				}
			}
			else
			{
				count = 1;
			}
		}

		return count;
	}


	inline
		OGRSpatialReference*
		getEPSG_4326()
	{
		static OGR_SpatialReferencePtr result
		(createEPSG_4326(),
			OGRSpatialReference::DestroySpatialReference);

		return result.get();
	}


	int
		getSpatialRefID(const OGRSpatialReference& spatialRef)
	{
		int result(0);

		if (!TAK::Engine::Port::String_strcasecmp(spatialRef.GetAuthorityName(nullptr), "EPSG"))
		{
			const char* value(spatialRef.GetAuthorityCode(nullptr));

			if (value)
			{
				std::istringstream strm(value);

				strm >> result;
			}
		}

		return result;
	}


	OGRCoordinateTransformation*
		getLayerTransform(OGRLayer& layer)
	{
		OGRCoordinateTransformation* result(nullptr);

		//
		// Check that the spatial reference ID is WGS84 (4326).  If not, but the
		// layer has a projection, return a coordinate transformation to WGS84.
		//

		OGRSpatialReference* spatialRef(layer.GetSpatialRef());

		if (spatialRef && getSpatialRefID(*spatialRef) != 4326)
		{
			result = OGRCreateCoordinateTransformation(spatialRef, getEPSG_4326());
		}

		return result;
	}

	OGRGeometry *
		massage(OGRGeometry *geom)
	{
		const auto *linestring = dynamic_cast<const OGRLineString *>(geom);
		if (!linestring)
			return geom;

		auto *retval = new OGRPoint();
		linestring->getPoint(0, retval);
		delete geom;
		return retval;
	}

	OGDIContent::OGDIContent(const char* filePath, std::size_t areaThreshold) NOTHROWS :
            m_path(nullptr),
	    m_isValid(false),
	    m_areaThreshold(areaThreshold),
	    m_dataSource(nullptr, nullptr),
	    m_driver(nullptr, nullptr),
            m_state(State::Geometry),
	    m_currentLevelOfDetail(0),
	    m_layerIndex(-1),
	    m_currentLayer(nullptr),
            m_currentLayerDef(nullptr),
            m_currentLayerName(nullptr),
	    m_layerTransform(nullptr, OGRCoordinateTransformation::DestroyCT),
	    m_currentFeature(nullptr, OGRFeature::DestroyFeature),
	    m_geometryCount(0)
	{
		bool success = false;

		bool isDir;
		if (TE_Ok == IO_isDirectory(&isDir, filePath) && isDir)
		{
			success = openOgdi(filePath);
		}
	}

	OGDIContent::~OGDIContent() NOTHROWS
	{}

	const char* OGDIContent::getType() const NOTHROWS
	{
		if (m_driver != nullptr)
			return m_driver->getType();
		return "OGR";
	}

	const char* OGDIContent::getProvider() const NOTHROWS
	{
		return "OGR";
	}

	TAKErr OGDIContent::get(FeatureDefinition2** feature) const NOTHROWS
	{
		std::unique_ptr<OGDIDefinition> result;

		StringBuilder nameStringBuilder;

		nameStringBuilder << m_currentFeatureName.c_str();
		if (m_state == Geometry)
		{
            nameStringBuilder << m_geometryCount;
		}

		result.reset(new OGDIDefinition(nameStringBuilder.c_str(),
			getCurrentFeatureAttributes()));
		m_currentGeometry->flattenTo2D();
		switch (m_driver->getFeatureEncoding())
		{
		case atakmap::feature::FeatureDataSource::FeatureDefinition::WKB:
		{
			OGRwkbByteOrder byteOrder(atakmap::util::ENDIAN_BYTE ? wkbNDR : wkbXDR);
			std::size_t buffSize(m_currentGeometry->WkbSize());
            array_ptr<unsigned char> buff(new unsigned char[buffSize]);

			m_currentGeometry->exportToWkb(byteOrder, buff.get());
			atakmap::feature::ByteBuffer blob(buff.get(), buff.get() + buffSize);
			result->setGeometry(GeometryPtr(atakmap::feature::parseWKB(blob), atakmap::feature::destructGeometry));
		}
		break;

		case atakmap::feature::FeatureDataSource::FeatureDefinition::WKT:
		{
			char* buff(nullptr);

			m_currentGeometry->exportToWkt(&buff);

			std::unique_ptr<char, decltype (&OGRFree)> cleanup(buff, OGRFree);

			result->setGeometry(GeometryPtr(atakmap::feature::parseWKT(buff), atakmap::feature::destructGeometry));
		}
		break;

		case atakmap::feature::FeatureDataSource::FeatureDefinition::BLOB:
            return TE_Unsupported;

		case atakmap::feature::FeatureDataSource::FeatureDefinition::GEOMETRY:
            return TE_Unsupported;
		}

		TAK::Engine::Port::String styleString;
		m_driver->getStyle(styleString,
			*m_currentFeature,
			*m_currentGeometry);

		if (styleString && styleString[0] == '@')
		{
			OGRStyleTable *styleTable = m_dataSource->GetStyleTable();
			if (styleTable)
			{
				styleTable = m_currentLayer->GetStyleTable();
			}

			if (styleTable)
			{
				const char *tableStyle = styleTable->Find(styleString);
				tableStyle = nullptr;
			}

			styleString = static_cast<const char *>(nullptr);
		}

		result->setStyle(styleString);

		m_currentDef = std::move(result);
		*feature = m_currentDef.get();
		return TE_Ok;
	}

	TAKErr OGDIContent::getFeatureSetName(TAK::Engine::Port::String& name) const NOTHROWS
	{
		name = m_currentFeatureSetName;
		return TE_Ok;
	}

	TAKErr OGDIContent::getFeatureSetVisible(bool* visible) const NOTHROWS
	{
		*visible = true;
		return TE_Ok;
	}

	TAKErr OGDIContent::getMinResolution(double* value) const NOTHROWS
	{
		*value = m_currentLevelOfDetail > 0 && m_currentLevelOfDetail < 32 ? 156543.034 / (1 << m_currentLevelOfDetail) : 0.0;
		return TE_Ok;
	}

	TAKErr OGDIContent::getMaxResolution(double* value) const NOTHROWS
	{
		*value = MAX_RESOLUTION;
		return TE_Ok;
	}

	TAKErr OGDIContent::getVisible(bool* visible) const NOTHROWS
	{
		*visible = true;
		return TE_Ok;
	}

	TAKErr OGDIContent::moveToNextFeature() NOTHROWS
	{
		bool result(false);
		bool terminate(false);             // Terminate state machine.

		try
		{
			do
			{
				switch (m_state)
				{
				case Feature:
					bool skipFeature;
					do
					{
						if (m_currentLayer)
							m_currentFeature.reset(m_currentLayer->GetNextFeature());
						else
							m_currentFeature.reset();
						if (!m_currentFeature.get())
							break;
						skipFeature = true;
						m_driver->skipFeature(&skipFeature, *m_currentFeature);
					} while (skipFeature);
					if (m_currentFeature.get())
					{
						setCurrentFeatureName();
						m_currentGeometry = m_currentFeature->GetGeometryRef();
						m_geometryCount = 0;

						if (m_currentGeometry)
						{
							// if the feature does not have any non-empty
							// geometries, skip
							if (getDeepGeometryCount(m_currentGeometry) < 1) {
								//Log.w(TAG, "Skipping empty geometry " + this->featureName);
								continue;
							}

							auto* collection
							(dynamic_cast<OGRGeometryCollection*> (m_currentGeometry));

							if (collection)
							{
								m_geoStack.push(GeoNode(collection));
								m_currentGeometry = nullptr;
								m_state = Geometry;
							}
							else
							{
								if (m_layerTransform.get())
								{
									m_currentGeometry->transform(m_layerTransform.get());
								}
								terminate = result = true;
							}
						}
					}
					else                        // Exhausted this layer's Features.
					{
						return TE_Done;
					}
					m_currentFeatureAttributes.reset();
					break;

				case Geometry:

					m_currentGeometry = m_geoStack.top().getNextChild();

					if (m_currentGeometry)
					{
						auto* collection
						(dynamic_cast<OGRGeometryCollection*> (m_currentGeometry));

						if (collection)
						{
							m_geoStack.push(GeoNode(collection));
							m_currentGeometry = nullptr;
						}
						else
						{
							// XXX - a lot of complexity to 'massage' without
							//       unique_ptr
							//currentGeometry = massage(currentGeometry);
							if (m_layerTransform.get())
							{
								m_currentGeometry->transform(m_layerTransform.get());
							}
							++m_geometryCount;
							terminate = result = true;
						}
					}
					else                    // Exhausted GeometryCollection.
					{
						m_geoStack.pop();
						if (m_geoStack.empty()) // Exhausted feature's Geometries.
						{
							m_state = Feature;
						}
					}
					break;
				}
			} while (!terminate);
		}
		catch(...)
		{
			return TE_Err;
		}
		return result ? TE_Ok : TE_Done;
	}

	TAKErr OGDIContent::moveToNextFeatureSet() NOTHROWS
	{
		try {
			do {
				if (m_currentLayer)
					m_currentLayer = nullptr;
				m_layerIndex++;
				if (m_layerIndex >= m_layerCount)
					break; // no more layers
				m_currentLayer = m_dataSource->GetLayer(m_layerIndex);
				if (m_currentLayer)
				{
					bool skipLayer = false;
					m_driver->skipLayer(&skipLayer, *m_currentLayer);
					if (skipLayer)
						continue;
					// we have the next layer and it should not be skipped, break
					break;
				}
			} while (true);

			if (m_currentLayer) {
				m_currentLayerName = m_currentLayer->GetName();
				if (this->m_driver->layerNameIsPath()) {
					m_currentFeatureSetName = m_currentLayerName;
				}
				else if (this->m_layerCount > 1) {
					TAK::Engine::Port::String filename;
					TAK::Engine::Util::IO_getName(filename, this->m_path);

					std::ostringstream strm;
					strm << filename << "/" << this->m_currentLayerName;
					m_currentFeatureSetName = strm.str().c_str();
				}
				else {
					TAK::Engine::Util::IO_getName(m_currentFeatureSetName, this->m_path);
				}

				m_currentLayerDef = m_currentLayer->GetLayerDefn();

				const atakmap::feature::OGR_SchemaDefinition* schema
				(atakmap::feature::OGR_SchemaDefinition::getSchema
				(m_path, *m_currentLayerDef));

				if (!schema)
				{
					schema = TAK::Engine::Feature::OGDISchemaDefinition::get();
				}

				m_layerNameFields = schema->getNameFields(m_path, *m_currentLayerDef);
				m_layerTransform.reset(getLayerTransform(*m_currentLayer));
				m_currentLayer->ResetReading();

				std::size_t pointCount(0);
				std::size_t featureCount(0);

				m_currentLevelOfDetail = 0;

				for (OGR_FeaturePtr feature(m_currentLayer->GetNextFeature(),
					OGRFeature::DestroyFeature);
					feature.get();
					feature.reset(m_currentLayer->GetNextFeature()))
				{
					OGREnvelope datasetMBR;
					bool haveDatasetMBR(false);
					OGREnvelope layerMBR;
					bool haveLayerMBR(false);
					//
					// Scan the layer to determine its MBR.
					//
					bool skipFeature = false;
					bool skipPoint = false;
					m_driver->skipFeature(&skipFeature, *feature);
					if (!skipFeature)
					{
						OGRGeometry* geometry(feature->GetGeometryRef());

						if (geometry && !geometry->IsEmpty())
						{
							OGREnvelope envelope;

							geometry->getEnvelope(&envelope);
							if (!haveLayerMBR)
							{
								layerMBR = envelope;
								haveLayerMBR = true;
							}
							else
							{
								layerMBR.MinX = std::min(layerMBR.MinX,
									envelope.MinX);
								layerMBR.MinY = std::min(layerMBR.MinY,
									envelope.MinY);
								layerMBR.MaxX = std::max(layerMBR.MaxX,
									envelope.MaxX);
								layerMBR.MaxY = std::max(layerMBR.MaxY,
									envelope.MaxY);
							}
							switch (geometry->getGeometryType())
							{
							case wkbPoint:
							case wkbPoint25D:
								++pointCount;
								skipPoint = true;
								break;
							default:                              break;
							}
							featureCount += getDeepGeometryCount(geometry);
						}
					}

					if (haveLayerMBR && !skipPoint)
					{
						if (m_layerTransform.get())
						{
							m_layerTransform->Transform(1, &layerMBR.MinX, &layerMBR.MinY);
							m_layerTransform->Transform(1, &layerMBR.MaxX, &layerMBR.MaxY);

							//
							// The pre-transform projection may not have been geodetic,
							// so the post-transform min values may not be less than the
							// max values.
							//

							if (!haveDatasetMBR)
							{
								datasetMBR.MinX = std::min(layerMBR.MinX,
									layerMBR.MaxX);
								datasetMBR.MinY = std::min(layerMBR.MinY,
									layerMBR.MaxY);
								datasetMBR.MaxX = std::max(layerMBR.MinX,
									layerMBR.MaxX);
								datasetMBR.MaxY = std::max(layerMBR.MinY,
									layerMBR.MaxY);
								haveDatasetMBR = true;
							}
							else
							{
								datasetMBR.MinX = std::min(datasetMBR.MinX,
									std::min(layerMBR.MinX,
										layerMBR.MaxX));
								datasetMBR.MinY = std::min(datasetMBR.MinY,
									std::min(layerMBR.MinY,
										layerMBR.MaxY));
								datasetMBR.MaxX = std::max(datasetMBR.MaxX,
									std::max(layerMBR.MinX,
										layerMBR.MaxX));
								datasetMBR.MaxY = std::max(datasetMBR.MaxY,
									std::max(layerMBR.MinY,
										layerMBR.MaxY));
							}
						}
						else if (!haveDatasetMBR)
						{
							datasetMBR = layerMBR;
							haveDatasetMBR = true;
						}
						else
						{
							datasetMBR.MinX = std::min(datasetMBR.MinX, layerMBR.MinX);
							datasetMBR.MinY = std::min(datasetMBR.MinY, layerMBR.MinY);
							datasetMBR.MaxX = std::max(datasetMBR.MaxX, layerMBR.MaxX);
							datasetMBR.MaxY = std::max(datasetMBR.MaxY, layerMBR.MaxY);
						}
					}

					if (!skipPoint)
					{
						std::size_t levelOfDetail = atakmap::feature::OGR_FeatureDataSource::ComputeLevelOfDetail(m_areaThreshold, datasetMBR);
						if (m_currentLevelOfDetail == 0 || levelOfDetail < m_currentLevelOfDetail)
							m_currentLevelOfDetail = levelOfDetail;
					}

					if (featureCount > 1000)
						break;
				}

				if (m_currentLevelOfDetail == 0)
					m_currentLevelOfDetail = 8;

				m_currentLayer->ResetReading();
				m_state = Feature;
			}
		}
		catch(...)
		{
			return TE_Err;
		}
		return (m_layerIndex < m_layerCount) ? TE_Ok : TE_Done;
	}

	const atakmap::util::AttributeSet& OGDIContent::getCurrentFeatureAttributes() const
	{
		if (!m_currentFeatureAttributes.get())
		{
			int fieldCount(m_currentLayerDef->GetFieldCount());
			std::unique_ptr<atakmap::util::AttributeSet> attributes(new atakmap::util::AttributeSet);

			for (int i(0); i < fieldCount; ++i)
			{
				OGRFieldDefn* fieldDef(m_currentLayerDef->GetFieldDefn(i));

				if (fieldDef && m_currentFeature->IsFieldSet(i))
				{
					const char* attrName(fieldDef->GetNameRef());

					switch (fieldDef->GetType())
					{
					case OFTDate:
					case OFTTime:
					case OFTDateTime:
					case OFTString:

						attributes->setString(attrName,
							m_currentFeature->GetFieldAsString(i));
						break;

					case OFTInteger:

						attributes->setInt(attrName,
							m_currentFeature->GetFieldAsInteger(i));
						break;

					case OFTInteger64:

						attributes->setLong(attrName,
							m_currentFeature->GetFieldAsInteger64(i));
						break;

					case OFTReal:

						attributes->setDouble(attrName,
							m_currentFeature->GetFieldAsDouble(i));
						break;

					case OFTBinary:
					{
						int length(0);
						unsigned char* start
						(m_currentFeature->GetFieldAsBinary(i, &length));

						attributes->setBlob(attrName,
							std::make_pair(start, start + length));
					}
					break;

					case OFTStringList:
					{
#if 0
						const char* const* head
						(currentFeature->GetFieldAsStringList(i));
						const char* const* tail(head);
#else
						const char* const *head = m_currentFeature->GetFieldAsStringList(i);
						const char* const *tail = head;
#endif

						while (*tail)
						{
							++tail;
					}
						attributes->setStringArray(attrName,
							std::make_pair(head, tail));
					}
					break;

					case OFTIntegerList:
					{
						int length(0);
						const int* start
						(m_currentFeature->GetFieldAsIntegerList(i, &length));

						attributes->setIntArray(attrName,
							std::make_pair(start,
								start + length));
					}
					break;

					case OFTInteger64List:
					{
						int length(0);
						const int64_t* start
							// XXX--
#ifdef __APPLE__
							(reinterpret_cast<const int64_t *>(currentFeature->GetFieldAsInteger64List(i, &length)));
#else
							(m_currentFeature->GetFieldAsInteger64List(i, &length));
#endif

						attributes->setLongArray(attrName,
							std::make_pair(start,
								start + length));
					}
					break;

					case OFTRealList:
					{
						int length(0);
						const double* start
						(m_currentFeature->GetFieldAsDoubleList(i, &length));

						attributes->setDoubleArray(attrName,
							std::make_pair(start,
								start + length));
					}
					break;

					default:

						std::cerr << "\nUnexpected OGRFieldType: "
							<< fieldDef->GetType();
					}
				}
			}

			m_currentFeatureAttributes = std::move(attributes); // Transfer ownership.
		}

		return *m_currentFeatureAttributes;
	}

	bool OGDIContent::openOgdi(const char* pszFilename)
	{
        StringBuilder path;
        path << "gltp:/vrf/";
		std::string filePath(pszFilename);
		std::replace(filePath.begin(), filePath.end(), '\\', '/');
		path << filePath.c_str();

        m_path = path.c_str();

		m_dataSource = GDAL_DatasetPtr(static_cast<GDALDataset*>
			(GDALOpenEx(m_path,
				GDAL_OF_VECTOR | GDAL_OF_READONLY | GDAL_OF_VERBOSE_ERROR | GDAL_OF_INTERNAL,
				nullptr, nullptr, nullptr)),
			[](GDALDataset * ds) { GDALClose(static_cast<GDALDatasetH> (ds)); });

		if (!m_dataSource.get())
		{
			return false;
		}

		m_layerCount = m_dataSource->GetLayerCount();

		if (m_layerCount < 1)
		{
			return false;
		}

		const char* driverName(m_dataSource->GetDriver()->GetDescription());

		TAK::Engine::Feature::OGRDriverDefinition2_create(m_driver, m_path, driverName);

		if (!preprocessDataset())
		{
			std::cerr << "\nNo valid features found in file: " << m_path;
			m_layerIndex = m_layerCount;        // Nothing to see here, move along.
		}

		m_isValid = true;

		return true;
	}

	std::size_t OGDIContent::preprocessDataset()
	{
		std::size_t pointCount(0);
		std::size_t featureCount(0);

		//
		// Iterate over the layers in the dataset.
		//

		for (int i(0); i < m_layerCount; ++i)
		{
			OGRLayer* layer(m_dataSource->GetLayer(i));

			bool skipLayer = !layer;
			if (!skipLayer)
			{
				// we've got a layer to process, see if it should be skipped
				m_driver->skipLayer(&skipLayer, *layer);
			}
			if (!skipLayer)
			{
				layer->ResetReading();

				OGREnvelope layerMBR;

				for (OGR_FeaturePtr feature(layer->GetNextFeature(),
					OGRFeature::DestroyFeature);
					feature.get();
					feature.reset(layer->GetNextFeature()))
				{
					//
					// Scan the layer to determine its MBR.
					//
					bool skipFeature = false;
					m_driver->skipFeature(&skipFeature, *feature);
					if (!skipFeature)
					{
						OGRGeometry* geometry(feature->GetGeometryRef());

						if (geometry && !geometry->IsEmpty())
						{
							switch (geometry->getGeometryType())
							{
							case wkbPoint:
							case wkbPoint25D:     ++pointCount;   break;
							default:                              break;
							}
							featureCount += getDeepGeometryCount(geometry);
						}
					}
				}
			}
		}

		return featureCount;
	}

	void OGDIContent::setCurrentFeatureName()
	{
		m_currentFeatureName.clear();

		if (m_currentFeature->GetFieldCount())
		{
			StringVector::const_iterator end(m_layerNameFields.end());

			for (StringVector::const_iterator iter(m_layerNameFields.begin());
				m_currentFeatureName.empty() && iter != end;
				++iter)
			{
				//
				// GetFieldIndex returns -1 for an unknown field.
				// GetFieldDefn returns NULL for an invalid index.
				//

				int index(m_currentLayerDef->GetFieldIndex(*iter));

				if (index >= 0)
				{
					OGRFieldDefn* fieldDef(m_currentLayerDef->GetFieldDefn(index));

					if (fieldDef && fieldDef->GetType() == OFTString)
					{
						m_currentFeatureName = m_currentFeature->GetFieldAsString(index);
					}
				}
			}
		}

		if (m_currentFeatureName.empty() || m_currentFeatureName.find("UNK") != std::string::npos)
			m_currentFeatureName = "";
		else
		{
			m_currentFeatureName.erase(m_currentFeatureName.find_last_not_of(" \t\n\r\f\v") + 1);
		}

		//if (!m_currentFeatureName)
		//{
		//	std::string layerName(m_currentLayer->GetName());
		//	std::size_t pathSeparatorIdx = layerName.find_last_of('/');
		//	while (pathSeparatorIdx && pathSeparatorIdx != std::string::npos)
		//	{
		//		// if the path separator character was escaped, find the previous
		//		if (pathSeparatorIdx && layerName[pathSeparatorIdx - 1] == '\\')
		//			pathSeparatorIdx = layerName.find_last_of('/', pathSeparatorIdx - 1);
		//		else
		//			break;
		//	}

		//	// if a path separator character is present, take the substring
		//	// starting with the character following the separator
		//	if (pathSeparatorIdx != std::string::npos)
		//		layerName = layerName.substr(pathSeparatorIdx + 1);

		//	std::ostringstream strm;

		//	strm << layerName << "."
		//		<< m_currentFeature->GetFID();
		//	m_currentFeatureName = strm.str().c_str();
		//}
	}

	OGDIDefinition::OGDIDefinition(const char* name, const atakmap::util::AttributeSet& value) NOTHROWS
		: m_featureName(name), m_attributes(value), m_styleEncoding(StyleStyle), m_geometry(nullptr, nullptr), m_ogrStyle(nullptr), m_style(nullptr, nullptr), m_feature(nullptr, nullptr)
	{
	}

	OGDIDefinition::~OGDIDefinition() NOTHROWS
	{
		freeGeometry();
		freeStyle();
	}

	void OGDIDefinition::freeGeometry()
	{
		m_geometry.reset();
	}


	void OGDIDefinition::freeStyle()
	{
		m_ogrStyle = nullptr;
		m_style.reset();
	}

	void OGDIDefinition::setGeometry(GeometryPtr &&geometry) NOTHROWS
	{
		if (!geometry.get())
		{
			//throw std::invalid_argument(MEM_FN("setGeometry") "Received NULL Geometry");
			atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "Received NULL Geometry");
		}
		freeGeometry();
		m_geometry = std::move(geometry);
	}

	TAK::Engine::Util::TAKErr OGDIDefinition::getRawGeometry(RawData *value) NOTHROWS
	{
		value->object = m_geometry.get();
		return TE_Ok;
	}

	FeatureDefinition2::GeometryEncoding OGDIDefinition::getGeomCoding() NOTHROWS
	{
		return GeomGeometry;
	}

	AltitudeMode OGDIDefinition::getAltitudeMode() NOTHROWS 
	{
		return AltitudeMode::TEAM_ClampToGround;
	}

	double OGDIDefinition::getExtrude() NOTHROWS 
	{
		return 0.0;
	}

	TAK::Engine::Util::TAKErr OGDIDefinition::getName(const char **value) NOTHROWS
	{
		*value = m_featureName;
		return TE_Ok;
	}

	void OGDIDefinition::setStyle(StylePtr_const &&style) NOTHROWS
	{
		freeStyle();
		m_style = std::move(style);
		m_styleEncoding = StyleStyle;
	}

	void OGDIDefinition::setStyle(const char* styleString) NOTHROWS
	{
		freeStyle();
		m_ogrStyle = styleString;
		m_styleEncoding = StyleOgr;
	}

	FeatureDefinition2::StyleEncoding OGDIDefinition::getStyleCoding() NOTHROWS
	{
		return m_styleEncoding;
	}

	TAK::Engine::Util::TAKErr OGDIDefinition::getRawStyle(RawData *value) NOTHROWS
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

	TAK::Engine::Util::TAKErr OGDIDefinition::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
	{
		*value = &m_attributes;
		return TE_Ok;
	}

	TAK::Engine::Util::TAKErr OGDIDefinition::get(const Feature2 **feature) NOTHROWS
	{
		m_feature.reset();
		TAKErr code = Feature_create(m_feature, *this);
		TE_CHECKRETURN_CODE(code);

		*feature = m_feature.get();

		return TE_Ok;
	}
}