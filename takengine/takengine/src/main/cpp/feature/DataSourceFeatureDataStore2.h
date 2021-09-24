#ifndef TAK_ENGINE_FEATURE_DATASOURCEFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_DATASOURCEFEATUREDATASTORE2_H_INCLUDED

#include "db/RowIterator.h"
#include "feature/FeatureDataStore2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API DataSourceFeatureDataStore2 : public virtual FeatureDataStore2
            {
            public :
                class FileCursor;
                typedef std::unique_ptr<FileCursor, void(*)(const FileCursor *)> FileCursorPtr;
            protected :
                virtual ~DataSourceFeatureDataStore2() NOTHROWS = 0;
            public :
                /**
                * Returns <code>true</code> if the layers for the specified file are in the
                * data store, <code>false</code> otherwise.
                *
                * @param file  A file
                *
                * @return  <code>true</code> if the layers for the specified file are in
                *          the data store, <code>false</code> otherwise.
                */
                virtual TAK::Engine::Util::TAKErr contains(bool *value, const char *file) NOTHROWS = 0;

                /**
                * Returns the file associated with the specified layer in the data store.
                *
                * @param info  A layer descriptor
                *
                * @return  The file associated with the specified layer or
                *          <code>null</code> if the data store does not contain the layer.
                */
                virtual TAK::Engine::Util::TAKErr getFile(Port::String &file, const int64_t fsid) NOTHROWS = 0;


                /**
                * Adds the layers for the specified file to the data store.
                *
                * @param file  A file
                *
                * @return  <code>true</code> if the layers for the file were added,
                *          <code>false</code> if no layers could be derived from the file.
                *
                * @throws IllegalArgumentException If the data store already contains the
                *                                  layers for the specified file.
                */
                virtual TAK::Engine::Util::TAKErr add(const char *file) NOTHROWS = 0;

                /**
                * Adds the layers for the specified file to the data store.
                *
                * @param file  A file
                * @param hint  The name of the preferred provider to create the layers, if
                *              <code>null</code> any compatible provider will be used.
                *
                * @return  <code>true</code> if the layers for the file were added,
                *          <code>false</code> if no layers could be derived from the file.
                *
                * @throws IllegalArgumentException If the data store already contains the
                *                                  layers for the specified file.
                */
                virtual TAK::Engine::Util::TAKErr add(const char *file, const char *hint) NOTHROWS = 0;

                /**
                * Removes all layers derived from the specified file from the data store.
                */
                virtual TAK::Engine::Util::TAKErr remove(const char *) NOTHROWS = 0;

                /**
                * Updates the layers derived from the specified file.
                *
                * @param file  The file
                *
                * @return  <code>true</code> if the layers were successfully updated,
                *          <code>false</code> otherwise
                *
                * @throws IOException
                */
                virtual TAK::Engine::Util::TAKErr update(const char *file) NOTHROWS = 0;

                /**
                * Updates the specified layer. Note that if other layers were derived from
                * the same file as the specified layer that they will be updated as well.
                *
                * @param info  The layer descriptor
                *
                * @return  <code>true</code> if the layers were successfully updated,
                *          <code>false</code> otherwise
                *
                * @throws IOException
                */
                virtual TAK::Engine::Util::TAKErr update(const int64_t fsid) NOTHROWS = 0;

                /**
                * Returns all of the files with content currently managed by the data
                * store.
                *
                * @return  The files with content currently managed by the data store.
                */
                virtual TAK::Engine::Util::TAKErr queryFiles(FileCursorPtr &cursor) NOTHROWS = 0;
            }; // DataSourceFeatureDataStore

            class DataSourceFeatureDataStore2::FileCursor : public TAK::Engine::DB::RowIterator
            {
            protected :
                virtual ~FileCursor() NOTHROWS = 0;
            public :
                virtual TAK::Engine::Util::TAKErr getFile(TAK::Engine::Port::String &path) NOTHROWS = 0;
            };
        }
    }
}

#endif
