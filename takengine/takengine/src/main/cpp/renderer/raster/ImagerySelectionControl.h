#pragma once


#include "port/Platform.h"
#include "util/Error.h"
#include "port/Set.h"
#include "port/String.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {

                class ENGINE_API ImagerySelectionControl
                {
                   public:
                    typedef enum
                    {
                        /** Select imagery based on filter, whose minimum resolution exceeds map resolution */
                        MinimumResolution,
                        /** Select imagery based on filter, whose maximum resolution exceeds map resolution */
                        MaximumResolution,
                        /** Select imagery based on filter, completely ignoring resolution */
                        IgnoreResolution,
                    } Mode;

                    virtual ~ImagerySelectionControl() NOTHROWS = 0;
                    virtual void setResolutionSelectMode(Mode mode) NOTHROWS = 0;
                    virtual Mode getResolutionSelectMode() NOTHROWS = 0;
                    virtual void setFilter(Port::Set<Port::String> &filter) NOTHROWS = 0;
                };

            }
        }  
    }  
}