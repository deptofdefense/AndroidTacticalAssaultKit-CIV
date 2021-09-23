
#include <sstream>
#include <map>
#include <libxml/xmlreader.h>
#include "model/KMZSceneInfoSpi.h"
#include "util/IO2.h"
#include "port/STLVectorAdapter.h"
#include "model/DAESceneInfoSpi.h"
#include "util/DataInput2.h"
#include "core/ProjectionFactory3.h"
#include "util/Memory.h"
#include "port/Platform.h"
#include "port/STLListAdapter.h"
#include "feature/KMLParser.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Feature;

namespace {
    bool isKMZSupportedModelFile(const char *file);
    bool isKMLFile(const char *file);
    TAKErr findKMLFiles(std::vector<TAK::Engine::Port::String> &result, const char *kmzPath) NOTHROWS;
    TAKErr parseScenesFromKML(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *kmlFile) NOTHROWS;
}

KMZSceneInfoSpi::KMZSceneInfoSpi() NOTHROWS
{}

KMZSceneInfoSpi::~KMZSceneInfoSpi() NOTHROWS {

}

int KMZSceneInfoSpi::getPriority() const NOTHROWS {
    return 1;
}

const char *KMZSceneInfoSpi::getName() const NOTHROWS {
    return "KMZ";
}

bool KMZSceneInfoSpi::isSupported(const char *path) NOTHROWS {
    // expect there to be model classes within
    std::vector<TAK::Engine::Port::String> files;
    TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> filesAdapter(files);
    IO_listFilesV(filesAdapter, path, TELFM_RecursiveFiles, isKMLFile, 1);
    if (files.empty())
        return false;
    files.clear();
    IO_listFilesV(filesAdapter, path, TELFM_RecursiveFiles, isKMZSupportedModelFile, 1);
    return !files.empty();
}

TAK::Engine::Util::TAKErr KMZSceneInfoSpi::create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {

    // sanity check
    if (!isSupported(path))
        return TE_Unsupported;
    
    std::vector<TAK::Engine::Port::String> kmlFiles;
    TAKErr code = findKMLFiles(kmlFiles, path);
    TE_CHECKRETURN_CODE(code);

    auto it = kmlFiles.begin();
    auto end = kmlFiles.end();
    while (it != end) {
        code = parseScenesFromKML(scenes, *it);
        TE_CHECKBREAK_CODE(code);
        ++it;
    }

    if (code != TE_Ok) {
        scenes.clear();
    }

    // if the scene only contains a single model, name it per the filename
    if (scenes.size() == 1u) {
        do {
            TAK::Engine::Port::Collection<SceneInfoPtr>::IteratorPtr it_scenes(nullptr, nullptr);
            code = scenes.iterator(it_scenes);
            TE_CHECKBREAK_CODE(code);;
            SceneInfoPtr scene;
            code = it_scenes->get(scene);
            TE_CHECKBREAK_CODE(code);
            code = IO_getName(scene->name, path);
            TE_CHECKBREAK_CODE(code);
        } while (false);
    }

    return code;
}

namespace {
    bool isKMZSupportedModelFile(const char *file) {
        //XXX-- KMZ supports FBX
        return SceneInfoFactory_isSupported(file, DAESceneInfoSpi::getStaticName());
    }

    bool isKMLFile(const char *file) {
        const char *ext = strrchr(file, '.');
        if (!ext)
            return false;

        if (strstr(file, "__MACOSX"))
            return false;

        int comp = -1;
        TAK::Engine::Port::String_compareIgnoreCase(&comp, ext, ".kml");
        if (comp != 0)
            return false;

        return true;
    }

    TAKErr findKMLFiles(std::vector<TAK::Engine::Port::String> &result, const char *kmzPath) NOTHROWS {
        TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> resultAdapter(result);
        TAKErr code = IO_listFilesV(resultAdapter, kmzPath, TELFM_RecursiveFiles, isKMLFile);
        return code;
    }

