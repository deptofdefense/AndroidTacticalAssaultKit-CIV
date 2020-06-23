/*
* Portions Copyright (C) 2003-2006 Sun Microsystems, Inc.
* All rights reserved.
*/

/*
** License Applicability. Except to the extent portions of this file are
** made subject to an alternative license as permitted in the SGI Free
** Software License B, Version 1.1 (the "License"), the contents of this
** file are subject only to the provisions of the License. You may not use
** this file except in compliance with the License. You may obtain a copy
** of the License at Silicon Graphics, Inc., attn: Legal Services, 1600
** Amphitheatre Parkway, Mountain View, CA 94043-1351, or at:
**
** http://oss.sgi.com/projects/FreeB
**
** Note that, as provided in the License, the Software is distributed on an
** "AS IS" basis, with ALL EXPRESS AND IMPLIED WARRANTIES AND CONDITIONS
** DISCLAIMED, INCLUDING, WITHOUT LIMITATION, ANY IMPLIED WARRANTIES AND
** CONDITIONS OF MERCHANTABILITY, SATISFACTORY QUALITY, FITNESS FOR A
** PARTICULAR PURPOSE, AND NON-INFRINGEMENT.
**
** NOTE:  The Original Code (as defined below) has been licensed to Sun
** Microsystems, Inc. ("Sun") under the SGI Free Software License B
** (Version 1.1), shown above ("SGI License").   Pursuant to Section
** 3.2(3) of the SGI License, Sun is distributing the Covered Code to
** you under an alternative license ("Alternative License").  This
** Alternative License includes all of the provisions of the SGI License
** except that Section 2.2 and 11 are omitted.  Any differences between
** the Alternative License and the SGI License are offered solely by Sun
** and not by SGI.
**
** Original Code. The Original Code is: OpenGL Sample Implementation,
** Version 1.2.1, released January 26, 2000, developed by Silicon Graphics,
** Inc. The Original Code is Copyright (c) 1991-2000 Silicon Graphics, Inc.
** Copyright in any portions created by third parties is as indicated
** elsewhere herein. All Rights Reserved.
**
** Additional Notice Provisions: The application programming interfaces
** established by SGI in conjunction with the Original Code are The
** OpenGL(R) Graphics System: A Specification (Version 1.2.1), released
** April 1, 1999; The OpenGL(R) Graphics System Utility Library (Version
** 1.3), released November 4, 1998; and OpenGL(R) Graphics with the X
** Window System(R) (Version 1.3), released October 19, 1998. This software
** was created using the OpenGL(R) version 1.2.1 Sample Implementation
** published by SGI, but has not been independently verified as being
** compliant with the OpenGL(R) version 1.2.1 Specification.
**
** Author: Eric Veach, July 1994
** Java Port: Pepijn Van Eeckhoudt, July 2003
** Java Port: Nathan Parker Burg, August 2003
*/
package gov.nasa.worldwind.util.glu;

import gov.nasa.worldwind.util.glu.error.Error;
import gov.nasa.worldwind.util.glu.tessellator.GLUtessellatorImpl;

/**
  * Provides access to the OpenGL Utility Library (GLU). This library
  * provides standard methods for setting up view volumes, building
  * mipmaps and performing other common operations.  The GLU NURBS
  * routines are not currently exposed.
  * 
  * <P>
  *
  * Notes from the Reference Implementation for this class:
  * Thanks to the contributions of many individuals, this class is a
  * pure Java port of SGI's original C sources. All of the projection,
  * mipmap, scaling, and tessellation routines that are exposed are
  * compatible with the GLU 1.3 specification. The GLU NURBS routines
  * are not currently exposed.
  */
public class GLU 
{
  public static String gluErrorString(int errorCode) {
    return Error.gluErrorString(errorCode);
  }

  //----------------------------------------------------------------------
  // Tessellation routines
  //
  
  /*****************************************************************************
   * <b>gluNewTess</b> creates and returns a new tessellation object.  This
   * object must be referred to when calling tesselation methods.  A return
   * value of null means that there was not enough memeory to allocate the
   * object.
   *
   * @return A new tessellation object.
   *
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   * @see #gluDeleteTess       gluDeleteTess
   * @see #gluTessCallback     gluTessCallback
   ****************************************************************************/
  public static GLUtessellator gluNewTess() {
      return GLUtessellatorImpl.gluNewTess();
  }
  
