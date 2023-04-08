# Version History

## 1.10.5

* Advance streambuf end pointer in DataInput2Streambuf

## 1.10.4

* `GLQuadTileNode4` address potential `NullPointerException` for renderers without surface control

## 1.10.3

* `DatabaseInformation.getUri(String &)` should not check for `NULL`-ness

## 1.10.2

* `GdalTileReader` supports concurrent read lanes for different subsample rates

## 1.10.1

* `GLGlobeSurfaceRenderer` sorts dirty tiles before updates

## 1.10.0

* Java `GLQuadTileNode4` prefetches nodes to be rendered based on surface bounds at render pump start

## 1.9.0

* Refactor outlines data store to `:takkernel:engine`

## 1.8.0

* Refactor `DatabaseInformation::getUri()` and `::getPassphrase()` `const char *` -> `TAK::Engine::Port::String` to retain ownership over memory

## 1.7.3

* `GLLabelManager` interprets text size of zero as default for consistency pre `0.61.0`

## 1.7.2

* Make `OGR_Content2` constructor exception free
* `OGR_Content2` constructs path via chaining as appropriate
* make `NativeFeatureDataSource` JNI implementation more permissive, but stay within bounds of contract, to avoid unhandled exceptions

## 1.7.1

* Initialize various fields on `Shader` and `Shader2` structs to address misbehavior observed in some runtimes

## 1.7.0

* Overload _interactive_ `CameraController` pan functions to allow client to specify whether or not to perform smooth pan over poles

## 1.6.0

* Introduce new `ICertificateStore` and `ICredentialsStore` as API replacement for legacy certificate and authentication databases
* Replace legacy certificate and authentication database implementation

## 1.5.0

* Add new `Strings.isBlank(String)` to remove _implementation_ dependency `apache-commons-slim` from `:shared` 

## 1.4.2

* Apply some threshold to mitigate globe rotating on pan when zoomed fully out

## 1.4.1

* Pass through allocator instance for PFI blocks

## 1.4.0

* Enable JNI dependencies for desktop 
  * Attach desktop engine runtime JAR to test dependencies/classpath
  * Utilize separate source set root, `jniTest`, for tests with JNI dependencies to better support Android <> Desktop crossplatform development

## 1.3.0

* Android `AtakCertificateDatabaseAdapter` derives from common `AtakCertificateDatabaseAdapterBase`

## 1.2.0

