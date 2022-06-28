
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.UUID;

public class FeatureDataSourceContentFactoryTests extends ATAKInstrumentedTest {
    @Test
    public void FeatureDataSourceContentFactory_get_null_provider() {
        FeatureDataSource provider = FeatureDataSourceContentFactory
                .getProvider(null);
        Assert.assertNull(provider);
    }

    @Test
    public void FeatureDataSourceContentFactory_get_empty_string_provider() {
        FeatureDataSource provider = FeatureDataSourceContentFactory
                .getProvider("");
        Assert.assertNull(provider);
    }

    @Test
    public void FeatureDataSourceContentFactory_get_invalid_provider() {
        FeatureDataSource provider = FeatureDataSourceContentFactory
                .getProvider("!@#$$$$%^&*(()_+");
        Assert.assertNull(provider);
    }

    @Test
    public void FeatureDataSourceContentFactory_register_provider() {
        final FeatureDataSource provider = MockFeatureDataSource
                .createNullContent();
        FeatureDataSourceContentFactory.register(provider);
    }

    @Test
    public void FeatureDataSourceContentFactory_register_provider_with_priority() {
        final FeatureDataSource provider = MockFeatureDataSource
                .createNullContent();
        FeatureDataSourceContentFactory.register(provider, 2);
    }

    @Test
    public void FeatureDataSourceContentFactory_register_provider_roundtrip() {
        final FeatureDataSource provider = MockFeatureDataSource
                .createNullContent();
        FeatureDataSourceContentFactory.register(provider);

        final FeatureDataSource registered = FeatureDataSourceContentFactory
                .getProvider(provider.getName());
        Assert.assertSame(provider, registered);
    }

