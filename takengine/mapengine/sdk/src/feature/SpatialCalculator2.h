#ifndef TAK_ENGINE_FEATURE_SPATIALCALCULATOR2_H_INCLUDED
#define TAK_ENGINE_FEATURE_SPATIALCALCULATOR2_H_INCLUDED

#include <map>

#include "db/Database2.h"
#include "db/Statement2.h"

#include "feature/Geometry2.h"
#include "feature/LineString2.h"

namespace TAK {
    namespace Engine {

        namespace Feature {
            class ENGINE_API Point2;

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
            class ENGINE_API SpatialCalculator2
            {
            public:
            class ENGINE_API Batch;
            public :
            typedef std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> BlobPtr;
            public:
            /**
             * Creates a new instance. If path is non-NULL, the calculator will
             * use a file at the specified location. If path is NULL (defualt),
             * the calculator will be stored in memory.
             */
            SpatialCalculator2(const char *path = nullptr);
            public:
            ~SpatialCalculator2();
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
             * @param handle The database id upon success
             * @param geom   The geometry
             *
             * @return TE_Ok on success, TE_IllegalState, TE_Unsupported on error.
             */
            Util::TAKErr createGeometry(int64_t *handle, const Geometry2 &geom);

            Util::TAKErr createGeometryFromBlob(int64_t *handle, const uint8_t *blob, const std::size_t &len);
            Util::TAKErr createGeometryFromWkb(int64_t *handle, const uint8_t *wkb, const std::size_t &len);
            Util::TAKErr createGeometryFromWkt(int64_t *handle, const char *wkt);

            /**
             * Creates a new quadrilateral polygon in the calculators memory. This
             * method may perform significantly faster than the other polygon creation
             * methods for creating a simple quadrilateral.
             *
             * @param handle  A handle to the polygon created in the calculator's memory.
             * @param a A corner of the quadrilateral
             * @param b A corner of the quadrilateral
             * @param c A corner of the quadrilateral
             * @param d A corner of the quadrilateral
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createPolygon(int64_t *handle, const Point2 &a, const Point2 &b, const Point2 &c, const Point2 &d);

            /**
             * Deletes the specified geometry from the calculator's memory.
             *
             * @param handle    The handle to the geometry in the calculator's memory.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr deleteGeometry(const int64_t &handle);

            Util::TAKErr getGeometryType(GeometryClass *geom_type, const int64_t &handle);

            /**
             * Returns the specified geometry from the calculator's memory as a
             * SpataiLite blob.
             *
             * @param blob  The specified geometry from the calculator's memory as a
             *              SpataiLite blob.
             * @param len   The length in bytes of the assigned geometry blob
             * @param handle    The handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             *
             * @see http://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html
             */
            Util::TAKErr getGeometryAsBlob(BlobPtr &blob, std::size_t *len, const int64_t &handle);

            /**
             * Returns the specified geometry from the calculator's memory as a
             * Well-Known Text string.
             *
             * @param wkt  The specified geometry from the calculator's memory as a
             *          Well-Known Text string
             * @param handle    The handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr getGeometryAsWkt(TAK::Engine::Port::String *wkt, const int64_t &handle);

            /**
             * Returns the specified geometry from the calculator's memory.
             *
             * @param geom  The specified geometry from the calculator's memory
             * @param handle    The handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr getGeometry(Geometry2Ptr &geom, const int64_t &handle);

            /**
             * Tests the two geometries for intersection.
             *
             * @param intersected  <code>true</code> if the two geometries intersect,
             *          <code>false</code> otherwise.
             * @param geom1 A handle to the geometry in the calculator's memory
             * @param geom2 A handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr intersects(bool *intersected, const int64_t &geom1, const int64_t &geom2);

            /**
             * Tests the two geometries for containment.
             *
             * @param contained  <code>true</code> if <code>geom1</code> contains
             *          <code>geom2</code>, <code>false</code> otherwise.
             * @param geom1 A handle to the geometry in the calculator's memory
             * @param geom2 A handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr contains(bool *contained, const int64_t &geom1, const int64_t &geom2);

            /**
             * Returns the intersection of the specified geometries as a new geometry in
             * the calculator's memory.
             *
             * @param handle  A handle to the new geometry that is the intersection of the
             *          specified geometries.
             * @param geom1 A handle to the geometry in the calculator's memory
             * @param geom2 A handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createIntersection(int64_t *handle, const int64_t& geom1, const int64_t& geom2);

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
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateIntersection(const int64_t &geom1, const int64_t &geom2, const int64_t &result);

            /**
             * Provides the union of the specified geometries as a new geometry in the
             * calculator's memory.
             *
             * @param result  A handle to the new geometry that is the union of the specified
             *                geometries.
             * @param geom1 A handle to the geometry in the calculator's memory
             * @param geom2 A handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createUnion(int64_t *result, const int64_t &geom1, const int64_t &geom2);

            /**
             * Performs the union operation on the specified geometries and stores the
             * result in the specified memory location.
             *
             * @param geom1     A handle to the geometry in the calculator's memory
             * @param geom2     A handle to the geometry in the calculator's memory
             * @param result    The memory location to store the union result. This
             *                  location must already exist. The same value as
             *                  <code>geom1</code> or <code>geom2</code> may be
             *                  specified in which case the old geometry will be
             *                  overwritten with the result.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateUnion(const int64_t &geom1, const int64_t &geom2, const int64_t &result);

            /**
             * Computes the unary union of a GeometryCollection2.
             *
             * @param result  The unary union result
             * @param geom    The handle to the GeometryCollection2
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createUnaryUnion(int64_t *result, const int64_t &geom);

            /**
             * Computes the unary union of a GeometryCollection2.
             *
             * @param geom    The handle to the GeometryCollection2
             * @param result    The memory location to store the union result. This
             *                  location must already exist. The same value as
             *                  <code>geom</code> may be specified in which case
             *                  the old geometry will be overwritten with the result.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateUnaryUnion(const int64_t &geom, const int64_t &result);

            /**
             * Returns the difference of the specified geometries as a new geometry in
             * the calculator's memory.
             *
             * @param result  A handle to the new geometry that is the difference of the
             *                specified geometries.
             * @param geom1 A handle to the geometry in the calculator's memory
             * @param geom2 A handle to the geometry in the calculator's memory
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createDifference(int64_t *result, const int64_t &geom1, const int64_t &geom2);

            /**
             * Performs the difference operation on the specified geometries and stores
             * the result in the specified memory location.
             *
             * @param geom1     A handle to the geometry in the calculator's memory
             * @param geom2     A handle to the geometry in the calculator's memory
             * @param result    The memory location to store the difference result. This
             *                  location must already exist. The same value as
             *                  <code>geom1</code> or <code>geom2</code> may be
             *                  specified in which case the old geometry will be
             *                  overwritten with the result.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateDifference(const int64_t &geom1, const int64_t &geom2, const int64_t &result);

            /**
             * Returns the simplification of the specified geometry as a new geometry in
             * the calculator's memory.
             *
             * @param result  A handle to the new geometry that is the simplification of the
             *          specified geometry.
             * @param handle          A handle to the geometry to be simplified
             * @param tolerance         The simplification tolerance, in degrees
             * @param preserveTopology  <code>true</code> to preserve topology,
             *                          <code>false</code> otherwise.
             *
             * @return TE_Ok on success, other on error.
             */

