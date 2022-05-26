#ifndef TAK_ENGINE_FEATURE_FEATUREDATASOURCE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATUREDATASOURCE2_H_INCLUDED

#include <memory>

#include <util/NonCopyable.h>

#include "feature/FeatureDefinition2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FeatureDataSource2 : private TAK::Engine::Util::NonCopyable
            {
            public :
                class ENGINE_API Content;
                typedef std::unique_ptr<Content, void(*)(const Content *)> ContentPtr;
                typedef std::unique_ptr<const Content, void(*)(const Content *)> ContentPtr_const;
            protected :
                virtual ~FeatureDataSource2() NOTHROWS = 0;
                /**
                * Parses the features for the specified file.
                *
                * @param file  The file
                *
                * @return  The parsed content. The content's cursor is always positioned
                *          <I>before</I> the first feature, so the
                *          {@link Content#moveToNext()} method should always be invoked
                *          before attempting access.
                *
                * @throws IOException  If an I/O error occurs while parsing the file.
                */
            public :
                //Content parse(File file) throws IOException;
                virtual TAK::Engine::Util::TAKErr parse(ContentPtr &content, const char *file) NOTHROWS = 0;

                /**
                * The name of the provider.
                *
                * @return  The name of the provider.
                */
                //public String getName();
                virtual const char *getName() const NOTHROWS = 0;

                /**
                * Returns the parse implementation version for this provider. Different
                * parse versions indicate that a provider may produce different content
                * from the same file.
                *
                * @return  The parse implementation version.
                */
                //public int parseVersion();
                virtual int parseVersion() const NOTHROWS = 0;
            };

            /**
            * Parsed feature content. Provides access to the feature data.
            *
            * @author Developer
            */
            class ENGINE_API FeatureDataSource2::Content
            {
            protected :
                virtual ~Content() NOTHROWS = 0;
            public :
                /**
                * Returns the type of the content (e.g. KML). This field is always
                * available regardless of pointer position.
                *
                * @return The type of the content.
                */
                //public String getType();
                virtual const char *getType() const NOTHROWS = 0;
                /**
                * Returns the name of the provider that parsed the content. This field
                * is always available regardless of pointer position.
                *
                * @return The name of the provider that parsed the content.
                */
                //public String getProvider();
                virtual const char *getProvider() const NOTHROWS = 0;
                /**
                * Increments the specified content pointer.  When the
                * {@link ContentPointer#FEATURE_SET} pointer is incremented the
                * the {@link ContentPointer#FEATURE} pointer is automatically reset
                * to the first feature for the new set.
                *
                * @param point    The pointer to be incremented
                *
                * @return <code>true</code> if there is another feature,
                *         <code>false</code> if no more features are available.
                */
                //public boolean moveToNext(ContentPointer pointer);
                virtual TAK::Engine::Util::TAKErr moveToNextFeature() NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr moveToNextFeatureSet() NOTHROWS = 0;
                /**
                * Returns the current feature definition. This field is dependent on
                * the current {@link ContentPointer#FEATURE} pointer position.
                *
                * @return The current feature definition.
                */
                //public FeatureDefinition get();
                virtual TAK::Engine::Util::TAKErr get(FeatureDefinition2 **feature) const NOTHROWS = 0;
                /**
                * Returns the name for the current feature set. This field is dependent
                * on the current {@link ContentPointer#FEATURE_SET} pointer position.
                *
                * @return The name for the current feature set.
                */
                //public String getFeatureSetName();
                virtual TAK::Engine::Util::TAKErr getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS = 0;
                /**
                 * Returns a value indicating whether the feature set should be displayed.
                 * This field is dependent on the current
                 * {@link ContentPointer#FEATURE_SET} pointer position.
                 *
                 * @return Whether the feature set is visible.
                 */
                virtual TAK::Engine::Util::TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS = 0;

                /**
                * Returns the minimum resolution that the features should be displayed
                * at. This field is dependent on the current
                * {@link ContentPointer#FEATURE_SET} pointer position.
                *
                * @return The minimum resolution, in meters-per-pixel, that the
                *         features should be displayed at.
                */
                //public double getMinResolution();
                virtual TAK::Engine::Util::TAKErr getMinResolution(double *value) const NOTHROWS = 0;
                /**
                * Returns the maximum resolution that the features should be displayed
                * at. This field is dependent on the current
                * {@link ContentPointer#FEATURE_SET} pointer position.
                *
                * @return The maximum resolution, in meters-per-pixel, that the
                *         features should be displayed at.
                */
                //public double getMaxResolution();
                virtual TAK::Engine::Util::TAKErr getMaxResolution(double *value) const NOTHROWS = 0;
				/**
				* Returns a value indicating whether the feature should be displayed.
				* This field is dependent on the current
				* {@link ContentPointer#FEATURE} pointer position.
				*
				* @return Whether the feature is visible.
				*/
				virtual TAK::Engine::Util::TAKErr getVisible(bool* visible) const NOTHROWS = 0;
            };

            typedef std::unique_ptr<FeatureDataSource2, void(*)(const FeatureDataSource2 *)> FeatureDataSourcePtr;
            typedef std::unique_ptr<const FeatureDataSource2, void(*)(const FeatureDataSource2 *)> FeatureDataSourcePtr_const;

            ENGINE_API Util::TAKErr FeatureDataSourceFactory_parse(FeatureDataSource2::ContentPtr &content, const char *path, const char *hint) NOTHROWS;
            ENGINE_API Util::TAKErr FeatureDataSourceFactory_getProvider(std::shared_ptr<FeatureDataSource2> &spi, const char *hint) NOTHROWS;
            ENGINE_API Util::TAKErr FeatureDataSourceFactory_registerProvider(FeatureDataSourcePtr &&provider, const int priority) NOTHROWS;
            ENGINE_API Util::TAKErr FeatureDataSourceFactory_registerProvider(const std::shared_ptr<FeatureDataSource2> &provider, const int priority) NOTHROWS;
            ENGINE_API Util::TAKErr FeatureDataSourceFactory_unregisterProvider(const FeatureDataSource2 &provider) NOTHROWS;
        }
    }
}

#endif
