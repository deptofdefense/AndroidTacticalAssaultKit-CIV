////============================================================================
////
////    FILE:           OGDISchemaDefinition.h
////
////    DESCRIPTION:    Concrete singleton OGR/OGDI schema definition class.
////
////    AUTHOR(S):      rob             rob_irving@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      May 9, 2019   rob             Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2019 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

#ifndef TAK_ENGINE_FEATURE_OGDI_SCHEMA_DEFINITION_H_INCLUDED
#define TAK_ENGINE_FEATURE_OGDI_SCHEMA_DEFINITION_H_INCLUDED

#include "feature/OGR_SchemaDefinition.h"

namespace TAK
{
	namespace Engine
	{
		namespace Feature
		{
			class OGDISchemaDefinition
				: public atakmap::feature::OGR_SchemaDefinition
			{
			public:
				~OGDISchemaDefinition() NOTHROWS
				{ }

				static const OGDISchemaDefinition* get();

				StringVector getNameFields(const char* filePath, const OGRFeatureDefn&) const override;

				bool matches(const char* filePath, const OGRFeatureDefn&) const override;
			protected:
			private:
				OGDISchemaDefinition()
				{ }
			};
		}
	}
}

#endif
