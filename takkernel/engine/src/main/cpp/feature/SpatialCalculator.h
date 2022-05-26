#ifndef ATAKMAP_FEATURE_SPATIALCALCULATOR_H_INCLUDED
#define ATAKMAP_FEATURE_SPATIALCALCULATOR_H_INCLUDED

#include "feature/Geometry.h"
#include "feature/QuadBlob.h"
#include "port/Platform.h"
#include "util/NonCopyable.h"

#include <cstdint>

#include <memory>

namespace atakmap {
    namespace db {
        class ENGINE_API Database;
        class ENGINE_API Statement;
    }

    namespace feature {
        class ENGINE_API Point;
        class ENGINE_API LineString;
        class ENGINE_API Polygon;
        class ENGINE_API GeometryCollection;

        /**
        * Spatial calculator backed by SpatiaLite. The calculator will require both
        * memory and disk resources. It is recommended that the {@link #dispose()}
        * method be invoked when the calculator will no longer be used to immediately
        * release all allocated resources. In the event that {@link #dispose()} is not
        * explicitly invoked, the resources will be released on finalization.
        *
        * <H2>Workflow</H2>
        *
        * <P>The general workflow for the calculator will be to create geometries in
        * the calculator's memory and then perform various spatial operations on those
        * geometries. When a geometry is created, a handle to that geometry in the
        * calculator's memory is returned. The geometry may be accessed (e.g. used as
        * an argument to a function) via the memory handle. The calculator's memory
        * may be cleared at any time by invoking the method, {@link #clear()}. When the
        * memory is cleared all existing geometries handles are invalidated. Attempting
        * to use an invalid handle will produce undefined results.
        *
        * <P>The calculator makes use of two mechanisms to improve performance. The
        * first mechanism is caching. Data structures and instructions are cached to
        * reduce general overhead. The user may clear the cache at any time by invoking
        * the method, {@link #clearCache()}. If a reference to the calculator is going
        * to be maintained for a int64_t time but its use is infrequent it may be
        * appropriate to clear the cache between uses. The second mechanism is
        * batching. Batching instructs the calculator to perform all instructions
        * without performing an actual commit to the calculator memory until the batch
        * has been completed. This can significantly improve performance when
        * instructions are being executed in high volume or frequency. All instructions
        * given to the calculator within the batch will produce valid results, however,
        * all instructions issued during the batch are not actually committed to the
        * calculator's memory until {@link #endBatch(bool)} is invoked. If
        * {@link #endBatch(bool)} is invoked with an argument of <code>false</code>
        * all instructions issued during the batch are undone and the calculator is in
        * the same state as it was prior to the batch.
        *
        * <H2>Thread Safety</H2>
        *
        * <P>This class is <B>NOT</B> thread-safe. Care needs to be taken to ensure
        * that methods are invoked in a thread-safe manner. It is strongly recommended
        * that each instance only be used on a single thread.
        *
        * <P>Special care must be taken when the calculator is in batch mode. When in
        * batch mode, instructions may only be issued to the calculator on the thread
        * that the batch was started in.
        *
        * @author Developer
        */
        class ENGINE_API SpatialCalculator
        {
        public :
            typedef std::pair<const uint8_t *, const uint8_t *> Blob;
        public :
            class ENGINE_API Batch;
        public:
            /**
            * Creates a new instance. If path is non-NULL, the calculator will
            * use a file at the specified location. If path is NULL (defualt),
            * the calculator will be stored in memory.
            */
            SpatialCalculator(const char *path = nullptr);
        public:
            ~SpatialCalculator();
        public:
            /**
            * Clears the calculator instruction cache.
            */
            void clearCache();

            /**
            * Clears the calculator's memory. Any previously obtained geometry handles
            * must be considered invalid.
            */
            void clear();

            /**
            * Begins an instruction batch. The calculator will not actually commit any
            * instructions issued to memory until the batch is completed via
            * {@link #endBatch(bool)}. During the batch, any created or modified
            * handles will reside in a special volatile memory available to all
            * functions. If the batch is ended successfully, the contents of the
            * volatile memory will be merged with the calculator's memory, otherwise
            * the volatile memory will be released effectively undoing all instructions
            * issued during the batch.
            *
            * <P>Once a batch is started, instructions may only be issued to the
            * calculator on the thread that the batch was started on.
            */
            void beginBatch();