  /*****************************************************************************
   * <b>gluDeleteTess</b> destroys the indicated tessellation object (which was
   * created with {@link #gluNewTess gluNewTess}).
   *
   * @param tessellator
   *        Specifies the tessellation object to destroy.
   *
   * @see #gluNewTess      gluNewTess
   * @see #gluTessCallback gluTessCallback
   ****************************************************************************/
  public static void gluDeleteTess(GLUtessellator tessellator) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluDeleteTess();
  }
  
  /*****************************************************************************
   * <b>gluTessProperty</b> is used to control properites stored in a
   * tessellation object.  These properties affect the way that the polygons are
   * interpreted and rendered.  The legal value for <i>which</i> are as
   * follows:<P>
   *
   * <b>GLU_TESS_WINDING_RULE</b>
   * <UL>
   *   Determines which parts of the polygon are on the "interior".
   *   <em>value</em> may be set to one of
   *   <BR><b>GLU_TESS_WINDING_ODD</b>,
   *   <BR><b>GLU_TESS_WINDING_NONZERO</b>,
   *   <BR><b>GLU_TESS_WINDING_POSITIVE</b>, or
   *   <BR><b>GLU_TESS_WINDING_NEGATIVE</b>, or
   *   <BR><b>GLU_TESS_WINDING_ABS_GEQ_TWO</b>.<P>
   *
   *   To understand how the winding rule works, consider that the input
   *   contours partition the plane into regions.  The winding rule determines
   *   which of these regions are inside the polygon.<P>
   *
   *   For a single contour C, the winding number of a point x is simply the
   *   signed number of revolutions we make around x as we travel once around C
   *   (where CCW is positive).  When there are several contours, the individual
   *   winding numbers are summed.  This procedure associates a signed integer
   *   value with each point x in the plane.  Note that the winding number is
   *   the same for all points in a single region.<P>
   *
   *   The winding rule classifies a region as "inside" if its winding number
   *   belongs to the chosen category (odd, nonzero, positive, negative, or
   *   absolute value of at least two).  The previous GLU tessellator (prior to
   *   GLU 1.2) used the "odd" rule.  The "nonzero" rule is another common way
   *   to define the interior.  The other three rules are useful for polygon CSG
   *   operations.
   * </UL>
   * <BR><b>GLU_TESS_BOUNDARY_ONLY</b>
   * <UL>
   *   Is a boolean value ("value" should be set to GL_TRUE or GL_FALSE). When
   *   set to GL_TRUE, a set of closed contours separating the polygon interior
   *   and exterior are returned instead of a tessellation.  Exterior contours
   *   are oriented CCW with respect to the normal; interior contours are
   *   oriented CW. The <b>GLU_TESS_BEGIN</b> and <b>GLU_TESS_BEGIN_DATA</b>
   *   callbacks use the type GL_LINE_LOOP for each contour.
   * </UL>
   * <BR><b>GLU_TESS_TOLERANCE</b>
   * <UL>
   *   Specifies a tolerance for merging features to reduce the size of the
   *   output. For example, two vertices that are very close to each other
   *   might be replaced by a single vertex.  The tolerance is multiplied by the
   *   largest coordinate magnitude of any input vertex; this specifies the
   *   maximum distance that any feature can move as the result of a single
   *   merge operation.  If a single feature takes part in several merge
   *   operations, the toal distance moved could be larger.<P>
   *
   *   Feature merging is completely optional; the tolerance is only a hint.
   *   The implementation is free to merge in some cases and not in others, or
   *   to never merge features at all.  The initial tolerance is 0.<P>
   *
   *   The current implementation merges vertices only if they are exactly
   *   coincident, regardless of the current tolerance.  A vertex is spliced
   *   into an edge only if the implementation is unable to distinguish which
   *   side of the edge the vertex lies on.  Two edges are merged only when both
   *   endpoints are identical.
   * </UL>
   *
   * @param tessellator
   *        Specifies the tessellation object created with
   *        {@link #gluNewTess gluNewTess}
   * @param which
   *        Specifies the property to be set.  Valid values are
   *        <b>GLU_TESS_WINDING_RULE</b>, <b>GLU_TESS_BOUNDARDY_ONLY</b>,
   *        <b>GLU_TESS_TOLERANCE</b>.
   * @param value
   *        Specifices the value of the indicated property.
   *
   * @see #gluGetTessProperty gluGetTessProperty
   * @see #gluNewTess         gluNewTess
   ****************************************************************************/
  public static void gluTessProperty(GLUtessellator tessellator, int which, double value) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessProperty(which, value);
  }
  
  /*****************************************************************************
   * <b>gluGetTessProperty</b> retrieves properties stored in a tessellation
   * object.  These properties affect the way that tessellation objects are
   * interpreted and rendered.  See the
   * {@link #gluTessProperty gluTessProperty} reference
   * page for information about the properties and what they do.
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   * @param which
   *        Specifies the property whose value is to be fetched. Valid values
   *        are <b>GLU_TESS_WINDING_RULE</b>, <b>GLU_TESS_BOUNDARY_ONLY</b>,
   *        and <b>GLU_TESS_TOLERANCES</b>.
   * @param value
   *        Specifices an array into which the value of the named property is
   *        written.
   *
   * @see #gluNewTess      gluNewTess
   * @see #gluTessProperty gluTessProperty
   ****************************************************************************/
  public static void gluGetTessProperty(GLUtessellator tessellator, int which, double[] value, int value_offset) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluGetTessProperty(which, value, value_offset);
  }
  
  /*****************************************************************************
   * <b>gluTessNormal</b> describes a normal for a polygon that the program is
   * defining. All input data will be projected onto a plane perpendicular to
   * the one of the three coordinate axes before tessellation and all output
   * triangles will be oriented CCW with repsect to the normal (CW orientation
   * can be obtained by reversing the sign of the supplied normal).  For
   * example, if you know that all polygons lie in the x-y plane, call
   * <b>gluTessNormal</b>(tess, 0.0, 0.0, 0.0) before rendering any polygons.<P>
   *
   * If the supplied normal is (0.0, 0.0, 0.0)(the initial value), the normal
   * is determined as follows.  The direction of the normal, up to its sign, is
   * found by fitting a plane to the vertices, without regard to how the
   * vertices are connected.  It is expected that the input data lies
   * approximately in the plane; otherwise, projection perpendicular to one of
   * the three coordinate axes may substantially change the geometry.  The sign
   * of the normal is chosen so that the sum of the signed areas of all input
   * contours is nonnegative (where a CCW contour has positive area).<P>
   *
   * The supplied normal persists until it is changed by another call to
   * <b>gluTessNormal</b>.
   *
   * @param tessellator
   *        Specifies the tessellation object (created by
   *        {@link #gluNewTess gluNewTess}).
   * @param x
   *        Specifies the first component of the normal.
   * @param y
   *        Specifies the second component of the normal.
   * @param z
   *        Specifies the third component of the normal.
   *
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   * @see #gluTessEndPolygon   gluTessEndPolygon
   ****************************************************************************/
  public static void gluTessNormal(GLUtessellator tessellator, double x, double y, double z) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessNormal(x, y, z);
  }
  
  /*****************************************************************************
   * <b>gluTessCallback</b> is used to indicate a callback to be used by a
   * tessellation object. If the specified callback is already defined, then it
   * is replaced. If <i>aCallback</i> is null, then the existing callback
   * becomes undefined.<P>
   *
   * These callbacks are used by the tessellation object to describe how a
   * polygon specified by the user is broken into triangles. Note that there are
   * two versions of each callback: one with user-specified polygon data and one
   * without. If both versions of a particular callback are specified, then the
   * callback with user-specified polygon data will be used. Note that the
   * polygonData parameter used by some of the methods is a copy of the
   * reference that was specified when
   * {@link #gluTessBeginPolygon gluTessBeginPolygon}
   * was called. The legal callbacks are as follows:<P>
   *
   * <b>GLU_TESS_BEGIN</b>
   * <UL>
   *   The begin callback is invoked like
   *   glBegin to indicate the start of a (triangle) primitive. The method
   *   takes a single argument of type int. If the
   *   <b>GLU_TESS_BOUNDARY_ONLY</b> property is set to <b>GL_FALSE</b>, then
   *   the argument is set to either <b>GL_TRIANGLE_FAN</b>,
   *   <b>GL_TRIANGLE_STRIP</b>, or <b>GL_TRIANGLES</b>. If the
   *   <b>GLU_TESS_BOUNDARY_ONLY</b> property is set to <b>GL_TRUE</b>, then the
   *   argument will be set to <b>GL_LINE_LOOP</b>. The method prototype for
   *   this callback is:
   * </UL>
   *
   * <PRE>
   *         void begin(int type);</PRE><P>
   *
   * <b>GLU_TESS_BEGIN_DATA</b>
   * <UL>
   *   The same as the <b>GLU_TESS_BEGIN</b> callback except
   *   that it takes an additional reference argument. This reference is
   *   identical to the opaque reference provided when
   *   {@link #gluTessBeginPolygon gluTessBeginPolygon}
   *   was called. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void beginData(int type, Object polygonData);</PRE>
   *
   * <b>GLU_TESS_EDGE_FLAG</b>
   * <UL>
   *   The edge flag callback is similar to
   *   glEdgeFlag. The method takes
   *   a single boolean boundaryEdge that indicates which edges lie on the
   *   polygon boundary. If the boundaryEdge is <b>GL_TRUE</b>, then each vertex
   *   that follows begins an edge that lies on the polygon boundary, that is,
   *   an edge that separates an interior region from an exterior one. If the
   *   boundaryEdge is <b>GL_FALSE</b>, then each vertex that follows begins an
   *   edge that lies in the polygon interior. The edge flag callback (if
   *   defined) is invoked before the first vertex callback.<P>
   *
   *   Since triangle fans and triangle strips do not support edge flags, the
   *   begin callback is not called with <b>GL_TRIANGLE_FAN</b> or
   *   <b>GL_TRIANGLE_STRIP</b> if a non-null edge flag callback is provided.
   *   (If the callback is initialized to null, there is no impact on
   *   performance). Instead, the fans and strips are converted to independent
   *   triangles. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void edgeFlag(boolean boundaryEdge);</PRE>
   *
   * <b>GLU_TESS_EDGE_FLAG_DATA</b>
   * <UL>
   *   The same as the <b>GLU_TESS_EDGE_FLAG</b> callback except that it takes
   *   an additional reference argument. This reference is identical to the
   *   opaque reference provided when
   *   {@link #gluTessBeginPolygon gluTessBeginPolygon}
   *   was called. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void edgeFlagData(boolean boundaryEdge, Object polygonData);</PRE>
   *
   * <b>GLU_TESS_VERTEX</b>
   * <UL>
   *   The vertex callback is invoked between the begin and end callbacks. It is
   *   similar to glVertex3f, and it
   *   defines the vertices of the triangles created by the tessellation
   *   process. The method takes a reference as its only argument. This
   *   reference is identical to the opaque reference provided by the user when
   *   the vertex was described (see
   *   {@link #gluTessVertex gluTessVertex}). The method
   *   prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void vertex(Object vertexData);</PRE>
   *
   * <b>GLU_TESS_VERTEX_DATA</b>
   * <UL>
   *   The same as the <b>GLU_TESS_VERTEX</b> callback except that it takes an
   *   additional reference argument. This reference is identical to the opaque
   *   reference provided when
   *   {@link #gluTessBeginPolygon gluTessBeginPolygon}
   *   was called. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void vertexData(Object vertexData, Object polygonData);</PRE>
   *
   * <b>GLU_TESS_END</b>
   * <UL>
   *   The end callback serves the same purpose as
   *   glEnd. It indicates the end of a
   *   primitive and it takes no arguments. The method prototype for this
   *   callback is:
   * </UL>
   *
   * <PRE>
   *         void end();</PRE>
   *
   * <b>GLU_TESS_END_DATA</b>
   * <UL>
   *   The same as the <b>GLU_TESS_END</b> callback except that it takes an
   *   additional reference argument. This reference is identical to the opaque
   *   reference provided when
   *   {@link #gluTessBeginPolygon gluTessBeginPolygon}
   *   was called. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void endData(Object polygonData);</PRE>
   *
   * <b>GLU_TESS_COMBINE</b>
   * <UL>
   *   The combine callback is called to create a new vertex when the
   *   tessellation detects an intersection, or wishes to merge features. The
   *   method takes four arguments: an array of three elements each of type
   *   double, an array of four references, an array of four elements each of
   *   type float, and a reference to a reference. The prototype is:
   * </UL>
   *
   * <PRE>
   *         void combine(double[] coords, Object[] data,
   *                      float[] weight, Object[] outData);</PRE>
   *
   * <UL>
   *   The vertex is defined as a linear combination of up to four existing
   *   vertices, stored in <i>data</i>. The coefficients of the linear
   *   combination are given by <i>weight</i>; these weights always add up to 1.
   *   All vertex pointers are valid even when some of the weights are 0.
   *   <i>coords</i> gives the location of the new vertex.<P>
   *
   *   The user must allocate another vertex, interpolate parameters using
   *   <i>data</i> and <i>weight</i>, and return the new vertex pointer
   *   in <i>outData</i>. This handle is supplied during rendering callbacks.
   *   The user is responsible for freeing the memory some time after
   *   {@link #gluTessEndPolygon gluTessEndPolygon} is
   *   called.<P>
   *
   *   For example, if the polygon lies in an arbitrary plane in 3-space, and a
   *   color is associated with each vertex, the <b>GLU_TESS_COMBINE</b>
   *   callback might look like this:
   * </UL>
   * <PRE>
   *         void myCombine(double[] coords, Object[] data,
   *                        float[] weight, Object[] outData)
   *         {
   *            MyVertex newVertex = new MyVertex();
   *
   *            newVertex.x = coords[0];
   *            newVertex.y = coords[1];
   *            newVertex.z = coords[2];
   *            newVertex.r = weight[0]*data[0].r +
   *                          weight[1]*data[1].r +
   *                          weight[2]*data[2].r +
   *                          weight[3]*data[3].r;
   *            newVertex.g = weight[0]*data[0].g +
   *                          weight[1]*data[1].g +
   *                          weight[2]*data[2].g +
   *                          weight[3]*data[3].g;
   *            newVertex.b = weight[0]*data[0].b +
   *                          weight[1]*data[1].b +
   *                          weight[2]*data[2].b +
   *                          weight[3]*data[3].b;
   *            newVertex.a = weight[0]*data[0].a +
   *                          weight[1]*data[1].a +
   *                          weight[2]*data[2].a +
   *                          weight[3]*data[3].a;
   *            outData = newVertex;
   *         }</PRE>
   *
   * <UL>
   *   If the tessellation detects an intersection, then the
   *   <b>GLU_TESS_COMBINE</b> or <b>GLU_TESS_COMBINE_DATA</b> callback (see
   *   below) must be defined, and it must write a non-null reference into
   *   <i>outData</i>. Otherwise the <b>GLU_TESS_NEED_COMBINE_CALLBACK</b> error
   *   occurs, and no output is generated.
   * </UL>
   *
   * <b>GLU_TESS_COMBINE_DATA</b>
   * <UL>
   *   The same as the <b>GLU_TESS_COMBINE</b> callback except that it takes an
   *   additional reference argument. This reference is identical to the opaque
   *   reference provided when
   *   {@link #gluTessBeginPolygon gluTessBeginPolygon}
   *   was called. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void combineData(double[] coords, Object[] data,
                              float[] weight, Object[] outData,
                              Object polygonData);</PRE>
   *
   * <b>GLU_TESS_ERROR</b>
   * <UL>
   *   The error callback is called when an error is encountered. The one
   *   argument is of type int; it indicates the specific error that occurred
   *   and will be set to one of <b>GLU_TESS_MISSING_BEGIN_POLYGON</b>,
   *   <b>GLU_TESS_MISSING_END_POLYGON</b>,
   *   <b>GLU_TESS_MISSING_BEGIN_CONTOUR</b>,
   *   <b>GLU_TESS_MISSING_END_CONTOUR</b>, <b>GLU_TESS_COORD_TOO_LARGE</b>,
   *   <b>GLU_TESS_NEED_COMBINE_CALLBACK</b> or <b>GLU_OUT_OF_MEMORY</b>.
   *   Character strings describing these errors can be retrieved with the
   *   {@link #gluErrorString gluErrorString} call. The
   *   method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void error(int errnum);</PRE>
   *
   * <UL>
   *   The GLU library will recover from the first four errors by inserting the
   *   missing call(s). <b>GLU_TESS_COORD_TOO_LARGE</b> indicates that some
   *   vertex coordinate exceeded the predefined constant
   *   <b>GLU_TESS_MAX_COORD</b> in absolute value, and that the value has been
   *   clamped. (Coordinate values must be small enough so that two can be
   *   multiplied together without overflow.)
   *   <b>GLU_TESS_NEED_COMBINE_CALLBACK</b> indicates that the tessellation
   *   detected an intersection between two edges in the input data, and the
   *   <b>GLU_TESS_COMBINE</b> or <b>GLU_TESS_COMBINE_DATA</b> callback was not
   *   provided. No output is generated. <b>GLU_OUT_OF_MEMORY</b> indicates that
   *   there is not enough memory so no output is generated.
   * </UL>
   *
   * <b>GLU_TESS_ERROR_DATA</b>
   * <UL>
   *   The same as the GLU_TESS_ERROR callback except that it takes an
   *   additional reference argument. This reference is identical to the opaque
   *   reference provided when
   *   {@link #gluTessBeginPolygon gluTessBeginPolygon}
   *   was called. The method prototype for this callback is:
   * </UL>
   *
   * <PRE>
   *         void errorData(int errnum, Object polygonData);</PRE>
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   * @param which
   *        Specifies the callback being defined. The following values are
   *        valid: <b>GLU_TESS_BEGIN</b>, <b>GLU_TESS_BEGIN_DATA</b>,
   *        <b>GLU_TESS_EDGE_FLAG</b>, <b>GLU_TESS_EDGE_FLAG_DATA</b>,
   *        <b>GLU_TESS_VERTEX</b>, <b>GLU_TESS_VERTEX_DATA</b>,
   *        <b>GLU_TESS_END</b>, <b>GLU_TESS_END_DATA</b>,
   *        <b>GLU_TESS_COMBINE</b>,  <b>GLU_TESS_COMBINE_DATA</b>,
   *        <b>GLU_TESS_ERROR</b>, and <b>GLU_TESS_ERROR_DATA</b>.
   * @param aCallback
   *        Specifies the callback object to be called.
   *
   * @see #gluNewTess          gluNewTess
   * @see #gluErrorString      gluErrorString
   * @see #gluTessVertex       gluTessVertex
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   * @see #gluTessBeginContour gluTessBeginContour
   * @see #gluTessProperty     gluTessProperty
   * @see #gluTessNormal       gluTessNormal
   ****************************************************************************/
  public static void gluTessCallback(GLUtessellator tessellator, int which, GLUtessellatorCallback aCallback) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessCallback(which, aCallback);
  }
  
  /*****************************************************************************
   * <b>gluTessVertex</b> describes a vertex on a polygon that the program
   * defines. Successive <b>gluTessVertex</b> calls describe a closed contour.
   * For example, to describe a quadrilateral <b>gluTessVertex</b> should be
   * called four times. <b>gluTessVertex</b> can only be called between
   * {@link #gluTessBeginContour gluTessBeginContour} and
   * {@link #gluTessBeginContour gluTessEndContour}.<P>
   *
   * <b>data</b> normally references to a structure containing the vertex
   * location, as well as other per-vertex attributes such as color and normal.
   * This reference is passed back to the user through the
   * <b>GLU_TESS_VERTEX</b> or <b>GLU_TESS_VERTEX_DATA</b> callback after
   * tessellation (see the {@link #gluTessCallback
   * gluTessCallback} reference page).
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   * @param coords
   *        Specifies the coordinates of the vertex.
   * @param data
   *        Specifies an opaque reference passed back to the program with the
   *        vertex callback (as specified by
   *        {@link #gluTessCallback gluTessCallback}).
   *
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   * @see #gluNewTess          gluNewTess
   * @see #gluTessBeginContour gluTessBeginContour
   * @see #gluTessCallback     gluTessCallback
   * @see #gluTessProperty     gluTessProperty
   * @see #gluTessNormal       gluTessNormal
   * @see #gluTessEndPolygon   gluTessEndPolygon
   ****************************************************************************/
  public static void gluTessVertex(GLUtessellator tessellator, double[] coords, int coords_offset, Object data) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessVertex(coords, coords_offset, data);
  }
  
  /*****************************************************************************
   * <b>gluTessBeginPolygon</b> and
   * {@link #gluTessEndPolygon gluTessEndPolygon} delimit
   * the definition of a convex, concave or self-intersecting polygon. Within
   * each <b>gluTessBeginPolygon</b>/
   * {@link #gluTessEndPolygon gluTessEndPolygon} pair,
   * there must be one or more calls to
   * {@link #gluTessBeginContour gluTessBeginContour}/
   * {@link #gluTessEndContour gluTessEndContour}. Within
   * each contour, there are zero or more calls to
   * {@link #gluTessVertex gluTessVertex}. The vertices
   * specify a closed contour (the last vertex of each contour is automatically
   * linked to the first). See the {@link #gluTessVertex
   * gluTessVertex}, {@link #gluTessBeginContour
   * gluTessBeginContour}, and {@link #gluTessEndContour
   * gluTessEndContour} reference pages for more details.<P>
   *
   * <b>data</b> is a reference to a user-defined data structure. If the
   * appropriate callback(s) are specified (see
   * {@link #gluTessCallback gluTessCallback}), then this
   * reference is returned to the callback method(s). Thus, it is a convenient
   * way to store per-polygon information.<P>
   *
   * Once {@link #gluTessEndPolygon gluTessEndPolygon} is
   * called, the polygon is tessellated, and the resulting triangles are
   * described through callbacks. See
   * {@link #gluTessCallback gluTessCallback} for
   * descriptions of the callback methods.
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   * @param data
   *        Specifies a reference to user polygon data.
   *
   * @see #gluNewTess          gluNewTess
   * @see #gluTessBeginContour gluTessBeginContour
   * @see #gluTessVertex       gluTessVertex
   * @see #gluTessCallback     gluTessCallback
   * @see #gluTessProperty     gluTessProperty
   * @see #gluTessNormal       gluTessNormal
   * @see #gluTessEndPolygon   gluTessEndPolygon
   ****************************************************************************/
  public static void gluTessBeginPolygon(GLUtessellator tessellator, Object data) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessBeginPolygon(data);
  }
  
  /*****************************************************************************
   * <b>gluTessBeginContour</b> and
   * {@link #gluTessEndContour gluTessEndContour} delimit
   * the definition of a polygon contour. Within each
   * <b>gluTessBeginContour</b>/
   * {@link #gluTessEndContour gluTessEndContour} pair,
   * there can be zero or more calls to
   * {@link #gluTessVertex gluTessVertex}. The vertices
   * specify a closed contour (the last vertex of each contour is automatically
   * linked to the first). See the {@link #gluTessVertex
   * gluTessVertex} reference page for more details. <b>gluTessBeginContour</b>
   * can only be called between
   * {@link #gluTessBeginPolygon gluTessBeginPolygon} and
   * {@link #gluTessEndPolygon gluTessEndPolygon}.
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   *
   * @see #gluNewTess          gluNewTess
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   * @see #gluTessVertex       gluTessVertex
   * @see #gluTessCallback     gluTessCallback
   * @see #gluTessProperty     gluTessProperty
   * @see #gluTessNormal       gluTessNormal
   * @see #gluTessEndPolygon   gluTessEndPolygon
   ****************************************************************************/
  public static void gluTessBeginContour(GLUtessellator tessellator) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessBeginContour();
  }
  
  /*****************************************************************************
   *  <b>gluTessEndContour</b> and
   * {@link #gluTessBeginContour gluTessBeginContour}
   * delimit the definition of a polygon contour. Within each
   * {@link #gluTessBeginContour gluTessBeginContour}/
   * <b>gluTessEndContour</b> pair, there can be zero or more calls to
   * {@link #gluTessVertex gluTessVertex}. The vertices
   * specify a closed contour (the last vertex of each contour is automatically
   * linked to the first). See the {@link #gluTessVertex
   * gluTessVertex} reference page for more details.
   * {@link #gluTessBeginContour gluTessBeginContour} can
   * only be called between {@link #gluTessBeginPolygon
   * gluTessBeginPolygon} and
   * {@link #gluTessEndPolygon gluTessEndPolygon}.
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   *
   * @see #gluNewTess          gluNewTess
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   * @see #gluTessVertex       gluTessVertex
   * @see #gluTessCallback     gluTessCallback
   * @see #gluTessProperty     gluTessProperty
   * @see #gluTessNormal       gluTessNormal
   * @see #gluTessEndPolygon   gluTessEndPolygon
   ****************************************************************************/
  public static void gluTessEndContour(GLUtessellator tessellator) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessEndContour();
  }
  
  /*****************************************************************************
   * <b>gluTessEndPolygon</b> and
   * {@link #gluTessBeginPolygon gluTessBeginPolygon}
   * delimit the definition of a convex, concave or self-intersecting polygon.
   * Within each {@link #gluTessBeginPolygon
   * gluTessBeginPolygon}/<b>gluTessEndPolygon</b> pair, there must be one or
   * more calls to {@link #gluTessBeginContour
   * gluTessBeginContour}/{@link #gluTessEndContour
   * gluTessEndContour}. Within each contour, there are zero or more calls to
   * {@link #gluTessVertex gluTessVertex}. The vertices
   * specify a closed contour (the last vertex of each contour is automatically
   * linked to the first). See the {@link #gluTessVertex
   * gluTessVertex}, {@link #gluTessBeginContour
   * gluTessBeginContour} and {@link #gluTessEndContour
   * gluTessEndContour} reference pages for more details.<P>
   *
   * Once <b>gluTessEndPolygon</b> is called, the polygon is tessellated, and
   * the resulting triangles are described through callbacks. See
   * {@link #gluTessCallback gluTessCallback} for
   * descriptions of the callback functions.
   *
   * @param tessellator
   *        Specifies the tessellation object (created with
   *        {@link #gluNewTess gluNewTess}).
   *
   * @see #gluNewTess          gluNewTess
   * @see #gluTessBeginContour gluTessBeginContour
   * @see #gluTessVertex       gluTessVertex
   * @see #gluTessCallback     gluTessCallback
   * @see #gluTessProperty     gluTessProperty
   * @see #gluTessNormal       gluTessNormal
   * @see #gluTessBeginPolygon gluTessBeginPolygon
   ****************************************************************************/
  public static void gluTessEndPolygon(GLUtessellator tessellator) {
      GLUtessellatorImpl tess = (GLUtessellatorImpl) tessellator;
      tess.gluTessEndPolygon();
  }

  //----------------------------------------------------------------------
  // GLU constants

  // Boolean
  public static final int GLU_FALSE = 0;
  public static final int GLU_TRUE = 1;
  
  // String Name
  public static final int GLU_VERSION = 100800;
  public static final int GLU_EXTENSIONS = 100801;
  
  // Extensions
  public static final String versionString = "1.3";
  public static final String extensionString = "GLU_EXT_object_space_tess ";
  
  // ErrorCode
  public static final int GLU_INVALID_ENUM = 100900;
  public static final int GLU_INVALID_VALUE = 100901;
  public static final int GLU_OUT_OF_MEMORY = 100902;
  public static final int GLU_INVALID_OPERATION = 100904;
  
  // TessCallback
  public static final int GLU_TESS_BEGIN = 100100;
  public static final int GLU_TESS_VERTEX = 100101;
  public static final int GLU_TESS_END = 100102;
  public static final int GLU_TESS_ERROR = 100103;
  public static final int GLU_TESS_EDGE_FLAG = 100104;
  public static final int GLU_TESS_COMBINE = 100105;
  public static final int GLU_TESS_BEGIN_DATA = 100106;
  public static final int GLU_TESS_VERTEX_DATA = 100107;
  public static final int GLU_TESS_END_DATA = 100108;
  public static final int GLU_TESS_ERROR_DATA = 100109;
  public static final int GLU_TESS_EDGE_FLAG_DATA = 100110;
  public static final int GLU_TESS_COMBINE_DATA = 100111;
  
  // TessContour
  public static final int GLU_CW = 100120;
  public static final int GLU_CCW = 100121;
  public static final int GLU_INTERIOR = 100122;
  public static final int GLU_EXTERIOR = 100123;
  public static final int GLU_UNKNOWN = 100124;
  
  // TessProperty
  public static final int GLU_TESS_WINDING_RULE = 100140;
  public static final int GLU_TESS_BOUNDARY_ONLY = 100141;
  public static final int GLU_TESS_TOLERANCE = 100142;
  
  // TessError
  public static final int GLU_TESS_ERROR1 = 100151;
  public static final int GLU_TESS_ERROR2 = 100152;
  public static final int GLU_TESS_ERROR3 = 100153;
  public static final int GLU_TESS_ERROR4 = 100154;
  public static final int GLU_TESS_ERROR5 = 100155;
  public static final int GLU_TESS_ERROR6 = 100156;
  public static final int GLU_TESS_ERROR7 = 100157;
  public static final int GLU_TESS_ERROR8 = 100158;
  public static final int GLU_TESS_MISSING_BEGIN_POLYGON = 100151;
  public static final int GLU_TESS_MISSING_BEGIN_CONTOUR = 100152;
  public static final int GLU_TESS_MISSING_END_POLYGON = 100153;
  public static final int GLU_TESS_MISSING_END_CONTOUR = 100154;
  public static final int GLU_TESS_COORD_TOO_LARGE = 100155;
  public static final int GLU_TESS_NEED_COMBINE_CALLBACK = 100156;
  
  // TessWinding
  public static final int GLU_TESS_WINDING_ODD = 100130;
  public static final int GLU_TESS_WINDING_NONZERO = 100131;
  public static final int GLU_TESS_WINDING_POSITIVE = 100132;
  public static final int GLU_TESS_WINDING_NEGATIVE = 100133;
  public static final int GLU_TESS_WINDING_ABS_GEQ_TWO = 100134;
  public static final double GLU_TESS_MAX_COORD = 1.0e150;

} // end of class GLU
