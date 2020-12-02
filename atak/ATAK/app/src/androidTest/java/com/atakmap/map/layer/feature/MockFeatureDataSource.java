
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.map.layer.feature.datastore.RuntimeFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.map.layer.feature.style.Style;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

public final class MockFeatureDataSource {
    static class FixedDataModelImpl implements FeatureDataSource {

        final String type;
        final String provider;
        final int parseVersion;
        final FeatureDataStore2 dataModel;

        private FixedDataModelImpl(String type, String provider,
                int parseVersion, FeatureDataStore2 dataModel) {
            this.type = type;
            this.provider = provider;
            this.parseVersion = parseVersion;
            this.dataModel = dataModel;
        }

        @Override
        public Content parse(File file) throws IOException {
            if (this.dataModel == null)
                return null;
            return new DataModelContentImpl(this.type, this.provider,
                    this.dataModel);
        }

        @Override
        public String getName() {
            return this.provider;
        }

        @Override
        public int parseVersion() {
            return this.parseVersion;
        }
    }

    public static FeatureDataSource createNullContent(String name,
            int parseVersion) {
        return new FixedDataModelImpl(null, name, parseVersion, null);
    }

    public static FeatureDataSource createNullContent() {
        return new FixedDataModelImpl(null, UUID.randomUUID().toString(),
                RandomUtils.rng().nextInt(), null);
    }

    /**
     * Creates a <code>FeatureDataSource</code> that will produce a non-<code>null</code>, but empty content for any input. The content's <I>provider</I> and <I>type</I> will both be set to the <I>name</I> of the data source.
     * @return
     */
    public static FeatureDataSource createEmptyContent() {
        return createEmptyContent(UUID.randomUUID().toString(),
                RandomUtils.rng().nextInt());
    }

    public static FeatureDataSource createEmptyContent(String name,
            int parseVersion) {
        return createRandomContent(name, name, parseVersion, 0, 0);
    }

    /**
     * Creates a <code>FeatureDataSource</code> that will produce a non-<code>null</code>, and randomly populated content for any input. The content's <I>provider</I> and <I>type</I> will both be set to the <I>name</I> of the data source.
     * @return
     */
    public static FeatureDataSource createRandomContent() {
        final String name = UUID.randomUUID().toString();
        return createRandomContent(
                name,
                name,
                RandomUtils.rng().nextInt(),
                1, RandomUtils.rng().nextInt(5) + 1,
                1, RandomUtils.rng().nextInt(10) + 1);
    }

    public static FeatureDataSource createRandomContent(String name,
            String type, int parseVersion, int numFeatureSets,
            int numFeatures) {
        RuntimeFeatureDataStore2 dataModel = new RuntimeFeatureDataStore2();
        try {
            for (int i = 0; i < numFeatureSets; i++) {
                final long fsid = dataModel.insertFeatureSet(
                        new FeatureSet(name, type, UUID.randomUUID().toString(),
                                RandomUtils.rng().nextDouble(),
                                RandomUtils.rng().nextDouble()));
                for (int j = 0; j < numFeatures; j++) {
                    Geometry geom = AbstractGeometryTests.randomGeometry();
                    Style style = StyleTestUtils.randomStyle(true);

                    dataModel.insertFeature(
                            new Feature(fsid,
                                    FeatureDataStore2.FEATURE_ID_NONE,
                                    UUID.randomUUID().toString(),
                                    geom,
                                    style,
                                    AttributeSetTests.randomAttributeSet(),
                                    RandomUtils.rng().nextLong(),
                                    RandomUtils.rng().nextLong()));
                }
            }

        } catch (DataStoreException e) {
            throw new RuntimeException(e);
        }

        return new FixedDataModelImpl(type, name, parseVersion, dataModel);
    }

    public static FeatureDataSource createRandomContent(String name,
            String type, int parseVersion, int minFeatureSets,
            int maxFeatureSets, int minFeatures, int maxFeatures) {
        final int numFeatureSets = RandomUtils.rng()
                .nextInt(maxFeatureSets - minFeatureSets + 1) + minFeatureSets;
        final int numFeatures = RandomUtils.rng()
                .nextInt(maxFeatures - minFeatures + 1) + minFeatures;
        return createRandomContent(name, type, parseVersion, numFeatureSets,
                numFeatures);
    }

    public static FeatureDataSource createSpawnedContent(final int geomCoding,
            final int styleCoding, final int numFeatureSets,
            final int numFeatures) {
        final String provider = UUID.randomUUID().toString();
        final String type = UUID.randomUUID().toString();
        final int parseVersion = RandomUtils.rng().nextInt();
        return new FeatureDataSource() {
            @Override
            public Content parse(File file) throws IOException {
                return new SpawningContentImpl(provider, type, geomCoding,
                        styleCoding, numFeatureSets, numFeatures);
            }

            @Override
            public String getName() {
                return provider;
            }

            @Override
            public int parseVersion() {
                return parseVersion;
            }
        };

    }

