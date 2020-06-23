
package com.atakmap.android.maps;

import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FilterMetaDataHolder implements MetaDataHolder {
    protected MetaDataHolder metadata;

    public FilterMetaDataHolder(MetaDataHolder metadata) {
        this.metadata = metadata;
    }

    @Override
    public String getMetaString(String key, String fallbackValue) {
        return this.metadata.getMetaString(key, fallbackValue);
    }

    @Override
    public void setMetaString(String key, String value) {
        this.metadata.setMetaString(key, value);
    }

    @Override
    public int getMetaInteger(String key, int fallbackValue) {
        return this.metadata.getMetaInteger(key, fallbackValue);
    }

    @Override
    public void setMetaInteger(String key, int value) {
        this.metadata.setMetaInteger(key, value);
    }

    @Override
    public double getMetaDouble(String key, double fallbackValue) {
        return this.metadata.getMetaDouble(key, fallbackValue);
    }

    @Override
    public void setMetaDouble(String key, double value) {
        this.metadata.setMetaDouble(key, value);
    }

    @Override
    public boolean getMetaBoolean(String key, boolean fallbackValue) {
        return this.metadata.getMetaBoolean(key, fallbackValue);
    }

    @Override
    public void setMetaBoolean(String key, boolean value) {
        this.metadata.setMetaBoolean(key, value);
    }

    @Override
    public boolean hasMetaValue(String key) {
        return this.metadata.hasMetaValue(key);
    }

    @Override
    public void setMetaData(Map<String, Object> bundle) {
        this.metadata.setMetaData(bundle);
    }

    @Override
    public void getMetaData(Map<String, Object> bundle) {
        this.metadata.getMetaData(bundle);
    }

    @Override
    public void setMetaLong(String key, long value) {
        this.metadata.setMetaLong(key, value);
    }

    @Override
    public long getMetaLong(String key, long fallbackValue) {
        return this.metadata.getMetaLong(key, fallbackValue);
    }

    @Override
    public void removeMetaData(String key) {
        this.metadata.removeMetaData(key);
    }

    @Override
    public Map<String, Object> getMetaMap(String key) {
        return this.metadata.getMetaMap(key);
    }

    @Override
    public void setMetaMap(String key, Map<String, Object> bundle) {
        this.metadata.setMetaMap(key, bundle);
    }

    @Override
    public void copyMetaData(Map<String, Object> bundle) {
        this.metadata.copyMetaData(bundle);
    }

    @Override
    public ArrayList<String> getMetaStringArrayList(String key) {
        return this.metadata.getMetaStringArrayList(key);
    }

    @Override
    public void setMetaStringArrayList(String key, ArrayList<String> value) {
        this.metadata.setMetaStringArrayList(key, value);
    }

    @Override
    public int[] getMetaIntArray(String key) {
        return this.metadata.getMetaIntArray(key);
    }

    @Override
    public void setMetaIntArray(String key, int[] value) {
        this.metadata.setMetaIntArray(key, value);
    }

    @Override
    public Serializable getMetaSerializable(String key) {
        return this.metadata.getMetaSerializable(key);
    }

    @Override
    public void setMetaSerializable(String key, Serializable value) {
        this.metadata.setMetaSerializable(key, value);
    }

    @Override
    public Parcelable getMetaParcelable(String key) {
        return this.metadata.getMetaParcelable(key);
    }

    @Override
    public void setMetaParcelable(String key, Parcelable value) {
        this.metadata.setMetaParcelable(key, value);
    }

    @Override
    public <T extends Object> T get(String key) {
        return this.metadata.get(key);
    }

    public void toggleMetaData(String k, boolean on) {
        if (on)
            setMetaBoolean(k, true);
        else
            removeMetaData(k);
    }

    public void setMetaDataImpl(MetaDataHolder impl) {
        Map<String, Object> meta = new HashMap<>();
        this.getMetaData(meta);
        impl.setMetaData(meta);
        this.metadata = impl;
    }
}