            /**
            * Ends the current batch. If <code>commit</code> is <code>true</code> all
            * instructions issued during the batch are committed to the calculator's
            * memory.
            *
            * @param commit    <code>true</code> to commit the batch instructions,
            *                  <code>false</code> to undo.
            */
            void endBatch(bool commit);

            /**
            * Creates a new geometry in the calculator's memory.
            *
            * @param geom   The geometry
            *
            * @return  A handle to the geometry created in the calculator's memory.
            */
            int64_t createGeometry(const Geometry *geom);

            int64_t createGeometryFromBlob(const Blob &blob);
            int64_t createGeometryFromWkb(const Blob &wkb);
            int64_t createGeometryFromWkt(const char *wkt);

            /**
            * Creates a new quadrilateral polygon in the calculators memory. This
            * method may perform significantly faster than the other polygon creation
            * methods for creating a simple quadrilateral.
            *
            * @param a A corner of the quadrilateral
            * @param b A corner of the quadrilateral
            * @param c A corner of the quadrilateral
            * @param d A corner of the quadrilateral
            *
            * @return  A handle to the polygon created in the calculator's memory.
            */
            int64_t createPolygon(Point *a, Point *b, Point *c, Point *d);

            /**
            * Deletes the specified geometry from the calculator's memory.
            *
            * @param handle    The handle to the geometry in the calculator's memory.
            */
            void deleteGeometry(int64_t handle);

            Geometry::Type getGeometryType(int64_t handle);

            /**
            * Returns the specified geometry from the calculator's memory as a
            * SpataiLite blob.
            *
            * @param handle    The handle to the geometry in the calculator's memory
            *
            * @return  The specified geometry from the calculator's memory as a
            *          SpataiLite blob.
            *
            * @see http://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html
            */
            Blob getGeometryAsBlob(int64_t handle);

            /**
            * Returns the specified geometry from the calculator's memory as a
            * Well-Known Text string.
            *
            * @param handle    The handle to the geometry in the calculator's memory
            *
            * @return  The specified geometry from the calculator's memory as a
            *          Well-Known Text string
            */
            const char *getGeometryAsWkt(int64_t handle);

            /**
            * Returns the specified geometry from the calculator's memory.
            *
            * @param handle    The handle to the geometry in the calculator's memory
            *
            * @return  The specified geometry from the calculator's memory
            */
            Geometry *getGeometry(int64_t handle);

            /**
            * Tests the two geometries for intersection.
            *
            * @param geom1 A handle to the geometry in the calculator's memory
            * @param geom2 A handle to the geometry in the calculator's memory
            *
            * @return  <code>true</code> if the two geometries intersect,
            *          <code>false</code> otherwise.
            */
            bool intersects(int64_t geom1, int64_t geom2);

            /**
            * Returns <code>true</code> if <code>geom1</code> contains
            * <code>geom2</code>.
            *
            * @param geom1 A handle to the geometry in the calculator's memory
            * @param geom2 A handle to the geometry in the calculator's memory
            *
            * @return  <code>true</code> if <code>geom1</code> contains
            *          <code>geom2</code>, <code>false</code> otherwise.
            */
            bool contains(int64_t geom1, int64_t geom2);

            /**
            * Returns the intersection of the specified geometries as a new geometry in
            * the calculator's memory.
            *
            * @param geom1 A handle to the geometry in the calculator's memory
            * @param geom2 A handle to the geometry in the calculator's memory
            *
            * @return  A handle to the new geometry that is the intersection of the
            *          specified geometries.
            */
            int64_t intersection(int64_t geom1, int64_t geom2);

