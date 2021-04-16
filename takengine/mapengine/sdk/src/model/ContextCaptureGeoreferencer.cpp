
#include <string>
#include <sstream>
#include <regex>
#include <libxml/xmlreader.h>
#include "model/ContextCaptureGeoreferencer.h"
#include "util/IO2.h"
#include "core/GeoPoint2.h"
#include "formats/wmm/GeomagnetismHeader.h"
#include "util/Memory.h"
#include "util/DataInput2.h"
#include "core/ProjectionFactory3.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;
using namespace TAK::Engine;

namespace {
    TAKErr getDatasetDir(Port::String &outPath, const char *objPath) NOTHROWS;
    TAKErr parseModelmetadata(Port::String &version, Port::String &srs, Port::String &srsOrigin, xmlTextReaderPtr reader) NOTHROWS;

	static int inputRead(void *context, char *buffer, int len) {
		auto *input = static_cast<DataInput2 *>(context);
		size_t numRead = 0;
		input->read(reinterpret_cast<uint8_t *>(buffer), &numRead, len);
		return static_cast<int>(numRead);
	}

	static int inputClose(void * context) {
		// nothing
		return 0;
	}
}

ContextCaptureGeoreferencer::ContextCaptureGeoreferencer() NOTHROWS
{}

ContextCaptureGeoreferencer::~ContextCaptureGeoreferencer() NOTHROWS
{}