* add in documentation to the AtakAuthenticationDatabaseIFace 
* add in `AtakAuthenticationDatabaseIFace.PERPETUAL` constant -1 for (which mirrors the desired behavior and underlying current impl
* `AtakCertificateDatabaseIFace` and `AtakAuthenticationDatabaseIFace` extend from `Disposable` instead of providing the own definition, helps with both intended behavior as well as proguard issues (where two interfaces define dispose and then obfuscation ends up not correctly obfuscating the method the same way (cant have two names at once)


## 1.1.1

* correct orientation when panning across pole to avoid spinning globe

## 1.1.0

* expose `apache-commons-lang-slim` as API dependency on AAR

## 1.0.4

* Assume user has canceled if CLI `HttpConnectionHandler` does not receive credentials

## 1.0.3

* Add config option `"overlays.default-label-render-resolution"` to control default label render resolution

## 1.0.2
* Update TTP dependency to 2.7.2 to pick up OpenSSL update to address CVE-2022-0778

## 1.0.1

* `GLBatchGeometryFeatureDataStoreRenderer3` uses default icon dimension constraint of `64`
* `GLBatchGeometryFeatureDataStoreRenderer3` pulls icon dimension constraint from _config options_

## 1.0.0

* Upgrade Android to NDK23
* Utilize NDK supplied CMake toolchain file
* Utilize `javac -h` for JNI header generation
* Local compilation compatibility through JDK17
* Remove sources for thirdparty dependencies; replace with managed dependencies or remove without replacement
* Remove deprecated code marked for removal 4.5 or earlier

## 0.63.0

* Added new FeatureDataStore wrappers
  * FeatureDataStoreProxy
  * FeatureDataStoreLruCacheLogic
* OGRFeatureDataStore uses these new FDS wrappers instead of `RuntimeCachingFeatureDataStore`
* Added `atakmap::feature::Envelope::operator==`
* Added `TAK::Engine::Feature::FeatureDataStore2::FeatureQueryParameters::operator==`
* Added `TAK::Engine::Feature::FeatureDataStore2::FeatureSetQueryParameters::operator==`
* Added `TAK::Engine::Feature::FeatureDataStore2` copy constructor
* Fixed issues in `RuntimeFeatureDataStore2`
  * Cursor returned by `RuntimeFeatureDataStore2::queryFeatures` now behaves like other cursors.  Call `moveToNext` before attempting to `get` the first item.
  * Removed `bulkModify` tracking from `RuntimeFeatureDataStore2`.  It's handled by `AbstractFeatureDataStore2`.
  * `RuntimeFeatureDataStore2::insertFeatureImpl` will call `setContentChanged` before returning when `inserted` is true
  * `RuntimeFeatureDataStore2::deleteAllFeaturesImpl` will call `dispatchDataStoreContentChangedNoSync`
* Added `TE_CHECKLOGCONTINUE_CODE` to use in loops when a message should be logged and then continue rather than break

## 0.62.0

* Add _xray color_ property to C++ `SceneInfo`
* C++ scene renderer uses `SceneInfo::xrayColor`

## 0.61.0

* Reimplement `GLLabelManager` text rendering via SDF

## 0.60.0

* Add LOB intersect to Java `GeoCalculations`

## 0.59.3

* Fix submenu inner/outer radius highlight having extra width

## 0.59.2

* Added additional file validation checks for Cesium JSON files

## 0.59.1

* Fix regression with `GLGlobe::lookAt` honoring minimum zoom introduced in `0.42.0`

## 0.59.0

* bump to `jogl@2.2.4` for closer API compatibility with TAKX

## 0.58.5

* Fix logic error causing failure of `Interop.getObject`

## 0.58.4

* Fix `jcameracontroller.cpp` signature for `tiltTo` causing JNI method resolution failure

## 0.58.3

* Address CVEs
  * CVE-2020-15522
  * CVE-2020-28052
  * CVE-2020-13956

## 0.58.2

* Preload mosaic root nodes on background thread

## 0.58.1

* Make Java `NodeContextResources.discardBuffers` instance based to avoid single render thread/context constraint

## 0.58.0

* Add default radial menu icon resources

## 0.57.2

* Make `GroupContactBase` public to resolve JDK limitations on "split packages" across modules.
  This is NOT intended to make the API published, but is required by the JDK.

## 0.57.1

* Deprecate `GroupContact`, `GroupContactBuilder` and `GroupContactBuilderBase`
* Create `GroupContactBuilderBase2`, replacing `GroupContactBuilderBase`
* Fix persistence errors in `AttributeSet` by using JPA annotations vs Hibernate `Lifecycle` methods
* Delete classes from `experimental.chat`, since they were moved out to TAKX 4 months ago.

## 0.56.1

* Support ModelInfo from zip comment for KMZ files containing only one model.

## 0.56.0

* Port updated submenu support for radial

## 0.55.4

* Re-order static initialization for `AtakAuthenticationDatabase`, `AtakCertificateDatabase` and `CertificateManager`

## 0.55.3

* Cleanup debugging for `Unsafe.allocateDirect` API discovery

## 0.55.2

* Handle potential `NullPointerException` in `Unsafe` due to `Class.getClassLoader()` may return `null`

## 0.55.1

* Crash dumps on Windows will attempt to capture full memory.

## 0.55.0

* Add `IPersistable` interface
* Add persistence support for JRE flavor of `GroupContact`
* `IGroupContact` adds _default_ `setParentContact`


## 0.54.5

* `DtedElevationSource` applies _flags_ filter appropriately on query params

## 0.54.4

* fix C++ surface mesh/model depth buffer based hit test

## 0.54.3

* MOBAC sources now correctly support `minZoom` greater than zero

## 0.54.2

* fix C++ `FDB` encoding for overloaded schemas

## 0.54.1

* fix issue with HTTPS redirects causing WMS/WMTS GetCapabilities to timeout for C++/CLI client

## 0.54.0

* port most recent changes to radial menu

## 0.53.3

* Add nullptr check to Tesselate_polygon to fix failing unit test

## 0.53.2

* Apply perspective divide in `GLLinesEmulation`

## 0.53.1

* Disable polygon tessellation threshold based on limited utility versus potential for resource exhaustion.

## 0.53.0

* Implement new C++ feature renderer
* C++/CLI `GLBatchGeometryFeatureDataStoreRenderer` allows for select between old and new C++ impls
* C++ `Tessellate` adds support for callback based processing to eliminate the need for pre-allocated buffers
* Fix bug in C++ `IconPointStyleConstructor` overload for correctly setting absolute vs relative rotation
* C++ `LineString` and `Polygon` allow for direct access to data buffers
* Micro-optimize `TAK::Engine::Port::String` to avoid heap allocations for small strings


## 0.52.2

* Dispatch focus changed on surface resize as focus point is now managed as offset by the renderer, not by the controller/globe

## 0.52.1

* `GLText2` performs non-printable character check AFTER decode

## 0.52.0

* Refactor `TextFormatParams::fontName` `const char *` -> `TAK::Engine::Port::String` to retain ownership over memory

## 0.51.14

* Avoid potential empty viewport specification for surface rendering

## 0.51.13

* Improve C++ C3DT culling for perspective camera

## 0.51.12

* Update batch point rendering & lollipop behavior to render at terrain instead of altitude 0

## 0.51.11

* Mitigate potential crash when path exceeds max path length

## 0.51.10

* keep enums and the inner interface for RenderSurface (exclude them from obfuscation)

## 0.51.9

* Simplify client initialization of engine for Desktop Java deployment

## 0.51.8

* Support SceneInfo from zip comment for KMZ files containing only one model.

## 0.51.7

* Ensure context is attached in `GLScene` hit-test

## 0.51.6

* In SceneLayer::update, failing to write the zip file comment will not prevent the call to dispatchContentChangedNoSync.
* Revert change from 0.50.0 where OGRFeatureDataStore no longer used RuntimeCachingFeatureDataStore but use a maxRenderQueryLimit of 10K.

## 0.51.5

* Consolidate CMake source file definitions

## 0.51.4

* Initialize texture handle to `GL_NONE` in the event that `glGenTextures` fails


## 0.51.3

* Skip attribute assignment if not defined for shader

## 0.51.2

* Pass correct name to `glInvalidateFramebuffer`

## 0.51.1

* Update model's zip comment before calling dispatchContentChangedNoSync.

## 0.51.0

* Java binding for C++ `DtedElevationSource`

## 0.50.2

* Gracefully handle situation where C++ `std::wstring` to CLI `System::String` fails due to an unsupported character.

## 0.50.1

* `GLBaseMap` tile size dropped to 64 pixels. Resolves issue with texture coordinate precision on MPU5 integrated display unit. This should also mitigate fill rate issues on some devices.
* Make atmosphere enabled state configurable via `ConfigOptions`

## 0.50.0

* Fix `GLBatchGeometryFeatureDataStoreRenderer2::checkSpatialFilter` logic for more than one include/exclude filter.
* Overloaded the `RuntimeCachingFeatureDataStore` constructor to allow clients to specify the maxRenderQueryLimit.
* Changed `ZipFile::setGlobalComment` to return TE_IO if `zipOpen` fails to open the zip file.

## 0.49.1

* Fix copy-paste bug in C++ `AtakMapView::getBounds`

## 0.49.0

* Resolve precision issues with ECEF globe by ditching ECEF emulation in favor of packing ECEF verts into terrain tile using a reserved attribute

## 0.48.0

* Upgrade to TTP-Dist 2.6.1
* Minor modifications to support Linux compilation with GCC 4.8.x

## 0.47.0

* prepare for the removal of direct access to the adapter for both AtakAuthenticationDatabase and AtakCredentialDatabase

## 0.46.0

* Add CLI `MobacMapSource2` to expose tile update interval
* `MobacMapSourceFactory` parses out `<tileUpdate>` tag as refresh interval in milliseconds
* implement automatic refresh monitor

## 0.45.2

* Add support for getting the raw url from `MobacMapSource`

## 0.45.1

* Fix off-by-one issue in C++ `MultiLayer2` that would result in failure to move a child layer to the last position.

## 0.45.0

* Add flag for CLI `GLAsynchronousMapRenderable` to allow for better consistency with legacy globe for image overlay selection

## 0.44.3

* Add Android 12 compatible implementation for the Unsafe allocator

## 0.44.2

* Pass through layer transparency setting to renderer for mobile imagery

## 0.44.1

* Use a recursive mutex to guard `AtakMapView` layers/callbacks to allow re-entry from callback

## 0.44.0

* legacy `GLGdalQuadTileNode` now works with globe surface renderer
* make CLI `GLTiledMapLayer` part of public API
* implement legacy adapter for tile reader/spi

## 0.43.0

* expose GLGlobeBase::release() via C++/CLI bindings
* C++ GLMapView2 orderly destruct offscreen FBO in release() or leak from destructor if not on render thread
* implement sharing of OSMDroidContainer instances to mitigate issues with concurrent writes
* Propagate GLGlobeBase::animationTick to GLGlobeBase::State during render pump
* Better define owner for C++/CLI ITileReader in GLTiledMapLayer to allow for positive release
* Resolve issues with out-of-order destruct

## 0.42.3

* Fix int precision issue in `IO_truncate` causing a failure during Cesium conversion

## 0.42.2

* C++/CLI `GLAbstractDataStoreRasterLayer` honors subject visibility setting

## 0.42.1

* When determining number of resolution levels, continue to bisect until both dimensions are reduced to single tile

## 0.42.0

* Refactor `CameraController` to C++
* C++ `AtakMapView` completely defer state to `MapRenderer2`
* `MapSceneModel2` assignment copies `displayDpi`
* Java `RenderSurface` interop
* Java `GLGlobeBase` interop does static init if necessary
* Collision handling becomes implementation detail of `GLGlobeBase` derivative
* Access `SurfaceRendererControl` via `GLGlobeBase::getControl(...)` rather than specialization

## 0.41.0

* Massage location of embedded font files
* Add new resource file describing embedded font files

## 0.39.1

* Give C++ `HttpProtocolHandler` a default timeout of 10s 

## 0.39.0

* Expose font files as resources via `TAK.Engine` assembly

## 0.38.1

* Avoid potential _null_ dereference in CLI `WFSQuery`

## 0.38.0

* Add support for rendering line segments in `GLBatchLineString`

## 0.37.1

* CLI `GeoMag` static constructor passing through wrong address to magnetic model struct

## 0.37.0

* Introduce spatial filters control for features renderer

## 0.36.0

* Minor modifications to support Linux compilation with GCC 4.8.x

## 0.35.0

* Add pre-generated SDF font representations

## 0.34.1

* Call SceneObjectControl's setLocation in onClampToGroundOffsetComputed.

## 0.34.0

* Marshal Java ModelInfo and native SceneInfo.
* Pix4dGeoreferencer.locate will return ModelInfo from ZipCommentGeoreferencer.locate if available.

## 0.33.1

* Fix typo when computing default stroke style in `GLBatchPolygon`

## 0.33.0

* Optimize LAS -> C3DT PNTS conversion
* Support progress callback during LAS -> C3DT PNTS conversion
* `SceneInfo` supports mask for various supported "capabilities"
* Add per-_scene object_ controls

## 0.32.0

* Add `getContact(String)` method to IContactStore, deprecate `containsContact(Contact)`

## 0.31.1

* Prevent a Playstore Crash: NullPointerException GdalLayerInfo

## 0.31.0

* Refactor shader source out of C++ files

## 0.30.0

* Adds CertificateManager.createSSLContext() to do SSLContext creation and initialization in a uniform way across TAK

## 0.29.0

+ Provide a mechanism to construct a safe PullParser that defends against bombs such as a billion laughs

## 0.28.2

* C++/CLI `Tessellate` gracefully handles zero-length array

## 0.28.1

* Proguard friendly fix for addressing reflection regression in `Unsafe.allocateDirect` with Android 29. 

## 0.28.0

* Add `isAcceptableInFilename(character)` method to FileSystemUtils

## 0.27.0

* Add `withParentContact(IGroupContact)` method to GroupContactBuilder to support `IGroupContact::getParentContact()`

## 0.26.2

* `ElMgrTerrainRenderService` unwinds all `ElevationSource::OnContentChangedListener`'s on destruct

## 0.26.1

* Fix potential crash in OGR parsing involving empty geometries. See ENGINE-459

## 0.26.0

* Introduce `OGR_Content2`, implementing `FeatureDataSource2::Content` directly
* `OGR_Content` defers implementation to `OGR_Content2`
* Update bindings/registration to prefer new class

## 0.25.4

* Add `libLAS` nuget as transitive dependency

## 0.25.3

* Ensure proper size of glyph bitmap returned by `ManagedTextFormat2`

## 0.25.2

* Enable _medium_ terrain tile shader for Android x86 and arm32
* Don't discard VBO/IBO on GLTerrainTile in _visible_ list; _visible_ list may remain unchanged but differ from front/back in the event visible tiles cannot be computed

## 0.25.1

* Add `null` check in `GLBatchPolygon.extrudeGeometry` as unsubstantiated fix for issue that could not be reproduced.

## 0.25.0

* Add `IGroupContact.getParentContact()` (as default)

## 0.24.1

* Prevent error when importing non-archive models.

## 0.24.0

* Add SIMD wrapper for NEON
* Implement SIMD raycast intersect computation
* Apply SIMD raycast for terrain raycasts

## 0.23.2

* Fix envelope calculation in SceneLayer.

## 0.23.1

* Handle potential NPE in `GLQuadTileNode.resolveTexture` for reconstruct from children

## 0.23.0

* Allow association of various cached GL state with `RenderContext` instance (e.g. `GLText`)
* `JOGLGLES` allows re-init, supporting both multi-thread and same thread changes of `GLAutoDrawable` instance
* `JOGLRenderContext` installs `GLEventListener` on `GLAutoDrawable` to re-init `JOGLGLES` at the start of every render pump
* `Interop` supports intern/find mechanism
* deprecate duplicate `Interop`

## 0.22.0

* Add initial support for LAS LiDAR file ingest and render

## 0.21.2

* Fix tile index computation for equirectangular projection in `OSMDroidSQLiteMosaicDatabase`

## 0.21.1

* massage GLSL version 100 to version 120
* bump to `android-port@2.0.1`
* bump to `gles-jogl-c@1.0.3`

## 0.21.0

* Define experimental Chat Service API

## 0.20.5

* `ttp-dist@2.5.1`; stripped `libassimp` and `libjassimp`

## 0.20.4

* KML style parsing for OGR specific strings handles multiple attributes

## 0.20.3

* `Shader` declares version for portability on Mac
* `EngineLibrary.init` assigns `appContext` to eliminate warning in `GLBitmapLoader`
* `controlled` bumps to conan `ttp-dist/2.5.0`
* return value from `CameraChangedForwarder::onCameraChanged` to prevent crash on linux
* normalize latitude/longitude in geoid height retrieval to quiet warnings

## 0.20.2

* Fix ZipComment unit tests.

## 0.20.1

* `GLBitmapLoader.mountArchive` accounts for URL path escaped characters

## 0.20.0

* Introduce the IlluminationControl2 and deprecate IlluminationControl

## 0.19.0

* Add ZipCommentGeoreferencer and ZipCommentInfo for model elevation and positioning.

## 0.18.3

* Attempt to mitigate observed inconsistent loading of `libjawt` on linux hosts.

## 0.18.2

* Update ContactStore to use identity-based collections instead of equality-based collections.
* Remove unneeded overrides from AttributeSet in shared/src/jre
* Reintroduce AssertJ usage in AttributeSetTest

## 0.18.1

* Remove hard-coded density multiplier for windows

## 0.18.0

* Define `IlluminationControl`
* Implement directional light source support in terrain tile renderer, based on topocentric coordinates

## 0.17.0

* Add Contacts API in support of a common contact management system

## 0.16.0

* Adds C++ `MapRenderer2` to reflect Java `MapRenderer3` interface, specifically targeting _camera-changed_ event for inclusion
* `AtakMapView` proxies `onCameraChanged` to `onMapMoved` to capture animated map motion, specifically for startup
* `ElMgrTerrainRenderService` only derives tile if source version is strictly less than derived version.

## 0.15.5

* Handle potential access violation by checking for empty DRW geometries on parse

## 0.15.4

* Invalidate hit-testing vertices when points are updated in `GLBatchLineString`

## 0.15.3

* Update WMM (World Magnetic Model) Coefficient file; 2020-2025.

## 0.15.2

* Change out the expired LetsEncrypt DSTRootCAX3.crt (9/30/21)

## 0.15.1

* Call to getRenderer3 after dispose causes a NullPointerException but should return null.

## 0.15.0

* Fix transitive dependency references in publication
* Add additional dependencies to simplify for clients
  * `takkernel-all` references all _non controlled_ JARs (excludes native runtimes)
  * `takkernel-engine-main` references `takkernel-engine` (classfiles) and all native runtimes
  * `takkernel-rt-<platform>` holds platform specific runtime libraries (drop classifier usage)

## 0.14.0

* Add `IScaleWidget2`; defer scale computation wholly to renderer.

## 0.13.0

* Initial _look-from_ implementation.

## 0.12.0

* Export transitive dependencies for `takkernel-engine` on publish 
* Export transitive dependencies for `takkernel-aar` on publish

## 0.11.1

* Replace calls to deprecated `DistanceCalculations` methods with `GeoCalculations`
* Mark additional methods in `DistanceCalculations` as deprecated

## 0.11.0

* Desktop external native build accepts `cmake.config` property to allow CMake configuration type pass through, enabling `Release` builds for windows (default linkage for Windows is `Debug`)

## 0.10.0

* Update TTP runtime to `2.2.0`
  * Exposes `minizip` API

## 0.9.1

* Update property for `TAK.Kernel.Controlled` nuget assembly install directory to avoid conflict with `TAK.Kernel` nuget

## 0.9.0

* Add `DontObfuscate` annotation
* Annotate classes that should be skipped for obfuscation

## 0.8.0

* `TAK.Kernel` nuget package adds dependencies on `ANGLE` and `TTP-Dist`

## 0.7.0

* Fix request for `uPointSize` _uniform_ handle
* Deprecate `Shader.getAPointSize()`; add `Shader.getUPointSize()`
* `GLES20FixedPipeline` uses color value of `0xFFFFFFFF` for point sprite rendering backwards compatibility

## 0.6.1

* Implement marshal for _portable_ `MotionEvent ` to _platform_ `MotionEvent`

## 0.6.0

* add in documentation for FileSystemUtils
* expand isEmpty to allow for Collections instead of just lists

## 0.5.1

* Handle JOGL instantiations that are `GL4bc` without client-array support

## 0.5.0

* Fix inconsitency in featureset _read-only_ state after insert for `TAK::Engine::Feature::FDB`
* Add overload to `TAK::Engine::Feature::FDB::insertFeatureSet(...)` to allow client to specify initial _read-only_ state 
* Fix package name for `TAK.Kernel.Controlled` nuget publication

## 0.4.3

* Fix memory leak in com.atakmap.map.layer.feature.geometry.Geometry



## 0.4.2

* Address potential `ConcurrentModificationException` with hit-testing machinery

## 0.4.1

* Implement caching for `GLText2_intern(const TextFormatParams &)` to address potential memory leak if client not actively managing instances

## 0.4.0

* Add _hover_ callback to `IMapWidget` (e.g. mouse enter, mouse move, mouse exit)

## 0.3.0

* Add `:takkernel:shared`
* Port widgets framework to `:takkernel:shared`

## 0.2.0

* Enable _warning-as-error_ for Windows `:takkernel:engine` native build
* Handle empty meshes
* Add `GeoBounds` builder utility
* Allow additional level of recursion for `tileset.json` for zipped 3D tiles datasets
* Fix logic for `RuntimeFeatureDataStore` feature name queries with wildcard character not first/last character

## 0.1.1

* Remove Win32 targets for `TAK.Engine` and `TAK.TCM`
* Update include/linker path references for `TAK.Engine` and `TAK.TCM`
* Update source code paths in `TAK.Engine` and `TAK.TCM`
* Add powershell scripts to generate `TAK.Kernel` and `TAK.Kernel.Controlled` Nuget packages

## 0.1.0

* Import TAK Globe sources

## 0.0.1

* Change JOGL dependency version `2.3.2` -> `2.1.5-01`
* Change Guiave version `27.0.1-jre` -> `30.0-jre`

## 0.0.0

* Initial Revision.