            /**
            * Performs the intersection operation on the specified geometries and
            * stores the result in the specified memory location. This method may
            * perform significantly faster than {@link #intersection(int64_t, int64_t)}.
            *
            * @param geom1     A handle to the geometry in the calculator's memory
            * @param geom2     A handle to the geometry in the calculator's memory
            * @param result    The memory location to store the intersection result.
            *                  this location must already exist. The same value as
            *                  <code>geom1</code> or <code>geom2</code> may be
            *                  specified in which case the old geometry will be
            *                  overwritten with the result.
            */
            void intersection(int64_t geom1, int64_t geom2, int64_t result);

            /**
            * Returns the union of the specified geometries as a new geometry in the
            * calculator's memory.
            *
            * @param geom1 A handle to the geometry in the calculator's memory
            * @param geom2 A handle to the geometry in the calculator's memory
            *
            * @return  A handle to the new geometry that is the union of the specified
            *          geometries.
            */
            int64_t gunion(int64_t geom1, int64_t geom2);

            /**
            * Performs the union operation on the specified geometries and stores the
            * result in the specified memory location. This method may perform
            * significantly faster than {@link #union(int64_t, int64_t)}.
            *
            * @param geom1     A handle to the geometry in the calculator's memory
            * @param geom2     A handle to the geometry in the calculator's memory
            * @param result    The memory location to store the union result. This
            *                  location must already exist. The same value as
            *                  <code>geom1</code> or <code>geom2</code> may be
            *                  specified in which case the old geometry will be
            *                  overwritten with the result.
            */
            void gunion(int64_t geom1, int64_t geom2, int64_t result);

            int64_t unaryUnion(int64_t geom);

            /**
            * Computes the unary union of a GeometryCollection.
            *
            * @param geom  The handle to the GeometryCollection
            *
            * @return  The unary union result
            */
            void unaryUnion(int64_t geom, int64_t result);

            /**
            * Returns the difference of the specified geometries as a new geometry in
            * the calculator's memory.
            *
            * @param geom1 A handle to the geometry in the calculator's memory
            * @param geom2 A handle to the geometry in the calculator's memory
            *
            * @return  A handle to the new geometry that is the difference of the
            *          specified geometries.
            */
            int64_t difference(int64_t geom1, int64_t geom2);

            /**
            * Performs the difference operation on the specified geometries and stores
            * the result in the specified memory location. This method may perform
            * significantly faster than {@link #difference(int64_t, int64_t)}.
            *
            * @param geom1     A handle to the geometry in the calculator's memory
            * @param geom2     A handle to the geometry in the calculator's memory
            * @param result    The memory location to store the difference result. This
            *                  location must already exist. The same value as
            *                  <code>geom1</code> or <code>geom2</code> may be
            *                  specified in which case the old geometry will be
            *                  overwritten with the result.
            */
            void difference(int64_t geom1, int64_t geom2, int64_t result);

            /**
            * Returns the simplification of the specified geometry as a new geometry in
            * the calculator's memory.
            *
            * @param handle            A handle to the geometry to be simplified
            * @param tolerance         The simplification tolerance, in degrees
            * @param preserveTopology  <code>true</code> to preserve topology,
            *                          <code>false</code> otherwise.
            *
            * @return  A handle to the new geometry that is the simplification of the
            *          specified geometry.
            */
            int64_t simplify(int64_t handle, double tolerance, bool preserveTopology);

            /**
            * Performs the simplification operation on the specified geometry and
            * stores the result in the specified memory location. This method may
            * perform significantly faster than
            * {@link #simplify(int64_t, double, bool)}.
            *
            * @param handle            A handle to the geometry to be simplified
            * @param tolerance         The buffer distance, in degrees
            * @param preserveTopology  <code>true</code> to preserve topology,
            *                          <code>false</code> otherwise.
            * @param result            The memory location to store the simplification
            *                          of <code>handle</code>. This location must
            *                          already exist. The same value as
            *                          <code>handle</code> may be specified in which
            *                          case the old geometry will be overwritten with
            *                          the result.
            */
            void simplify(int64_t handle, double tolerance, bool preserveTopology, int64_t result);

