////============================================================================
////
////    FILE:           DatasetDescriptor.h
////
////    DESCRIPTION:    Class definition of an immutable descriptor object for a
///                     dataset.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 9, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_DATASET_DESCRIPTOR_H_INCLUDED
#define ATAKMAP_RASTER_DATASET_DESCRIPTOR_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <cstddef>
#include <functional>
#include <iosfwd>
#include <map>
#include <memory>
#include <ostream>
#include <set>
#include <stdint.h>
#include <utility>
#include <vector>

#include "port/Platform.h"
#include "core/GeoPoint.h"
#include "feature/Envelope.h"
#include "spi/ServiceProvider.h"
#include "port/String.h"
#include "util/Error.h"
#include "util/Blob.h"
#include "util/IO_Decls.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


class ENGINE_API Geometry;


}                                       // Close feature namespace.

namespace util
{


struct IO_Error;


}                                       // Close util namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


///=============================================================================
///
///  class atakmap::raster::DatasetDescriptor
///
///     Immutable descriptor object for a dataset. Defines a number of common
///     properties over a dataset (e.g. name, resolution, URI, etc.) and also
///     provides a mechanism for storage of opaque application specific data.
///
///     The descriptor may retain ownership over a working directory.  This
///     directory is guaranteed to remain unmodified by the application for the
///     lifetime of the descriptor, including between runtime invocations of the
///     application so long as the descriptor still exists and is valid (e.g. it
///     is stored in persistent storage on the local file system such as SQLite).
///     This directory may be used by dataset service providers to store
///     information and data associated with the dataset.  There should be an
///     expectation that no service provider instance for any other dataset
///     descriptor will access the directory, nor will any data for any dataset
///     descriptor ever be written to the directory.  Example uses would be a
///     catalog for a dataset composed of adjacent images or a cache for
///     subsampled tiles of high-resolution imagery.
///
///=============================================================================

    typedef std::unique_ptr<class DatasetDescriptor, void(*)(const class DatasetDescriptor *)> DatasetDescriptorUniquePtr;
    

