#ifndef TAK_ENGINE_FEATURE_ABSTRACTDATASOURCEFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_ABSTRACTDATASOURCEFEATUREDATASTORE2_H_INCLUDED

#include "feature/AbstractFeatureDataStore2.h"
#include "feature/DataSourceFeatureDataStore2.h"
#include "feature/FeatureDataSource.h"
#include "port/Platform.h"
#include "util/Error.h"

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4250)
#endif

namespace TAK {
    namespace Engine {
        namespace Feature {
            class AbstractDataSourceFeatureDataStore2 : public DataSourceFeatureDataStore2,
                public AbstractFeatureDataStore2
            {
            protected:
                AbstractDataSourceFeatureDataStore2(int visFlags) NOTHROWS;
            protected:
                virtual ~AbstractDataSourceFeatureDataStore2() NOTHROWS = 0;
            public: // DataSourceFeatureDataStore2 impl
                virtual TAK::Engine::Util::TAKErr contains(bool *value, const char *file) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getFile(TAK::Engine::Port::String &file, const int64_t fsid) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr add(const char *file) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr add(const char *file, const char *hint) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr remove(const char *file) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr update(const char *file) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr update(const int64_t fsid) NOTHROWS override;
            protected: // AbstractFeatureDataStore2 impl
                virtual TAK::Engine::Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr deleteFeatureImpl(const int64_t fsid) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS override;
            protected:
                virtual TAK::Engine::Util::TAKErr containsImpl(bool *value, const char *path) NOTHROWS = 0;

                /**
                * Implements {@link #getFile(FeatureSet)}.
                *
                * <P>Invocation of this method should always be externally synchronized on
                * <code>this</code>.
                *
                * @param fsid  The feature set ID
                *
                * @return  The file associated with the specified feature set or
                *          <code>null</code> if the data store does not contain the layer.
                */
                virtual TAK::Engine::Util::TAKErr getFileNoSync(TAK::Engine::Port::String &path, const int64_t fsid) NOTHROWS = 0;

                /**
                * Implementation for {@link #add(File, String)}. Returns <code>true</code>
                * on success.
                *
                * <P>Modification of the data store is internally synchronized on
                * <code>this</code>. It is recommended not to invoke this method when
                * externally synchronized on <code>this</code> as creation of the dataset
                * descriptors may take significant time.
                *
                * @param file      A file
                * @param hint      The provider hint
                * @param notify    If <code>true</code>,
                *                  {@link #dispatchDataStoreContentChangedNoSync()} will be
                *                  invoked prior to this method returning successfully
                *
                * @return  <code>true</code> if the layers for the file were added,
                *          <code>false</code> if no layers could be derived from the file.
                *
                * @throws IOException
                */
                virtual TAK::Engine::Util::TAKErr addNoSync(const char *file, const char *hint, const bool notify)  NOTHROWS;


                /**
                * Adds the parsed layers to the underlying storage.
                *
                * <P>Invocation of this method should always be externally synchronized on
                * <code>this</code>.
                *
                * @param file      The file that the layers were derived from
                * @param layers    The layers to be added to the storage
                */
                virtual TAK::Engine::Util::TAKErr addImpl(const char *file, atakmap::feature::FeatureDataSource::Content &content) NOTHROWS = 0;

                /**
                * Removes the layers derived from the specified file from the data store.
                *
                * <P>Invocation of this method should always be externally synchronized on
                * <code>this</code>.
                *
                * @param file      The file
                * @param notify    If <code>true</code>,
                *                  {@link #dispatchDataStoreContentChangedNoSync()} will be
                *                  invoked prior to this method returning successfully
                */
                virtual TAK::Engine::Util::TAKErr removeNoSync(const char *path, const bool notify) NOTHROWS;

                /**
                * Implements {@link #removeNoSync(File, boolean)}.
                *
                * <P>Invocation of this method should always be externally synchronized on
                * <code>this</code>.
                *
                * @param file  The file
                */
                virtual TAK::Engine::Util::TAKErr removeImpl(const char *path) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr updateImpl(const char *path) NOTHROWS;
            }; // DataSourceFeatureDataStore
        }
    }
}

#ifdef _MSC_VER
#pragma warning( pop )
#endif

#endif // TAK_ENGINE_FEATURE_ABSTRACTDATASOURCEFEATUREDATASTORE2_H_INCLUDED
