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

#ifndef TAK_ENGINE_FORMATS_PFPS_DRS_SCHEMA_DEFINITION_H_INCLUDED
#define TAK_ENGINE_FORMATS_PFPS_DRS_SCHEMA_DEFINITION_H_INCLUDED

#include "feature/OGR_SchemaDefinition.h"

namespace TAK {
namespace Engine {
namespace Feature {
class ENGINE_API DrsSchemaDefinition : public atakmap::feature::OGR_SchemaDefinition {
   public:
    ~DrsSchemaDefinition() NOTHROWS {}

    static const DrsSchemaDefinition* get();

    StringVector getNameFields(const char* filePath, const OGRFeatureDefn&) const override;

    bool matches(const char* filePath, const OGRFeatureDefn&) const override;

   protected:
   private:
    DrsSchemaDefinition() {}
};
}  // namespace Feature
}  // namespace Engine
}  // namespace TAK

#endif