TAK::Engine::Util::TAKErr ContextCaptureGeoreferencer::locate(SceneInfo &sceneInfo) NOTHROWS {
    Port::String path;
    TAKErr code = locateMetadataFile(path, sceneInfo.uri);
    TE_CHECKRETURN_CODE(code);

    if (path == "")
        return TE_Unsupported;

	DataInput2Ptr dataPtr(nullptr, nullptr);
	code = IO_openFileV(dataPtr, path);
	TE_CHECKRETURN_CODE(code);

	xmlTextReaderPtr xmlReader = xmlReaderForIO(inputRead, inputClose, dataPtr.get(), path.get(), nullptr, 0);
    if (!xmlReader) {
        return TE_Unsupported;
    }
    
    /*
        <ModelMetadata version="1">
            <!--Spatial Reference System-->
            <SRS>ENU:35.76484,-120.76987</SRS>
            <!--Origin in Spatial Reference System-->
            <SRSOrigin>0,0,0</SRSOrigin>
            <Texture>
                <ColorSource>Visible</ColorSource>
            </Texture>
        </ModelMetadata>
    */

    int readResult;
    Port::String version;
    Port::String srs;
    Port::String srsOrigin;

    while ((readResult = xmlTextReaderRead(xmlReader)) == 1) {

        const xmlChar *name = xmlTextReaderConstName(xmlReader);
        if (xmlStrEqual(name, BAD_CAST "ModelMetadata")) {
            code = parseModelmetadata(version, srs, srsOrigin, xmlReader);
            TE_CHECKBREAK_CODE(code);
        }
    }

    xmlTextReaderClose(xmlReader);

    if (version == "" || srs == "")
        return TE_Unsupported;

    long v = std::strtol(version.get(), nullptr, 10);
    if (v != 1)
        return TE_InvalidArg;

    TE_BEGIN_TRAP() {
        std::regex rex("ENU\\:[\\+\\-]?\\d+(\\.\\d+)?\\,\\s*[\\+\\-]?\\d+(\\.\\d+)?");
        std::string subject = srs.get();
        std::smatch match;
        if (!std::regex_search(subject, match, rex)) {
            return TE_InvalidArg;
        }
    
        std::string srsStr = srs.get();
        size_t comma = srsStr.find_first_of(',', 4);
        std::string first = srsStr.substr(4, comma - 4);
        std::string second = srsStr.substr(comma + 1, srsStr.length() - comma - 1);

        char *end = nullptr;
        double lat = strtod(first.c_str(), &end);
        if (end == first.c_str())
            return TE_InvalidArg;

        end = nullptr;
        double lng = strtod(second.c_str(), &end);
        if (end == second.c_str())
            return TE_InvalidArg;

        TAK::Engine::Core::GeoPoint2 origin(lat, lng);

		std::regex rex2("[\\+\\-]?\\d+(\\.\\d+)?\\,[\\+\\-]?\\d+(\\.\\d+)?\\,[\\+\\-]?\\d+(\\.\\d+)?");
		std::string subject2 = srsOrigin.get();
		std::smatch match2;

		TAK::Engine::Math::Point2<double> tx(0, 0, 0);
		if (std::regex_search(subject2, match2, rex2)) {
			std::string srs_origin_str = srsOrigin.get();
			size_t comma_origin = srs_origin_str.find_first_of(',');
			std::string first_origin = srs_origin_str.substr(0, comma_origin);
			size_t comma2 = srs_origin_str.find_first_of(',', comma_origin + 1);
			std::string second_origin = srs_origin_str.substr(comma_origin + 1, comma2 - comma_origin - 1);
			std::string third = srs_origin_str.substr(comma2 + 1, srs_origin_str.size() - comma2 - 1);
			tx.x = strtod(first_origin.c_str(), nullptr);
			tx.y = strtod(second_origin.c_str(), nullptr);
			tx.z = strtod(third.c_str(), nullptr);
		}

		TAK::Engine::Math::Point2<double> p(0, 0, 0);

		int srid = 4326;
		TAK::Engine::Core::Projection2Ptr projPtr(nullptr, nullptr);
		code = TAK::Engine::Core::ProjectionFactory3_create(projPtr, srid);
		TE_CHECKRETURN_CODE(code);

		code = projPtr->forward(&p, origin);
		TE_CHECKRETURN_CODE(code);

		double rlat = origin.latitude * M_PI / 180.0;

		double metersLng = 111412.84 * cos(rlat) - 93.5 * cos(3 * rlat);;
		double metersLat = 111132.92 - 559.82 * cos(2 * rlat)
			+ 1.175 * cos(4 * rlat);

        std::unique_ptr<Math::Matrix2> localFrame(new Math::Matrix2());
        localFrame->translate(p.x, p.y, p.z);
		localFrame->scale(1.0 / metersLng, 1.0 / metersLat, 1.0);
		localFrame->translate(tx.x, tx.y, tx.z);

        sceneInfo.localFrame = Math::Matrix2Ptr_const(localFrame.release(), Memory_deleter_const<Math::Matrix2>);
        sceneInfo.location = Core::GeoPoint2Ptr(new Core::GeoPoint2(lat, lng, 0.0, Core::AltitudeReference::HAE), Memory_deleter_const<Core::GeoPoint2>);
        sceneInfo.altitudeMode = Feature::TEAM_Absolute;
		sceneInfo.srid = srid;

    } TE_END_TRAP(code);
    TE_CHECKRETURN_CODE(code);

    return TE_Ok;
}

TAKErr ContextCaptureGeoreferencer::locateMetadataFile(Port::String &outPath, const char *objPath) NOTHROWS {
	Port::String datasetDir;
	TAKErr code = getDatasetDir(datasetDir, objPath);
	TE_CHECKRETURN_CODE(code);

	if (datasetDir == "")
		return TE_Unsupported;

	std::ostringstream ss;
	TE_BEGIN_TRAP() {
		ss << datasetDir << "/" << "metadata.xml";
	} TE_END_TRAP(code);
	TE_CHECKRETURN_CODE(code);

	return IO_getAbsolutePath(outPath, ss.str().c_str());
}

TAKErr ContextCaptureGeoreferencer::getDatasetName(Port::String &outName, const char *objPath) NOTHROWS {
	Port::String datasetDir;
	TAKErr code = getDatasetDir(datasetDir, objPath);
	TE_CHECKRETURN_CODE(code);

	if (datasetDir == "")
		return TE_Unsupported;

	return IO_getName(outName, datasetDir.get());
}

