/***************************************************************************
 *  Copyright 2021 PAR Government Systems
 *
 * Unlimited Rights:
 * PAR Government retains ownership rights to this software.  The Government has Unlimited Rights
 * to use, modify, reproduce, release, perform, display, or disclose this
 * software as identified in the purchase order contract. Any
 * reproduction of computer software or portions thereof marked with this
 * legend must also reproduce the markings. Any person who has been provided
 * access to this software must be aware of the above restrictions.
 */

#include "formats/pfps/DrsSchemaDefinition.h"

#include <memory>
#include <stdexcept>

#include "ogr_feature.h"
#include "util/IO2.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;
using namespace atakmap::feature;

#define MEM_FN(fn) "TAK::Engine::Feature::DrsSchemaDefinition::" fn ": "

const DrsSchemaDefinition* DrsSchemaDefinition::get() {
    static std::unique_ptr<DrsSchemaDefinition> instance(new DrsSchemaDefinition);

    return instance.get();
}

OGR_SchemaDefinition::StringVector DrsSchemaDefinition::getNameFields(const char* filePath, const OGRFeatureDefn& featureDef) const {
    StringVector result;
    int fieldCount(const_cast<OGRFeatureDefn&>(featureDef).GetFieldCount());

    for (int i(0); i < fieldCount; ++i) {
        OGRFieldDefn* fieldDef(const_cast<OGRFeatureDefn&>(featureDef).GetFieldDefn(i));

        if (fieldDef && fieldDef->GetType() == OFTString) {
            TAK::Engine::Port::String fieldName = fieldDef->GetNameRef();
            if (fieldName && !std::strstr(fieldName, "font") && (std::strstr(fieldName, "text") || std::strstr(fieldName, "labeltext") || std::strstr(fieldName, "name") ||
                std::strstr(fieldName, "comment"))) {
                //
                // Add the field to the end of the list unless it is exactly
                // "name".
                //

                if (std::strcmp(fieldName, "text")) {
                    result.push_back(fieldName);
                } else {
                    result.insert(result.begin(), fieldName);
                }
            }
        }
    }

    return result;
}

bool DrsSchemaDefinition::matches(const char* filePath, const OGRFeatureDefn&) const {
    if (!filePath) {
        throw std::invalid_argument(MEM_FN("matches") "Received NULL filePath");
    }

    Port::String ext;
    TAKErr code = IO_getExt(ext, filePath);
    TE_CHECKRETURN_CODE(code);
    int drs = -1;
    String_compareIgnoreCase(&drs, ext, ".drs");
    return (drs == 0);
}
