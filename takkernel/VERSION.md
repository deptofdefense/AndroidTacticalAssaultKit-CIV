# Version History

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