TAKErr TAK::Engine::Model::ContextCapture_resolution(double *value, const std::size_t levelOfDetail) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (levelOfDetail < 16u)
        *value = 1 << (16u - levelOfDetail);
    else if (levelOfDetail == 16u)
        *value = 1.0;
    else // levelOfDetail > 16
        *value = 1.0 / (double)(1 << (levelOfDetail - 16));
    return TE_Ok;
}
TAKErr TAK::Engine::Model::ContextCapture_levelOfDetail(std::size_t *value, const double resolution, const int round) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (resolution < 0.0)
        return TE_InvalidArg;

    double clod = 16.0 - (log(resolution) / log(2.0));
    if (clod < 0)
        *value = 0u;
    else if (round < 0)
        *value = (std::size_t)clod;
    else if (round > 0)
        *value = (std::size_t)ceil(clod);
    else
        *value = (std::size_t)(clod + 0.5);
    return TE_Ok;
}

namespace {
    

    TAKErr getDatasetDir(Port::String &outPath, const char *objPath) NOTHROWS {

        Port::String f;
        TAKErr code = IO_getParentFile(f, objPath);
        TE_CHECKRETURN_CODE(code);

        if (f == "")
            return TE_Unsupported;
        
		Port::String fName;
        code = IO_getName(fName, f);
        TE_CHECKRETURN_CODE(code);

        TE_BEGIN_TRAP() {
            std::regex rex("Tile_[\\+\\-]\\d+_[\\+\\-]\\d+");
            std::string subject = fName.get();
            std::smatch match;
            if (!std::regex_search(subject, match, rex)) {
                return TE_Unsupported;
            }
        } TE_END_TRAP(code);
        TE_CHECKRETURN_CODE(code);

        code = IO_getParentFile(f, f);
        TE_CHECKRETURN_CODE(code);
		// parent file may be "Data" for unsegmented or some arbitrary name for segmented
		code = IO_getParentFile(f, f);
		TE_CHECKRETURN_CODE(code);

        std::string metadataPath;
        TE_BEGIN_TRAP() {
            std::ostringstream ss;
            ss << f << "/" << "metadata.xml";
            metadataPath = ss.str();
        } TE_END_TRAP(code);
        TE_CHECKRETURN_CODE(code);

        bool exists = false;
        code = IO_existsV(&exists, metadataPath.c_str());
        TE_CHECKRETURN_CODE(code);

        if (!exists)
            return TE_Unsupported;

        outPath = f;
        return code;
    }

    const xmlChar *readNodeNodeText(xmlTextReaderPtr xmlReader) {

        if (xmlTextReaderRead(xmlReader) != 1) {
            return nullptr;
        }

        int nodeType = xmlTextReaderNodeType(xmlReader);
        if (nodeType != XML_READER_TYPE_TEXT &&
            nodeType != XML_READER_TYPE_SIGNIFICANT_WHITESPACE &&
            nodeType != XML_READER_TYPE_END_ELEMENT) {
            return nullptr;
        }

        return xmlTextReaderConstValue(xmlReader);
    }

    TAKErr parseModelmetadata(Port::String &version, Port::String &srs, Port::String &srsOrigin, xmlTextReaderPtr xmlReader) NOTHROWS {
        
        if (xmlTextReaderAttributeCount(xmlReader) > 0) {
            xmlChar *versionVal = xmlTextReaderGetAttribute(xmlReader, BAD_CAST "version");
            if (versionVal) {
                version = (const char *)versionVal;
                xmlFree(versionVal);
            }
        }

        do {
            if (xmlTextReaderRead(xmlReader) == -1)
                return TE_IllegalState;

            int nodeType = xmlTextReaderNodeType(xmlReader);
            const xmlChar *inNode;

            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
                inNode = xmlTextReaderConstName(xmlReader);
                if (xmlStrEqual(inNode, BAD_CAST "SRS")) {
                    const char *text = (const char *)readNodeNodeText(xmlReader);
                    if (!text)
                        return TE_InvalidArg;
                    srs = text;
                }
                else if (xmlStrEqual(inNode, BAD_CAST "SRSOrigin")) {
                    const char *text = (const char *)readNodeNodeText(xmlReader);
                    if (!text)
                        return TE_InvalidArg;
                    srsOrigin = text;
                }
            }

        } while (xmlTextReaderDepth(xmlReader) > 0);

        return TE_Ok;
    }
}