    @Test
    public void FeatureDataSourceContentFactory_register_overwrite() {
        final String name = UUID.randomUUID().toString();
        final FeatureDataSource provider1 = MockFeatureDataSource
                .createNullContent(name, 1);
        final FeatureDataSource provider2 = MockFeatureDataSource
                .createNullContent(name, 1);
        Assert.assertNotSame(provider1, provider2);

        try {
            FeatureDataSourceContentFactory.register(provider1);
            FeatureDataSourceContentFactory.register(provider2);
        } finally {
            FeatureDataSourceContentFactory.unregister(provider2);
            FeatureDataSourceContentFactory.unregister(provider1);
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_register_overwrite_roundtrip() {
        final String name = UUID.randomUUID().toString();
        final FeatureDataSource provider1 = MockFeatureDataSource
                .createNullContent(name, 1);
        final FeatureDataSource provider2 = MockFeatureDataSource
                .createNullContent(name, 1);
        try {
            FeatureDataSourceContentFactory.register(provider1);
            FeatureDataSourceContentFactory.register(provider2);

            final FeatureDataSource registered = FeatureDataSourceContentFactory
                    .getProvider(name);
            Assert.assertSame(provider2, registered);

        } finally {
            FeatureDataSourceContentFactory.unregister(provider2);
            FeatureDataSourceContentFactory.unregister(provider1);
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_unregister_provider() {
        final FeatureDataSource provider = MockFeatureDataSource
                .createNullContent();
        FeatureDataSourceContentFactory.register(provider);

        final FeatureDataSource registered = FeatureDataSourceContentFactory
                .getProvider(provider.getName());
        Assert.assertSame(provider, registered);

        FeatureDataSourceContentFactory.unregister(provider);

        final FeatureDataSource stillRegistered = FeatureDataSourceContentFactory
                .getProvider(provider.getName());
        Assert.assertNull(stillRegistered);
    }

    @Test
    public void FeatureDataSourceContentFactory_parse_no_providers() {
        FeatureDataSource.Content content = null;
        try {
            final File file = new File("/dev/null");
            content = FeatureDataSourceContentFactory.parse(file, null);
            Assert.assertNull(content);
        } finally {
            if (content != null)
                content.close();
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_parse_unsupported_provider() {
        final FeatureDataSource provider = MockFeatureDataSource
                .createNullContent();
        FeatureDataSourceContentFactory.register(provider);
        try {
            FeatureDataSource.Content content = null;
            try {
                final File file = new File("/dev/null");
                content = FeatureDataSourceContentFactory.parse(file, null);
                Assert.assertNull(content);
            } finally {
                if (content != null)
                    content.close();
            }
        } finally {
            FeatureDataSourceContentFactory.unregister(provider);
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_parse_supported_provider() {
        final FeatureDataSource provider = MockFeatureDataSource
                .createRandomContent();
        FeatureDataSourceContentFactory.register(provider);
        try {
            FeatureDataSource.Content content = null;
            try {
                final File file = new File("/dev/null");
                content = FeatureDataSourceContentFactory.parse(file, null);
                Assert.assertNotNull(content);

                Assert.assertEquals(content.getType(), provider.getName());
                Assert.assertEquals(content.getProvider(), provider.getName());
            } finally {
                if (content != null)
                    content.close();
            }
        } finally {
            FeatureDataSourceContentFactory.unregister(provider);
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_parse_supported_multiple_providers() {
        final String prefix = UUID.randomUUID().toString();

        FeatureDataSource[] unsupported = new FeatureDataSource[5];
        for (int i = 0; i < unsupported.length; i++) {
            unsupported[i] = MockFeatureDataSource
                    .createNullContent(prefix + "." + i, 1);
            FeatureDataSourceContentFactory.register(unsupported[i]);
        }

        final FeatureDataSource supported = MockFeatureDataSource
                .createEmptyContent(
                        prefix + "." + unsupported.length, 1);

        FeatureDataSourceContentFactory.register(supported);
        try {
            FeatureDataSource.Content content = null;
            try {
                final File file = new File("/dev/null");
                content = FeatureDataSourceContentFactory.parse(file, null);
                Assert.assertNotNull(content);

                Assert.assertEquals(content.getType(), supported.getName());
                Assert.assertEquals(content.getProvider(), supported.getName());
            } finally {
                if (content != null)
                    content.close();
            }
        } finally {
            FeatureDataSourceContentFactory.unregister(supported);
            for (FeatureDataSource featureDataSource : unsupported)
                FeatureDataSourceContentFactory.unregister(featureDataSource);
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_parse_supported_by_name_multiple_providers() {
        final String prefix = UUID.randomUUID().toString();

        FeatureDataSource[] supported = new FeatureDataSource[20];
        for (int i = 0; i < supported.length; i++) {
            supported[i] = MockFeatureDataSource.createRandomContent(
                    prefix + "." + i,
                    prefix + "." + i, 1, 1, 1);
            FeatureDataSourceContentFactory.register(supported[i]);
        }

        final FeatureDataSource targetProvider = supported[supported.length
                - 1];

        try {
            FeatureDataSource.Content content = null;
            try {
                final File file = new File("/dev/null");
                content = FeatureDataSourceContentFactory.parse(file,
                        targetProvider.getName());
                Assert.assertNotNull(content);

                Assert.assertEquals(content.getType(),
                        targetProvider.getName());
                Assert.assertEquals(content.getProvider(),
                        targetProvider.getName());
            } finally {
                if (content != null)
                    content.close();
            }
        } finally {
            for (FeatureDataSource featureDataSource : supported)
                FeatureDataSourceContentFactory.unregister(featureDataSource);
        }
    }

    @Test
    public void FeatureDataSourceContentFactory_parse_supported_by_invalid_name_multiple_providers() {
        final String prefix = UUID.randomUUID().toString();

        FeatureDataSource[] supported = new FeatureDataSource[20];
        for (int i = 0; i < supported.length; i++) {
            supported[i] = MockFeatureDataSource.createRandomContent(
                    prefix + "." + i,
                    prefix + "." + i, 1, 1, 1);
            FeatureDataSourceContentFactory.register(supported[i]);
        }

        try {
            FeatureDataSource.Content content = null;
            try {
                final File file = new File("/dev/null");
                content = FeatureDataSourceContentFactory.parse(file,
                        prefix + "." + supported.length);
                Assert.assertNull(content);
            } finally {
                if (content != null)
                    content.close();
            }
        } finally {
            for (FeatureDataSource featureDataSource : supported)
                FeatureDataSourceContentFactory.unregister(featureDataSource);
        }
    }
}
