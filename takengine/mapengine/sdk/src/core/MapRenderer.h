#ifndef TAK_ENGINE_CORE_MAPRENDERER_H_INCLUDED
#define TAK_ENGINE_CORE_MAPRENDERER_H_INCLUDED

#include "core/Control.h"
#include "core/Layer2.h"
#include "core/RenderContext.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API MapRenderer
            {
            public :
                enum DisplayMode
                {
                    Flat,
                    Globe,
                };
                enum DisplayOrigin
                {
                    UpperLeft,
                    LowerLeft,
                };
                enum InverseMode
                {
                    RayCast,
                    Transform,
                };
                enum InverseResult
                {
                    None,
                    Transformed,
                    GeometryModel,
                    SurfaceMesh,
                    TerrainMesh,
                };
                enum InverseHints
                {
                    IgnoreTerrainMesh = 0x01,
                    IgnoreSurfaceMesh = 0x02,
                };
                enum CameraCollision
                {
                    Ignore,
                    AdjustCamera,
                    AdjustFocus,
                    Abort,
                };
            public :
                class OnControlsChangedListener;
            public :
                virtual ~MapRenderer() NOTHROWS = 0;
            public :
                /**
                 * Registers the specified control for the specified layer.
                 * 
                 * @param layer A layer
                 * @param ctrl  The control
                 */
                virtual Util::TAKErr registerControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS = 0;
                /**
                 * Unregisters the specified control for the specified layer.
                 * 
                 * @param layer A layer
                 * @param ctrl  The control
                 */
                virtual Util::TAKErr unregisterControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS = 0;
                /**
                 * Invokes the specified visitor on the specified control for the specified
                 * layer. The visitor <B>MAY NOT</B> raise an exception during its
                 * invocation. If invoked, the visitor's invocation is always completed
                 * before this method returns.
                 * 
                 * <P>Client code should only interact with the control during the
                 * invocation of the visitor. Caching the reference to the control and
                 * attempting to use it outside of the invocation may lead to undefined
                 * results.
                 * 
                 * @param layer     The layer 
                 * @param visitor   The visitor
                 * @param ctrlClazz The class that the control derives from
                 * 
                 * @return  <code>true</code> if the control could be found and the visitor
                 *          was invoked, <code>false</code> otherwise.
                 */
                //<T extends MapControl> boolean visitControl(Layer2 layer, Visitor<T> visitor, Class<T> ctrlClazz);
                virtual Util::TAKErr visitControls(bool *visited, void *opaque, Util::TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer, const char *type) NOTHROWS = 0;
                /**
                 * Invokes the specified visitor on the controls for the specified layer.
                 * The visitor <B>MAY NOT</B> raise an exception during its invocation. If
                 * invoked, the visitor's invocation is always completed before this method
                 * returns.
                 * 
                 * <P>Client code should only interact with the controls during the
                 * invocation of the visitor. Caching the reference to the controls and
                 * attempting to use any outside of the invocation may lead to undefined
                 * results.
                 * 
                 * @param layer     The layer 
                 * @param visitor   The visitor
                 * 
                 * @return  <code>true</code> if controls for the layer were available and
                 *          the visitor was invoked, <code>false</code> otherwise.
                 */
                virtual Util::TAKErr visitControls(bool *visited, void *opaque, Util::TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer) NOTHROWS = 0;
                /**
                 * Invokes the specified visitor on the controls across all layers that have
                 * had controls registered. The visitor <B>MAY NOT</B> raise an exception
                 * during its invocation. If invoked, the visitor's invocation is always
                 * completed before this method returns.
                 * 
                 * <P>Client code should only interact with the controls during the
                 * invocation of the visitor. Caching the reference to the controls and
                 * attempting to use any outside of the invocation may lead to undefined
                 * results.
                 * 
                 * @param visitor   The visitor
                 *
                 * @return  The first non-TE_Ok code returned by the visitor or TE_Ok if all controls visited
                 */
                virtual Util::TAKErr visitControls(void *opaque, Util::TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl)) NOTHROWS = 0;

                virtual Util::TAKErr addOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS = 0;
                virtual Util::TAKErr removeOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS = 0;

                virtual RenderContext &getRenderContext() const NOTHROWS = 0;
            };

            /**
             * Callback interface providing notification when controls are added or
             * removed on a {@link Layer2}.
             * 
             * <P>Client code that is interested in receiving such notifications should
             * use registration and unregistration for state information purposes only;
             * the {@link MapRenderer#visitControl(Layer2, Visitor, Class)} and
             * {@link MapRenderer#visitControls(Layer2, Visitor)} methods should still
             * be used to safely access controls asynchronously.
             * 
             * @author Developer
             */
            class ENGINE_API MapRenderer::OnControlsChangedListener
            {
            public :
                virtual ~OnControlsChangedListener() NOTHROWS = 0;
            public :
                /**
                 * Invoked when a control is registered.
                 * 
                 * @param layer The layer the control is registered against.
                 * @param ctrl  The control
                 */
                virtual Util::TAKErr onControlRegistered(const Layer2 &layer, const Control &ctrl) NOTHROWS = 0;
                /**
                 * Invoked when a control is unregistered.
                 * 
                 * @param layer The layer the control was previously registered against.
                 * @param ctrl  The control
                 */
                virtual Util::TAKErr onControlUnregistered(const Layer2 &layer, const Control &ctrl) NOTHROWS = 0;
            };
        }
    }
}

#endif