    final static class DataModelContentImpl
            implements FeatureDataSource.Content {

        final String type;
        final String provider;
        final FeatureDataStore2 dataModel;
        FeatureSetCursor featureSets;
        FeatureCursor features;
        FeatureDataSource.FeatureDefinition row;

        DataModelContentImpl(String type, String provider,
                FeatureDataStore2 dataModel) {
            this.type = type;
            this.provider = provider;
            this.dataModel = dataModel;

            try {
                this.featureSets = this.dataModel.queryFeatureSets(null);
            } catch (DataStoreException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public String getType() {
            return this.type;
        }

        @Override
        public String getProvider() {
            return this.provider;
        }

        @Override
        public boolean moveToNext(ContentPointer pointer) {
            if (pointer == ContentPointer.FEATURE)
                return moveToNextFeature();
            else if (pointer == ContentPointer.FEATURE_SET)
                return moveToNextFeatureSet();
            else
                throw new IllegalArgumentException();
        }

        private boolean moveToNextFeatureSet() {
            if (this.features != null) {
                this.features.close();
                this.features = null;
            }

            if (!this.featureSets.moveToNext())
                return false;

            try {
                FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
                params.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
                params.featureSetFilter.ids = Collections
                        .singleton(this.featureSets.getId());

                this.features = this.dataModel.queryFeatures(params);
                this.features = new FeatureCursorEncoder(this.features,
                        FeatureCursor.GEOM_WKB, FeatureCursor.STYLE_OGR);
            } catch (DataStoreException e) {
                throw new RuntimeException(e);
            }

            return true;
        }

        private boolean moveToNextFeature() {
            if (this.features == null)
                throw new IllegalStateException();
            this.row = null;
            if (!this.features.moveToNext())
                return false;
            this.row = new FeatureDataSource.FeatureDefinition();
            this.row.styleCoding = this.features.getStyleCoding();
            this.row.rawStyle = this.features.getRawStyle();
            this.row.geomCoding = this.features.getGeomCoding();
            this.row.rawGeom = this.features.getRawGeometry();
            this.row.attributes = this.features.getAttributes();
            this.row.name = this.features.getName();
            return true;
        }

        @Override
        public FeatureDataSource.FeatureDefinition get() {
            return this.row;
        }

        @Override
        public String getFeatureSetName() {
            return this.featureSets.getName();
        }

        @Override
        public double getMinResolution() {
            return this.featureSets.getMinResolution();
        }

        @Override
        public double getMaxResolution() {
            return this.featureSets.getMaxResolution();
        }

        @Override
        public void close() {
            if (this.features != null) {
                this.features.close();
                this.features = null;
            }
            if (this.featureSets != null) {
                this.featureSets.close();
                this.featureSets = null;
            }
        }
    }

    final static class SpawningContentImpl
            implements FeatureDataSource.Content {

        final String type;
        final String provider;
        final int geomCoding;
        final int styleCoding;
        final int numFeatureSets;
        final int numFeatures;
        int featureSetNum;
        FeatureSet featureSet;
        int featureNum;
        FeatureDataSource.FeatureDefinition row;

        SpawningContentImpl(String type, String provider, int geomCoding,
                int styleCoding, int numFeatureSets, int numFeatures) {
            this.type = type;
            this.provider = provider;
            this.geomCoding = geomCoding;
            this.styleCoding = styleCoding;
            this.numFeatureSets = numFeatureSets;
            this.numFeatures = numFeatures;

            this.featureSetNum = 0;
        }

        @Override
        public String getType() {
            return this.type;
        }

        @Override
        public String getProvider() {
            return this.provider;
        }

        @Override
        public boolean moveToNext(ContentPointer pointer) {
            if (pointer == ContentPointer.FEATURE)
                return moveToNextFeature();
            else if (pointer == ContentPointer.FEATURE_SET)
                return moveToNextFeatureSet();
            else
                throw new IllegalArgumentException();
        }

        private boolean moveToNextFeatureSet() {
            this.featureNum = 0;
            if (this.featureSetNum >= this.numFeatureSets)
                return false;
            this.featureSetNum++;

            this.featureSet = new FeatureSet(this.provider, this.type,
                    UUID.randomUUID().toString(),
                    RandomUtils.rng().nextDouble(),
                    RandomUtils.rng().nextDouble());
            return true;
        }

        private boolean moveToNextFeature() {
            this.row = null;

            if (this.featureNum >= this.numFeatures)
                return false;
            this.featureNum++;

            this.row = new FeatureDataSource.FeatureDefinition();
            this.row.styleCoding = this.styleCoding;
            //this.row.rawStyle = StyleTestUtils.randomStyle(true);
            switch (this.row.styleCoding) {
                case FeatureDefinition.STYLE_ATAK_STYLE:
                    break;
                case FeatureDefinition.STYLE_OGR:
                    this.row.rawStyle = FeatureStyleParser
                            .pack((Style) this.row.rawStyle);
                    break;
                default:
                    throw new IllegalStateException();
            }
            this.row.geomCoding = this.geomCoding;
            this.row.rawGeom = AbstractGeometryTests.randomGeometry();
            switch (this.row.geomCoding) {
                case FeatureDefinition.GEOM_ATAK_GEOMETRY:
                    break;
                case FeatureDefinition.GEOM_SPATIALITE_BLOB:
                    this.row.rawGeom = FeatureCursorEncoder
                            .toSpatiaLiteBlob((Geometry) this.row.rawGeom);
                    break;
                case FeatureDefinition.GEOM_WKB:
                    this.row.rawGeom = FeatureCursorEncoder
                            .toWkb((Geometry) this.row.rawGeom);
                    break;
                case FeatureDefinition.GEOM_WKT:
                    this.row.rawGeom = FeatureCursorEncoder
                            .toWkt((Geometry) this.row.rawGeom);
                    break;
                default:
                    throw new IllegalStateException();
            }
            //this.row.attributes = AttributeSetTests.randomAttributeSet();
            //this.row.name = UUID.randomUUID().toString();
            return true;
        }

        @Override
        public FeatureDataSource.FeatureDefinition get() {
            return this.row;
        }

        @Override
        public String getFeatureSetName() {
            return this.featureSet.getName();
        }

        @Override
        public double getMinResolution() {
            return this.featureSet.getMinResolution();
        }

        @Override
        public double getMaxResolution() {
            return this.featureSet.getMaxResolution();
        }

        @Override
        public void close() {
        }
    }
}