            Util::TAKErr createSimplify(int64_t *result, const int64_t &handle, const double &tolerance, const bool &preserveTopology);

            /**
             * Performs the simplification operation on the specified geometry and
             * stores the result in the specified memory location.
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
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateSimplify(const int64_t &handle, const double &tolerance, const bool &preserveTopology, const int64_t &result);

            /**
             * Returns the simplification of the specified linestring as a new
             * linestring.
             *
             * @param result            The new linestring that is the simplification of the specified
             *                          linestring.
             * @param linestring        A linestring
             * @param tolerance         The simplification tolerance, in degrees
             * @param preserveTopology  <code>true</code> to preserve topology,
             *                          <code>false</code> otherwise.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createSimplify(LineString2Ptr &result, const LineString2 &linestring, const double &tolerance, const bool &preserveTopology);

            /**
             * Returns the buffer of the specified geometry as a new geometry in the
             * calculator's memory.
             *
             * @param result    A handle to the new geometry that is the buffer of the specified
             *                  geometry.
             * @param handle    A handle to the geometry to be buffered
             * @param dist      The buffer distance, in degrees
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr createBuffer(int64_t *result, const int64_t &handle, const double &dist);

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
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateBuffer(const int64_t &handle, const double &dist, const int64_t &result);


            /**************************************************************************/

            /**
             * Update a geometry in the calculator's memory.
             *
             * @param geom      The new geometry
             * @param handle    A handle to the geometry created in the calculator's memory.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateGeometry(const Geometry2 &geom, const int64_t &handle);

            /**
             * Update a geometry in the calculator's memory.
             *
             * @param blob      The new geometry as binary blob
             * @param len       The length in bytes of the binary blob
             * @param handle    A handle to the geometry created in the calculator's memory.
             *
             * @return TE_Ok on success, other on error.
             */
            Util::TAKErr updateGeometry(const uint8_t *blob, const std::size_t &len, const int64_t &handle);

            Util::TAKErr getGEOSVersion(TAK::Engine::Port::String *version);

            private:
            Util::TAKErr createGeometry(int64_t *handle, const uint8_t *blob, const std::size_t &len);
            private:
            DB::DatabasePtr database;

            DB::StatementPtr insertGeomWkb;
            DB::StatementPtr insertGeomWkt;
            DB::StatementPtr insertGeomBlob;
            DB::StatementPtr bufferInsert;
            DB::StatementPtr bufferUpdate;
            DB::StatementPtr intersectionInsert;
            DB::StatementPtr intersectionUpdate;
            DB::StatementPtr unionInsert;
            DB::StatementPtr unionUpdate;
            DB::StatementPtr unaryUnionInsert;
            DB::StatementPtr unaryUnionUpdate;
            DB::StatementPtr differenceInsert;
            DB::StatementPtr differenceUpdate;
            DB::StatementPtr simplifyInsert;
            DB::StatementPtr simplifyUpdate;
            DB::StatementPtr simplifyPreserveTopologyInsert;
            DB::StatementPtr simplifyPreserveTopologyUpdate;
            DB::StatementPtr clearMem;
            DB::StatementPtr deleteGeom;

            DB::StatementPtr updateGeomBlob;
            DB::StatementPtr updateGeomWkb;
            DB::StatementPtr updateGeomWkt;
            };

            class ENGINE_API SpatialCalculator2::Batch : public TAK::Engine::Util::NonCopyable
            {
            public:
                Batch(SpatialCalculator2 &calc);
                ~Batch();
            public:
                void setSuccessful();
            private:
                SpatialCalculator2 &calc;
                bool success;
            };
        }
    }
}

#endif
