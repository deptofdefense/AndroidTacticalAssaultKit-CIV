#ifndef TAK_ENGINE_CORE_MULTILAYER2_H_INCLUDED
#define TAK_ENGINE_CORE_MULTILAYER2_H_INCLUDED

#include <vector>

#include "core/AbstractLayer2.h"
#include "core/Layer2.h"
#include "port/Collection.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            /**
             * A layer composed of zero or more child layers. Child layers may be
             * programmatically added, removed and reordered.
             *
             * @author Developer
             */
            class ENGINE_API MultiLayer2 : public AbstractLayer2
            {
            public :
                class ENGINE_API LayersChangedListener;
            private :
                class VisibilityUpdater;
            public :
                /**
                 * Creates a new instance with the specified name
                 *
                 * @param name  The layer name
                 */
                MultiLayer2(const char *name) NOTHROWS;
                ~MultiLayer2() NOTHROWS;
            public :
                /**
                 * Adds the specified layer.
                 *
                 * @param layer The layer to be added
                 */
                Util::TAKErr addLayer(const std::shared_ptr<Layer2> &layer) NOTHROWS;
                Util::TAKErr addLayer(Layer2Ptr &&layer) NOTHROWS;

                /**
                 * Adds the specified layer at the specified position.
                 *
                 * @param position  The position. A position of <code>0</code> is the bottom
                 *                  of the stack (will be rendered first); a position of
                 *                  {@link #getNumLayers()} is the top of the stack (will be
                 *                  rendered last.
                 * @param layer     The layer to be added
                 *
                 * @throws IndexOutOfBoundsException    if <code>index</code> is less than
                 *                                      <code>0</code> or greater than
                 *                                      {@link #getNumLayers()}.
                 */
                Util::TAKErr addLayer(const std::size_t position, const std::shared_ptr<Layer2> &layer) NOTHROWS;
                Util::TAKErr addLayer(const std::size_t position, Layer2Ptr &&layer) NOTHROWS;

                /**
                 * Removes the specified layer
                 *
                 * @param layer The layer to be removed
                 */
                Util::TAKErr removeLayer(const Layer2 &layer) NOTHROWS;

                /**
                 * Removes all layers.
                 */
                Util::TAKErr removeAllLayers() NOTHROWS;

                /**
                 * Sets the position of the layer in the stack. A position of <code>0</code>
                 * is the bottom of the stack (will be rendered first); a position of
                 * {@link #getNumLayers()}<code> - 1</code> is the top of the stack (will be
                 * rendered last.
                 *
                 * @param layer     The layer
                 * @param position  The index
                 *
                 * @throws IndexOutOfBoundsException    if <code>index</code> is less than
                 *                                      <code>0</code> or greater than or
                 *                                      equal to {@link #getNumLayers()}.
                 */
                Util::TAKErr setLayerPosition(const Layer2 &layer, const std::size_t position) NOTHROWS;

                /**
                 * Returns the number of layers in the stack.
                 *
                 * @return  The number of layers
                 */
                std::size_t getNumLayers() const NOTHROWS;

                /**
                 * Returns the layer at the specified position in the stack.
                 *
                 * @param i The index
                 *
                 * @return  The layer at the specified index in the stack.
                 *
                 * @throws IndexOutOfBoundsException    if <code>index</code> is less than
                 *                                      <code>0</code> or greater than or
                 *                                      equal to {@link #getNumLayers()}.
                 */
                Util::TAKErr getLayer(std::shared_ptr<Layer2> &value, const std::size_t i) const NOTHROWS;

                /**
                 * Returns the current layer stack. Modification of the
                 * {@link java.util.List List} returned will not result in modification of
                 * the actual layer stack.
                 *
                 * @return  The current layer stack.
                 */
                Util::TAKErr getLayers(Port::Collection<std::shared_ptr<Layer2>> &value) const NOTHROWS;

                /**
                 * Adds the specified {@link OnLayersChangedListener}.
                 *
                 * @param l The listener to be added.
                 */
                Util::TAKErr addLayersChangedListener(MultiLayer2::LayersChangedListener *l) NOTHROWS;

                /**
                 * Removes the specified {@link OnLayersChangedListener}.
                 *
                 * @param l The listener to be removed.
                 */
                Util::TAKErr removeLayersChangedListener(MultiLayer2::LayersChangedListener *l) NOTHROWS;
            public : // Abstract Layer
                virtual void setVisible(const bool visible) NOTHROWS;
            protected :
                Util::TAKErr addLayerNoSync(const std::size_t position, const std::shared_ptr<Layer2> &layer) NOTHROWS;
                Util::TAKErr dispatchOnLayerAddedNoSync(const std::shared_ptr<Layer2> &layer) NOTHROWS;
                Util::TAKErr dispatchOnLayerRemovedNoSync(Port::Collection<std::shared_ptr<Layer2>> &layers) NOTHROWS;
                Util::TAKErr dispatchOnLayerPositionChanged(const std::shared_ptr<Layer2> &l, const std::size_t oldPos, const std::size_t newPos) NOTHROWS;

                void updateVisibility() NOTHROWS;
            private :
                std::set<LayersChangedListener *> layers_changed_listeners_;
                std::vector<std::shared_ptr<Layer2>> layers_;

                std::unique_ptr<VisibilityUpdater> visibility_updater_;
            };

            /**
            * Callback interface to provide notification on programmatic modification
            * of the layer stack.
            *
            * @author Developer
            */
            class ENGINE_API MultiLayer2::LayersChangedListener
            {
            protected :
                virtual ~LayersChangedListener() NOTHROWS = 0;
            public :
                /**
                * Notifies the user when a layer has been added.
                *
                * @param parent    The multi layer
                * @param layer     The layer that was added
                */
                virtual void layerAdded(const MultiLayer2 &parent, const std::shared_ptr<Layer2> &layer) NOTHROWS = 0;

                /**
                * Notifies the user when a layer has been removed.
                *
                * @param parent    The multi layer
                * @param layer     The layer that was removed
                */
                virtual void layerRemoved(const MultiLayer2 &parent, const std::shared_ptr<Layer2> &layer) NOTHROWS = 0;

                /**
                * Notifies the user when the position of the layer has been explicitly
                * changed. This callback will <B>NOT</B> be invoked when a layer's
                * position changes due to the addition or removal of other layers.
                *
                * @param parent        The multi layer
                * @param layer         The layer
                * @param oldPosition   The layer's old position
                * @param newPosition   The layer's new position
                */
                virtual void layerPositionChanged(const MultiLayer2 &parent, const std::shared_ptr<Layer2> &layer, const std::size_t oldPosition, const std::size_t newPosition) NOTHROWS = 0;
            };
        }
    }
}

#endif