            /**
            * Returns the simplification of the specified linestring as a new
            * linestring.
            *
            * @param linestring        A linestring
            * @param tolerance         The simplification tolerance, in degrees
            * @param preserveTopology  <code>true</code> to preserve topology,
            *                          <code>false</code> otherwise.
            *
            * @return  The new linestring that is the simplification of the specified
            *          linestring.
            */
            LineString *simplify(LineString *linestring, double tolerance, bool preserveTopology);

            /**
            * Returns the buffer of the specified geometry as a new geometry in the
            * calculator's memory.
            *
            * @param handle    A handle to the geometry to be buffered
            * @param dist      The buffer distance, in degrees
            *
            * @return  A handle to the new geometry that is the buffer of the specified
            *          geometry.
            */
            int64_t buffer(int64_t handle, double dist);

            /**
            * Performs the buffer operation on the specified geometry and stores the
            * result in the specified memory location. This method may perform
            * significantly faster than {@link #buffer(int64_t, double)}.
            *
            * @param handle    A handle to the geometry to be buffered
            * @param dist      The buffer distance, in degrees
            * @param result    The memory location to store the buffer of
            *                  <code>handle</code>. This location must already exist.
            *                  The same value as <code>handle</code> may be specified
            *                  in which case the old geometry will be overwritten with
            *                  the result.
            */
            void buffer(int64_t handle, double dist, int64_t result);


            /**************************************************************************/

            /**
            * Update a geometry in the calculator's memory.
            *
            * @param handle    A handle to the geometry created in the calculator's memory.
            * @param polygon   The new geometry
            */
            void updateGeometry(const int64_t handle, const Geometry *geom);

            /**
            * Update a geometry in the calculator's memory.
            *
            * @param handle    A handle to the geometry created in the calculator's memory.
            * @param blob      The new geometry
            */
            void updateGeometry(int64_t handle, Blob blob);
        public:
            static void destroyBlob(Blob blob);
            static void destroyGeometry(const Geometry *geometry);
            static void destroyWkt(const char *wkt);
        private:
            int64_t createGeometry(Blob blob);
        private:
            std::auto_ptr<atakmap::db::Database> database;

            QuadBlob quad;

            std::auto_ptr<atakmap::db::Statement> insertGeomWkt;
            std::auto_ptr<atakmap::db::Statement> insertGeomBlob;
            std::auto_ptr<atakmap::db::Statement> bufferInsert;
            std::auto_ptr<atakmap::db::Statement> bufferUpdate;
            std::auto_ptr<atakmap::db::Statement> intersectionInsert;
            std::auto_ptr<atakmap::db::Statement> intersectionUpdate;
            std::auto_ptr<atakmap::db::Statement> unionInsert;
            std::auto_ptr<atakmap::db::Statement> unionUpdate;
            std::auto_ptr<atakmap::db::Statement> unaryUnionInsert;
            std::auto_ptr<atakmap::db::Statement> unaryUnionUpdate;
            std::auto_ptr<atakmap::db::Statement> differenceInsert;
            std::auto_ptr<atakmap::db::Statement> differenceUpdate;
            std::auto_ptr<atakmap::db::Statement> simplifyInsert;
            std::auto_ptr<atakmap::db::Statement> simplifyUpdate;
            std::auto_ptr<atakmap::db::Statement> simplifyPreserveTopologyInsert;
            std::auto_ptr<atakmap::db::Statement> simplifyPreserveTopologyUpdate;
            std::auto_ptr<atakmap::db::Statement> clearMem;
            std::auto_ptr<atakmap::db::Statement> deleteGeom;

            std::auto_ptr<atakmap::db::Statement> updateGeomBlob;
            std::auto_ptr<atakmap::db::Statement> updateGeomWkt;
        };

        class ENGINE_API SpatialCalculator::Batch : public TAK::Engine::Util::NonCopyable
        {
        public :
            Batch(SpatialCalculator *calc);
            ~Batch();
        public :
            void setSuccessful();
        private :
            void *operator new(size_t size);
            void *operator new[](size_t size);
            void operator delete(void *ptr);
            void operator delete[](void *ptr);
        private :
            SpatialCalculator *calc;
            bool success;
        };
    }
}

#endif