class ENGINE_API DatasetDescriptor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class ENGINE_API Factory;                      // DatasetDescriptor creation factory.

    enum Type
      {
        IMAGE,
        MOSAIC
      };

    typedef atakmap::util::BlobImpl
            ByteBuffer;
      
    //
    // DescriptorArgs pairs a file path and (optional) working directory path.
    //
    typedef std::pair<const char*, const char*>
            FactoryArgs;

    struct PtrLess
      : std::binary_function<DatasetDescriptor*, DatasetDescriptor*, bool>
      {
        bool
        operator() (const DatasetDescriptor* lhs,
                    const DatasetDescriptor* rhs)
            const
          { return rhs && (!lhs || *lhs < *rhs); }
      };

    typedef std::set<DatasetDescriptor*, PtrLess>
            DescriptorSet;

    typedef spi::ServiceProviderCallback
            CreationCallback;


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    enum
      {
        DEFAULT_PROBE = 500             // Default limit for isSupported probing.
      };

    static const char* const URI_CHARACTER_ENCODING;    // "UTF-8"


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~DatasetDescriptor ()
        NOTHROWS;

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    bool
    operator== (const DatasetDescriptor& rhs)
        const
        NOTHROWS
      { return uri_ == rhs.uri_; }

    bool
    operator!= (const DatasetDescriptor& rhs)
        const
        NOTHROWS
      { return !(*this == rhs); }

    bool
    operator< (const DatasetDescriptor& rhs)
        const
        NOTHROWS
      { return getURI () < rhs.getURI (); }


    //
    // Computes the resolution, in meters-per-pixel, of an image given the
    // pixel dimensions of the image and its four corner coordinates.  The
    // returned value is a local resolution and results may not be accurate for
    // images with large spatial extents.
    //
    static
    double
    computeGSD (unsigned long width,
                unsigned long height,
                const core::GeoPoint& ul,
                const core::GeoPoint& ur,
                const core::GeoPoint& lr,
                const core::GeoPoint& ll)
        NOTHROWS;

    //
    // Returns a (possibly NULL) set of DatasetDescriptors for the supplied
    // filePath.  A NULL pointer will be returned if no registered Factory
    // supports the supplied filePath.  In the event that more than one
    // descriptor is produced for the supplied filePath, the Factory will create
    // subdirectories in the supplied workingDir for each returned descriptor.
    // A non-NULL typeHint selects a registered Factory by type.  When NULL, the
    // returned set will be the result from the first Factory that supports the
    // supplied filePath.  If the supplied CreationCallback is not NULL, it will
    // receive progress updates.
    //
    // Throws std::invalid_argument if filePath is NULL, workingDir is NULL
    // when multiple descriptors need to be created, or a factory supports the
    // supplied filePath, but no datasets could be derived.
    //
    static
    DescriptorSet*
    create (const char* filePath,
            const char* workingDir,
            const char* typeHint = nullptr,
            CreationCallback* = nullptr);

    //
    // Creates a simple Polygon from four coordinates.
    //
    static
    feature::Geometry*
    createSimpleCoverage (const core::GeoPoint& ul,
                          const core::GeoPoint& ur,
                          const core::GeoPoint& lr,
                          const core::GeoPoint& ll)
        NOTHROWS;

    //
    // Decodes a dataset descriptor from the supplied byte array.
    //
    static
    DatasetDescriptor*
    decode (const ByteBuffer&)
        throw (util::IO_Error);

    //
    // Decodes a dataset descriptor from the supplied input stream.
    //
    static
    DatasetDescriptor*
    decode (std::istream&)
        throw (util::IO_Error);

    //
    // Encodes the descriptor as a byte array, assigning the supplied layerID to
    // the result.  The descriptor that will result from a subsequent decode of
    // the byte array will have the specified ID.
    //
    // Returns begin and end iterators to the byte array.
    //
    ByteBuffer
    encode (int64_t layerID)
        throw (util::IO_Error);

    //
    // Encodes the descriptor to the supplied output stream, assigning the
    // supplied layerID to the result.  The descriptor that will result from a
    // subsequent decode of the stream contents will have the specified ID.
    //
    void
    encode (int64_t layer_id,
            std::ostream&)
        throw (util::IO_Error);

    //
    // Returns a heap-allocated type string for an image, formatted as:
    //
    //  <baseType><resolution>
    //
    // where <baseType> is the image type and <resolution> is the image
    // resolution (in meters/pixel) formatted as 0_c for resolution in
    // centimeters or m for resolution in meters.
    //
    // Examples:
    //
    //  Nitf10 is NITF 10m data
    //  Nitf0_5 is NITF 50cm data
    //
    static
    TAK::Engine::Port::String
    formatImageryType (const char* baseType,
                       double resolution)
        NOTHROWS;

    static
    TAK::Engine::Port::String
    formatResolution(double resolution)
        NOTHROWS;

    //
    // Returns the dataset coverage for the specified imagery type (or NULL).
    // Returns the coverage across all imagery types if the supplied imageryType
    // is NULL.  The returned Geometry is owned by the DatasetDescriptor and
    // must not be deleted.
    //
    const feature::Geometry*
    getCoverage (const char* imageryType = nullptr) // Defaults to all types.
        const
        NOTHROWS;

    //
    // Returns the type of the dataset.  The dataset type will drive which
    // service providers are selected for activities such as rendering the
    // dataset and performing the image-to-ground and ground-to-image functions
    // on the dataset.
    //
    const char*
    getDatasetType ()
        const
        NOTHROWS
      { return dataset_type_; }

    //
    // Returns the (possibly NULL) extra data associated with the specified key.
    // The extra data is opaque application specific data that may only be
    // correctly interpreted by the relevant service providers.
    //
    const char*
    getExtraData (const char* key)
        const
        NOTHROWS;

    //
    // Convenience method for obtaining an extra data value.  Returns the extra
    // data value associated with the supplied key, or defaultValue if the key
    // is not found.
    //
    static
    const char*
    getExtraData (const DatasetDescriptor&,
                  const char* key,
                  const char* defaultValue)
        NOTHROWS;

    //
    // Returns the (possibly NULL) registered Factory of the supplied type.
    //
    static
    const Factory*
    getFactory (const char* factoryType);

    //
    // Returns the imagery types available for the dataset.  These types should
    // be relatively well-defined (e.g. cib1, onc, tpc).
    //
    const std::vector<TAK::Engine::Port::String>&
    getImageryTypes ()
        const
        NOTHROWS
      { return imagery_types_; }

    //
    // Returns the ID assigned by the data store that the dataset descriptor
    // resides in.  A value of 0 is reserved and indicates that the dataset
    // descriptor does not belong to a data store.
    //
    int64_t
    getLayerID ()
        const
        NOTHROWS
      { return layer_id_; }

    //
    // Returns the (possibly NULL) local data value for the supplied key.  Local
    // data is runtime information that external objects may associate with an
    // instance.  Local data is only valid for the instance and will not persist
    // between invocations of the software.
    //
    const void*
    getLocalData (const char* key)
        const
        NOTHROWS;

    template <typename T>
    const T*
    getLocalData (const char* key)
        const
        NOTHROWS;

    //
    // Returns the maximum resolution, in meters-per-pixel, for the supplied
    // imagery type, or the maximum resolution for the dataset if the supplied
    // imageryType is NULL.  Returns NaN for unrecognized imageryType.
    //
    // Data for the supplied imageryType should not be displayed when the map
    // resolution is greater than the returned value.
    //
    double
    getMaxResolution (const char* imageryType = nullptr)
        const
        NOTHROWS;

    //
    // Returns the minimum bounding box for the dataset.
    //
    feature::Envelope
    getMinimumBoundingBox ()
        const
        NOTHROWS
      { return minimum_bounding_box_; }

    //
    // Returns the minimum resolution, in meters-per-pixel, for the supplied
    // imagery type, or the minimum resolution for the dataset if the supplied
    // imageryType is NULL.  Returns NaN for unrecognized imageryType.
    //
    // Data for the supplied imageryType should not be displayed when the map
    // resolution is lower than the returned value.
    //
    double
    getMinResolution (const char* imageryType = nullptr)
        const
        NOTHROWS;

    //
    // Returns the name of the dataset.
    //
    const char*
    getName ()
        const
        NOTHROWS
      { return name_; }

    //
    // Returns the name of the service provider that was responsible for
    // creating the dataset descriptor.
    //
    const char*
    getProvider ()
        const
        NOTHROWS
      { return provider_; }

    //
    // Returns the spatial reference ID for the dataset, or -1 if the spatial
    // reference is not well-defined.
    //
    int
    getSpatialReferenceID ()
        const
        NOTHROWS
      { return spatial_reference_id_; }

    //
    // Returns the URI for the dataset.
    //
    const char*
    getURI ()
        const
        NOTHROWS
      { return uri_; }

    //
    // Returns the (possibly NULL) working directory for the dataset.
    // Generally, contents of the working directory should only be modified by
    // applicable service providers.
    //
    const char*
    getWorkingDirectory ()
        const
        NOTHROWS
     { return working_directory_; }

    //
    // Returns true if the dataset is remote, false if the dataset resides on
    // the local file system.
    //
    bool
    isRemote ()
        const
        NOTHROWS
      { return !is_local_; }
      
    
    //
    // Returns a exact copy of the dataset
    //
    virtual TAK::Engine::Util::TAKErr clone(DatasetDescriptorUniquePtr &value) const NOTHROWS = 0;
      
    static void deleteDatasetDescriptor(const DatasetDescriptor *datasetDescriptor) NOTHROWS;
      
    //
    // Returns false if no Factory supports the supplied filePath.  Returns true
    // if a Factory exists that *may* support the supplied filePath.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    static
    bool
    isSupported (const char* filePath);

    //
    // Register the supplied Factory.  Ignores NULL Factory or Factory with
    // Factory::getType() == NULL.
    //
    static
    void
    registerFactory (const Factory*);

    //
    // Sets the local data value for the supplied key.  Local data is runtime
    // information that external objects may associate with an instance.  Local
    // data is only valid for the instance and will not persist between
    // invocations of the software.
    //
    // Returns the (possibly NULL) old value associated with the supplied key.
    //
    const void*
    setLocalData (const char* key,
                  const void* value)    // Not adopted.
        NOTHROWS;

    //
    // Unregisters the supplied Factory.  Ignores NULL Factory or Factory with
    // Factory::getType() == NULL.
    //
    static
    void
    unregisterFactory (const Factory*);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED NESTED TYPES
    //==================================


    typedef std::map<TAK::Engine::Port::String, std::pair<double, double>, TAK::Engine::Port::StringLess>
            ResolutionMap;

    typedef std::map<TAK::Engine::Port::String, const feature::Geometry*, TAK::Engine::Port::StringLess>
            CoverageMap;

    typedef std::map<TAK::Engine::Port::String, TAK::Engine::Port::String, TAK::Engine::Port::StringLess>
            StringMap;

    typedef std::vector<TAK::Engine::Port::String>
            StringVector;


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    //
    // Creates a new DatasetDescriptor.
    //
    //  type            The type of descriptor.
    //  layerId         The ID for the descriptor.
    //  name            The name of the dataset.
    //  URI             The URI for the dataset.
    //  provider        The name of the service provider responsible for
    //                  creation.
    //  datasetType     The dataset type.
    //  imageryTypes    The (non-empty) imagery types available for the dataset.
    //  resolutions     The resolutions for each imagery type in the dataset.
    //  coverages       The coverage for each imagery type in the dataset. The
    //                  DatasetDescriptor will assume ownership of the Geometry
    //                  objects.
    //  referenceID     The Spatial Reference ID for the dataset or -1 if not
    //                  known or not well-defined.
    //  isRemote        A flag indicating whether the dataset content is local
    //                  or remote.
    //  workingDir      The (possibly NULL) working directory for the dataset.
    //  extraData       Opaque application specified data associated with the
    //                  dataset.
    //
    // Ownership of the Geometry objects in the supplied CoverageMap is assumed
    // by the DatasetDescriptor. Concrete derivations are expected to provide
    // newly allocated Geometry objects (e.g. copies) to the base class,
    // leaving ultimate responsibility of Geometry deletion for the CoverageMap
    // passed to the derived constructor on the client.
    //
    // Throws std::invalid_argument if name, URI, provider, or datasetType is
    // NULL.
    //
    DatasetDescriptor (Type type,
                       int64_t layer_id,
                       const char* name,
                       const char* uri,
                       const char* provider,
                       const char* dataset_type,
                       const StringVector& imageryTypes,
                       const ResolutionMap& resolutions,
                       const CoverageMap& coverages,
                       int reference_id,
                       bool is_remote,
                       const char* working_dir,
                       const StringMap& extra_data);

    DatasetDescriptor (const DatasetDescriptor&);

    DatasetDescriptor&
    operator= (const DatasetDescriptor&);


    static
    CoverageMap
    cloneCoverageMap(const CoverageMap& coverageMap);

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    static
    DatasetDescriptor*
    decodeVersion7 (std::istream&)
        throw (util::IO_Error);

    static
    DatasetDescriptor*
    decodeVersion8(std::istream&)
        throw (util::IO_Error);

    static
    DatasetDescriptor*
    decodeVersion9(std::istream&)
        throw (util::IO_Error);

    static
    DatasetDescriptor*
    decodeVersion10(std::istream&)
        throw (util::IO_Error);
    //
    // Encodes the details of the derived descriptor class to the supplied
    // output stream.
    //
    virtual
    void
    encodeDetails (std::ostream&)       // Called by encode.
        throw (util::IO_Error)
        = 0;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    Type type_;
    int64_t layer_id_;
    int spatial_reference_id_;
    TAK::Engine::Port::String name_;
    TAK::Engine::Port::String uri_;
    TAK::Engine::Port::String provider_;
    TAK::Engine::Port::String dataset_type_;
    TAK::Engine::Port::String working_directory_;
    StringVector imagery_types_;
    CoverageMap coverages_;
    ResolutionMap resolutions_;
    StringMap extra_data_;
    feature::Envelope minimum_bounding_box_;
    bool is_local_;                       // On local file system.
    std::map<std::string, const void*> local_data_;
  };