	TAKErr parseModel(SceneInfoPtr &scenePtr, const char *kmlPath, const char *name, const KMLModel *model) {
		
		TAKErr code(TE_Ok);
		ResourceAliasCollectionPtr resourceAliases(nullptr, nullptr);

		if (!model->Link.get())
			return TE_Unsupported;

		if (!model->Location.is_specified()) {
			Logger_log(TELL_Error, "'%s' Model '%s' missing Location or misdefined, aborting.", kmlPath, name);
			return TE_Err;
		}

		if (!model->Link.get()) {
			Logger_log(TELL_Error, "'%s' Model '%s' missing Link or misdefined, aborting.", kmlPath, name);
			return TE_Err;
		}

		if (!model->Scale.is_specified())
			Logger_log(TELL_Info, "'%s' Model '%s' missing Scale or misdefined, assuming default", kmlPath, name);

		if (!model->Orientation.is_specified())
			Logger_log(TELL_Info, "'%s' Model '%s' missing Orientation or misdefined, assuming default", kmlPath, name);

		bool altUnknown = true;
		std::pair<AltitudeMode, TAK::Engine::Core::AltitudeReference> sceneAltMode = std::make_pair(TEAM_ClampToGround, TAK::Engine::Core::AGL);

		if (model->altitudeMode.is_specified()) {
			altUnknown = false;
			switch (model->altitudeMode.value) {
			case KMLAltitudeMode_absolute:
				sceneAltMode = std::make_pair(TEAM_Absolute, TAK::Engine::Core::HAE);
				break;
			case KMLAltitudeMode_clampToGround:
				sceneAltMode = std::make_pair(TEAM_ClampToGround, TAK::Engine::Core::AGL);
				break;
			case KMLAltitudeMode_relativeToGround:
				sceneAltMode = std::make_pair(TEAM_Relative, TAK::Engine::Core::AGL);
				break;
			default:
				altUnknown = true;
				break;
			}
		}

		if (altUnknown)
			Logger_log(TELL_Info, "'%s' Model '%s' missing altitudeMode or misdefined, assuming default", kmlPath, name);

		int srid = 4326;
		TAK::Engine::Core::Projection2Ptr projPtr(nullptr, nullptr);
		code = TAK::Engine::Core::ProjectionFactory3_create(projPtr, srid);
		TE_CHECKRETURN_CODE(code);

		TAK::Engine::Port::String parentDir;
		code = IO_getParentFile(parentDir, kmlPath);
		TE_CHECKRETURN_CODE(code);

		double latRadians = model->Location.value.latitude * M_PI / 180.0;
		double metersLat = 111132.92 - 559.82 * std::cos(2 * latRadians) + 1.175 * std::cos(4 * latRadians);
		double metersLng = 111412.84 * std::cos(latRadians) - 93.5 * std::cos(3 * latRadians);

		scenePtr->location = GeoPoint2Ptr(new GeoPoint2(model->Location.value.latitude, 
			model->Location.value.longitude, 
			model->Location.value.altitude, 
			sceneAltMode.second), Memory_deleter_const<GeoPoint2>);
		scenePtr->altitudeMode = sceneAltMode.first;

		TAK::Engine::Math::Point2<double> p(0, 0, 0);
		projPtr->forward(&p, *scenePtr->location);

		std::unique_ptr<TAK::Engine::Math::Matrix2> mat(new(std::nothrow) TAK::Engine::Math::Matrix2());
		if (!mat)
			return TE_OutOfMemory;

		mat->setToIdentity();

		// Order: translate -> scale -> rotate
		mat->translate(p.x, p.y, p.z);

		mat->scale(1.0 / metersLng * model->Scale.value.x, 1.0 / metersLat * model->Scale.value.y, model->Scale.value.z);

		// XXX-- Google Earth interprets Y_UP models as mirrored in the X and Y axis. No real
		// explanation why.
		// Find a better way to find Y_UP via Assimp loader SPI
		/*if (upAxis == Y_UP) {
			info.localFrame.scale(-1, -1, 1);
		}*/

		mat->rotate(-model->Orientation.value.heading * M_PI / 180.0, 0.0f, 0.0f, 1.0f);
		mat->rotate(model->Orientation.value.roll * M_PI / 180.0, 0.0f, 1.0f, 0.0f);
		mat->rotate(model->Orientation.value.tilt * M_PI / 180.0, 1.0f, 0.0f, 0.0f);

		TE_BEGIN_TRAP() {

			std::unique_ptr<TAK::Engine::Port::STLVectorAdapter<ResourceAlias>> aliases(new TAK::Engine::Port::STLVectorAdapter<ResourceAlias>());
			for (size_t i = 0; i < model->ResourceMap.value.Alias.size(); ++i) {
				const KMLAlias &alias = model->ResourceMap.value.Alias[i];
				aliases->add({ alias.sourceHref.value.c_str(),
					alias.targetHref.value.c_str() });
			}

			std::ostringstream ss;
			ss << parentDir.get() << TAK::Engine::Port::Platform_pathSep() << model->Link->href.value;
			scenePtr->uri = ss.str().c_str();
			scenePtr->type = "KMZ";
			if (aliases->size())
				scenePtr->resourceAliases = ResourceAliasCollectionPtr(aliases.release(), Memory_free_const<TAK::Engine::Port::Collection<ResourceAlias>>);
			scenePtr->name = name;
		} TE_END_TRAP(code);
		TE_CHECKRETURN_CODE(code);

		scenePtr->srid = srid;

		scenePtr->localFrame = TAK::Engine::Math::Matrix2Ptr_const(mat.release(), Memory_deleter_const<TAK::Engine::Math::Matrix2>);

		// XXX - provide default resolution thresholds
		scenePtr->minDisplayResolution = 5.0; // 5m
		scenePtr->maxDisplayResolution = 0.0; // unlimited zoom in

		// Correct for incorrect root that some KMZ models have (Google Earth supports this)
		bool exists = false;
		IO_existsV(&exists, scenePtr->uri);
		if (!exists) {
			
			std::string subpath = model->Link->href.value;
			size_t index = subpath.find_first_of("/\\");
			if (index != std::string::npos) {
				std::ostringstream ss;
				ss << parentDir.get() << TAK::Engine::Port::Platform_pathSep() << subpath.substr(index + 1);

				IO_existsV(&exists, ss.str().c_str());
				if (!exists)
					return TE_Unsupported;

				scenePtr->uri = ss.str().c_str();
			}
		}

		return code;
	}

