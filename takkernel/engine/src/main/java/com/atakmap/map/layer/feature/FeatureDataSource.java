
package com.atakmap.map.layer.feature;

import com.atakmap.annotations.DeprecatedApi;

import java.io.File;
import java.io.IOException;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface FeatureDataSource {

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
    public Content parse(File file) throws IOException;

    /**
     * The name of the provider.
     * 
     * @return  The name of the provider.
     */
    public String getName();

    /**
     * Returns the parse implementation version for this provider. Different
     * parse versions indicate that a provider may produce different content
     * from the same file.
     * 
     * @return  The parse implementation version.
     */
    public int parseVersion();

    /**
     * Parsed feature content. Provides access to the feature data.
     *  
     * @author Developer
     */
    @DontObfuscate
    public static interface Content {
        @DontObfuscate
        public enum ContentPointer {
            FEATURE,
            FEATURE_SET,
        };
        
        /**
         * Returns the type of the content (e.g. KML). This field is always
         * available regardless of pointer position.
         * 
         * @return The type of the content.
         */
        public String getType();

        /**
         * Returns the name of the provider that parsed the content. This field
         * is always available regardless of pointer position.
         * 
         * @return The name of the provider that parsed the content.
         */
        public String getProvider();

        /**
         * Increments the specified content pointer.  When the
         * {@link ContentPointer#FEATURE_SET} pointer is incremented the
         * the {@link ContentPointer#FEATURE} pointer is automatically reset
         * to the first feature for the new set.
         * 
         * @param pointer    The pointer to be incremented
         * 
         * @return <code>true</code> if there is another feature,
         *         <code>false</code> if no more features are available.
         */
        public boolean moveToNext(ContentPointer pointer);
        
        /**
         * Returns the current feature definition. This field is dependent on
         * the current {@link ContentPointer#FEATURE} pointer position.
         * 
         * @return The current feature definition.
         */
        public FeatureDefinition get();

        /**
         * Returns the name for the current feature set. This field is dependent
         * on the current {@link ContentPointer#FEATURE_SET} pointer position.
         * 
         * @return The name for the current feature set.
         */
        public String getFeatureSetName();
        
        /**
         * Returns the minimum resolution that the features should be displayed
         * at. This field is dependent on the current
         * {@link ContentPointer#FEATURE_SET} pointer position.
         * 
         * @return The minimum resolution, in meters-per-pixel, that the
         *         features should be displayed at.
         */
        public double getMinResolution();
        
        /**
         * Returns the maximum resolution that the features should be displayed
         * at. This field is dependent on the current
         * {@link ContentPointer#FEATURE_SET} pointer position.
         * 
         * @return The maximum resolution, in meters-per-pixel, that the
         *         features should be displayed at.
         */
        public double getMaxResolution();
        
        /**
         * Releases any allocated resources. Subsequent invocation of other
         * methods may produce undefined results.
         */
        public void close();
    }
    
    /**************************************************************************/

    /**
     * The definition of a feature. Feature properties may be recorded as raw,
     * unprocessed data of several well-defined types. Utilization of
     * unprocessed data may yield significant a performance advantage depending
     * on the intended storage. 
     *  
     * @author Developer
     */
    @DontObfuscate
    public final static class FeatureDefinition implements com.atakmap.map.layer.feature.FeatureDefinition {

        /**
         * The raw geometry data.
         */
        public Object rawGeom;
        
        /**
         * The coding of the geometry data.
         */
        public int geomCoding;
        
        /**
         * The name of the feature.
         */
        public String name;
        
        /**
         * The coding of the style data.
         */
        public int styleCoding;
        
        /**
         * The raw style data.
         */
        public Object rawStyle;
        
        /**
         * The feature attributes.
         */
        public AttributeSet attributes;


        @Override
        public Feature get() {
            return new Feature(this);
        }

        /**
         * Creates a new feature from the associated properties based on the
         * coding.
         * 
         * @return the Feature
         * 
         * @throws  UnsupportedOperationException   If the specified geometry
         *                                          or style codings are not
         *                                          defined
         * @throws  ClassCastException              If the class for
         *                                          {@link #rawGeom} or
         *                                          {@link #rawStyle} does not
         *                                          match the class expected for
         *                                          the respective coding.
         *                                          
         * @deprecated use {@link #get()}
         */
        @Deprecated
        @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
        public Feature getFeature() {
            return this.get();
        }

        @Override
        public Object getRawGeometry() {
            return this.rawGeom;
        }

        @Override
        public int getGeomCoding() {
            return this.geomCoding;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getStyleCoding() {
            return this.styleCoding;
        }

        @Override
        public Object getRawStyle() {
            return this.rawStyle;
        }

        @Override
        public AttributeSet getAttributes() {
            return this.attributes;
        }
    } // FeatureDefinition
}
