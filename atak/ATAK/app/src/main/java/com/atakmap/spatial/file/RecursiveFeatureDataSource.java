
package com.atakmap.spatial.file;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.lang.Objects;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.FeatureDataSourceContentFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

public class RecursiveFeatureDataSource implements FeatureDataSource {

    private final String providerName;
    private final String targetProviderHint;
    private final FileFilter recurseFilter;

    public RecursiveFeatureDataSource(String name, String providerHint,
            FileFilter recurseFilter) {
        this.providerName = name;
        this.targetProviderHint = providerHint;
        this.recurseFilter = recurseFilter;
    }

    @Override
    public Content parse(File file) throws IOException {
        if (!IOProviderFactory.isDirectory(file))
            return FeatureDataSourceContentFactory.parse(file,
                    this.targetProviderHint);

        Collection<Content> retval = new LinkedList<>();
        final String type = recursiveParseContent(file, this.recurseFilter,
                new String[] {
                        this.targetProviderHint
                }, new String[1], retval);
        if (retval.size() < 1)
            return null;
        else if (retval.size() == 1)
            return retval.iterator().next();

        return new CollectionContent(this.providerName, type,
                retval.iterator());
    }

    @Override
    public String getName() {
        return this.providerName;
    }

    @Override
    public int parseVersion() {
        // XXX - need to indicate parse version of underlying provider
        return 0;
    }

    /**************************************************************************/

    private static String recursiveParseContent(File file,
            FileFilter recurseFilter, String[] provider, String[] type,
            Collection<Content> content) {
        File[] children = IOProviderFactory.listFiles(file, recurseFilter);
        if (children != null) {
            for (File aChildren : children) {
                if (IOProviderFactory.isDirectory(aChildren)) {
                    recursiveParseContent(aChildren, recurseFilter, provider,
                            type, content);
                } else {
                    Content c = FeatureDataSourceContentFactory.parse(
                            aChildren,
                            provider[0]);
                    if (c != null) {
                        if ((provider[0] == null || Objects.equals(provider[0],
                                c.getProvider()))
                                &&
                                (type[0] == null || Objects.equals(type[0],
                                        c.getType()))) {

                            content.add(c);
                            if (provider[0] == null)
                                provider[0] = c.getType();
                            if (type[0] == null)
                                type[0] = c.getType();
                        }
                    }
                }
            }
        }
        return type[0];
    }

    /**************************************************************************/

    private static class CollectionContent implements Content {

        private final String providerName;
        private final String type;
        private final Iterator<Content> content;
        private Content featureSet;

        CollectionContent(String providerName, String type,
                Iterator<Content> content) {
            this.providerName = providerName;
            this.type = type;
            this.content = content;
        }

        @Override
        public String getType() {
            return this.type;
        }

        @Override
        public String getProvider() {
            return this.providerName;
        }

        @Override
        public boolean moveToNext(ContentPointer pointer) {
            if (this.featureSet == null && !this.content.hasNext())
                throw new IndexOutOfBoundsException();

            if (this.featureSet == null)
                this.featureSet = this.content.next();
            boolean retval = this.featureSet.moveToNext(pointer);
            while (!retval &&
                    pointer == ContentPointer.FEATURE_SET &&
                    this.content.hasNext()) {

                if (this.featureSet != null)
                    this.featureSet.close();
                this.featureSet = this.content.next();
                retval = this.featureSet.moveToNext(pointer);
            }
            return retval;
        }

        @Override
        public FeatureDefinition get() {
            if (this.featureSet == null)
                throw new IndexOutOfBoundsException();
            return this.featureSet.get();
        }

        @Override
        public String getFeatureSetName() {
            if (this.featureSet == null)
                throw new IndexOutOfBoundsException();
            return this.featureSet.getFeatureSetName();
        }

        @Override
        public double getMinResolution() {
            if (this.featureSet == null)
                throw new IndexOutOfBoundsException();
            return this.featureSet.getMinResolution();
        }

        @Override
        public double getMaxResolution() {
            if (this.featureSet == null)
                throw new IndexOutOfBoundsException();
            return this.featureSet.getMaxResolution();
        }

        @Override
        public void close() {
            if (this.featureSet != null)
                this.featureSet.close();
            while (this.content.hasNext())
                this.content.next().close();
        }
    } // CollectionContent

} // ZipFeatureDataSource