    TAKErr parseScenesFromKML(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *kmlFile) NOTHROWS {

        DataInput2Ptr dataPtr(nullptr, nullptr);
        TAKErr code = IO_openFileV(dataPtr, kmlFile);
        TE_CHECKRETURN_CODE(code);

		TAK::Engine::Feature::KMLParser kmlParser;
		code = kmlParser.open(*dataPtr, kmlFile);
		TE_CHECKRETURN_CODE(code);

		kmlParser.enableStore(false);
		KMLPtr<KMLContainer> container;

		do {
			code = kmlParser.step();

			if (code != TE_Ok) break;

			switch (kmlParser.position()) {
			case KMLParser::Object_begin:
				if (kmlParser.object()->get_entity() == KMLEntity_Container) {
					container = kmlParser.objectPtr().as<KMLContainer>();
				} else if (kmlParser.object()->get_entity() == KMLEntity_Placemark) {
					kmlParser.enableStore(true);
				}
				break;
			case KMLParser::Object_end:
				if (kmlParser.object()->get_entity() == KMLEntity_Placemark) {
					kmlParser.enableStore(false);
					const auto *placemark = static_cast<const KMLPlacemark *>(kmlParser.object());
					TE_BEGIN_TRAP() {
						
						if (placemark->Geometry.get() &&
							placemark->Geometry->get_entity() == KMLEntity_Model) {

							const auto *model = static_cast<const KMLModel *>(placemark->Geometry.get());

							const char *name = "";
							
							if (placemark->name.is_specified())
								name = placemark->name.value.c_str();
							if (*name == '\0' && container.get())
								name = container->name.value.c_str();
							if (*name == '\0')
								name = "uknown";

							SceneInfoPtr scenePtr = std::make_shared<SceneInfo>();
							code = parseModel(scenePtr, kmlFile, name, model);
							if (code == TE_Ok) {
								scenes.add(scenePtr);
							} else {
								//TODO-- log skipped over
							}
						}
					} TE_END_TRAP(code);
					TE_CHECKBREAK_CODE(code);
				}
				break;
			}
		} while (code == TE_Ok);

		if (code == TE_Done)
			code = TE_Ok;

		if (scenes.size() == 0)
			return TE_Unsupported;

        return code;
    }
}