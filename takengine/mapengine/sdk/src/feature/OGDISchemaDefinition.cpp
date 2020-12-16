#include "feature/OGDISchemaDefinition.h"

#include <memory>
#include <stdexcept>

#include "ogr_feature.h"

using namespace TAK::Engine::Feature;
using namespace atakmap::feature;

#define MEM_FN( fn )    "TAK::Engine::Feature::DefaultSchemaDefinition::" fn ": "

const OGDISchemaDefinition* OGDISchemaDefinition::get()
{
	static std::unique_ptr<OGDISchemaDefinition> instance
	(new OGDISchemaDefinition);

	return instance.get();
}


OGR_SchemaDefinition::StringVector OGDISchemaDefinition::getNameFields(const char* filePath, const OGRFeatureDefn& featureDef) 	const
{
	StringVector result;
	int fieldCount(const_cast<OGRFeatureDefn&> (featureDef).GetFieldCount());

	for (int i(0); i < fieldCount; ++i)
	{
		OGRFieldDefn* fieldDef
		(const_cast<OGRFeatureDefn&> (featureDef).GetFieldDefn(i));

		if (fieldDef && fieldDef->GetType() == OFTString)
		{
            TAK::Engine::Port::String fieldName = fieldDef->GetNameRef();
			if (fieldName && (std::strstr(fieldName, "nam") || std::strstr(fieldName, "na2") || std::strstr(fieldName, "na3")))
			{
				//
				// Add the field to the end of the list unless it is exactly
				// "name".
				//

				if (std::strcmp(fieldName, "nam"))
				{
					result.push_back(fieldName);
				}
				else
				{
					result.insert(result.begin(), fieldName);
				}
			}
		}
	}

	return result;
}


bool OGDISchemaDefinition::matches(const char* filePath, const OGRFeatureDefn&) 	const
{
	if (!filePath)
	{
		throw std::invalid_argument(MEM_FN("matches")
			"Received NULL filePath");
	}

	return true;
}