///=============================================================================
///
///  class atakmap::raster::DatasetDescriptor::Factory
///
///     Abstract base class for factories that create DatasetDescriptors.
///
///=============================================================================


class ENGINE_API DatasetDescriptor::Factory
  : public spi::InteractiveServiceProvider
               <spi::StrategyServiceProvider
                    <spi::PriorityServiceProvider
                         <spi::ServiceProvider<DescriptorSet, FactoryArgs> >,
                     const char*> >
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    using spi::InteractiveServiceProvider
              <spi::StrategyServiceProvider
                   <spi::PriorityServiceProvider
                        <spi::ServiceProvider<DescriptorSet, FactoryArgs> >,
                    const char*> >::create;

    //
    // Returns the parse version associated with this Factory.  The parse
    // version should be incremented every time the Factory's implementation is
    // modified to produce different results.
    //
    // The version number 0 is a reserved value that should never be returned.
    //
    virtual
    unsigned short
    getVersion ()
        const
        NOTHROWS
        = 0;


    //==================================
    //  spi::InteractiveServiceProvider INTERFACE
    //==================================


    DescriptorSet*
    create (const FactoryArgs&,
            const char* strategy,
            CreationCallback*)
        const override;


    //==================================
    //  spi::PriorityServiceProvider INTERFACE
    //==================================


    unsigned int
    getPriority ()
        const
        NOTHROWS override
      { return priority_; }


    //==================================
    //  spi::StrategyServiceProvider INTERFACE
    //==================================


    DescriptorSet*
    create (const FactoryArgs& args,
            const char* strategy)
        const override
      { return create (args, strategy, NULL); }

    const char*
    getStrategy ()
        const
        NOTHROWS override
      { return strategy_; }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //
    // Throws std::invalid_argument if the supplied strategy is NULL.
    //
    Factory (const char* strategy,
             unsigned int priority);


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    //
    // Returns a (possibly NULL) pointer to a DescriptorSet created for the
    // supplied filePath and (possibly NULL) workingDir.  The implementation
    // should return NULL if no result can be created for the supplied filePath.
    //
    virtual
    DescriptorSet*
    createImpl (const char* filePath,   // Never NULL.
                const char* workingDir, // May be NULL.
                CreationCallback*)      // May be NULL.
        const
        = 0;

    virtual
    bool
    probeFile (const char* file,
               CreationCallback&)
        const
        = 0;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    const char* strategy_;
    unsigned int priority_;
  };


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{

template <typename T>
inline
const T*
DatasetDescriptor::getLocalData (const char* key)
    const
    NOTHROWS
  { return static_cast<const T*> (getLocalData (key)); }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_RASTER_DATASET_DESCRIPTOR_H_INCLUDED
