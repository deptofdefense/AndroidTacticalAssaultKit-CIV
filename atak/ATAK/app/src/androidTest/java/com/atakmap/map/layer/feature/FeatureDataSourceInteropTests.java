
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.interop.Pointer;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

public class FeatureDataSourceInteropTests extends ATAKInstrumentedTest {
    @Test
    public void wrapper_create() {
        // create Mock
        FeatureDataSource source = MockFeatureDataSource.createNullContent();
        Assert.assertNotNull(source);
        // wrap as native
        Pointer pointer = wrap(source);
        Assert.assertNotNull(pointer);
        Assert.assertNotEquals(pointer.raw, 0L);
        // destruct
        destruct(pointer);
        Assert.assertEquals(pointer.raw, 0L);
    }

    @Test
    public void wrap_roundtrip() {
        // create Mock
        FeatureDataSource source = MockFeatureDataSource.createNullContent();
        Assert.assertNotNull(source);
        // wrap as native
        Pointer pointer = wrap(source);
        Assert.assertNotNull(pointer);
        Assert.assertNotEquals(pointer.raw, 0L);

        FeatureDataSource wrapped = newNativeFeatureDataSource(pointer);

        Assert.assertEquals(wrapped.getName(), source.getName());
        Assert.assertEquals(wrapped.parseVersion(), source.parseVersion());
    }

    @Test
    public void wrap_content_roundtrip() {
        // create Mock
        FeatureDataSource source = MockFeatureDataSource.createRandomContent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                RandomUtils.rng().nextInt(), 2, 5, 10, 20);
        Assert.assertNotNull(source);
        // wrap as native
        Pointer pointer = wrap(source);
        Assert.assertNotNull(pointer);
        Assert.assertNotEquals(pointer.raw, 0L);

        FeatureDataSource wrapped = newNativeFeatureDataSource(pointer);

        FeatureDataSource.Content truthContent = null;
        FeatureDataSource.Content testContent = null;
        try {
            File f = new File(UUID.randomUUID().toString());
            try {
                truthContent = source.parse(f);
                testContent = wrapped.parse(f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Assert.assertNotNull(truthContent);
            Assert.assertNotNull(testContent);

            Assert.assertNotSame(truthContent, testContent);

            // compare content fields
            Assert.assertEquals(truthContent.getProvider(),
                    testContent.getProvider());
            Assert.assertEquals(truthContent.getType(), testContent.getType());

            do {
                final boolean nextFStruth = truthContent.moveToNext(
                        FeatureDataSource.Content.ContentPointer.FEATURE_SET);
                final boolean nextFStest = testContent.moveToNext(
                        FeatureDataSource.Content.ContentPointer.FEATURE_SET);

                Assert.assertEquals(nextFStest, nextFStruth);
                if (!nextFStruth)
                    break;

                // compare feature set fields
                Assert.assertEquals(truthContent.getFeatureSetName(),
                        testContent.getFeatureSetName());
                Assert.assertTrue(truthContent.getMinResolution() == testContent
                        .getMinResolution());
                Assert.assertTrue(truthContent.getMaxResolution() == testContent
                        .getMaxResolution());

                do {
                    final boolean nextFtruth = truthContent.moveToNext(
                            FeatureDataSource.Content.ContentPointer.FEATURE);
                    final boolean nextFtest = testContent.moveToNext(
                            FeatureDataSource.Content.ContentPointer.FEATURE);

                    Assert.assertEquals(nextFtest, nextFtruth);
                    if (!nextFtruth)
                        break;

                    final FeatureDataSource.FeatureDefinition truthDef = truthContent
                            .get();
                    final FeatureDataSource.FeatureDefinition testDef = testContent
                            .get();

                    Assert.assertNotNull(truthDef);
                    Assert.assertNotNull(testDef);

                    Assert.assertNotSame(truthDef, testDef);

                    // compare feature fields
                    Assert.assertEquals(truthDef.name, testDef.name);
                    Assert.assertEquals(truthDef.geomCoding,
                            testDef.geomCoding);
                    Assert.assertEquals(truthDef.styleCoding,
                            testDef.styleCoding);
                    Assert.assertEquals(truthDef.attributes,
                            testDef.attributes);

                    Feature truthF = truthDef.get();
                    Feature testF = testDef.get();

                    AbstractGeometryTests.assertEqual(truthF.getGeometry(),
                            testF.getGeometry());
                    StyleTestUtils.assertEqual(truthF.getStyle(),
                            testF.getStyle());
                } while (true);
            } while (true);
        } finally {
            if (truthContent != null)
                truthContent.close();
            if (testContent != null)
                testContent.close();
        }
    }

    Pointer wrap(FeatureDataSource source) {
        try {
            Method wrapMethod = NativeFeatureDataSource.class
                    .getDeclaredMethod("wrap", FeatureDataSource.class);
            wrapMethod.setAccessible(true);
            return (Pointer) wrapMethod.invoke(null, source);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    void destruct(Pointer pointer) {
        try {
            Method destructMethod = NativeFeatureDataSource.class
                    .getDeclaredMethod("FeatureDataSource_destruct",
                            Pointer.class);
            destructMethod.setAccessible(true);
            destructMethod.invoke(null, pointer);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    FeatureDataSource newNativeFeatureDataSource(Pointer pointer) {
        try {
            Constructor<NativeFeatureDataSource> ctor = NativeFeatureDataSource.class
                    .getDeclaredConstructor(Pointer.class);
            ctor.setAccessible(true);
            return ctor.newInstance(pointer);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
