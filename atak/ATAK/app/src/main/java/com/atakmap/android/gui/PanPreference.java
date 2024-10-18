
package com.atakmap.android.gui;

import android.content.Context;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Plugin aware preference that correctly handles Summary and Title information.
 * This has been modified to allow for a pluginIcon attribute which will allow for icons
 * to be used in the entity.
 */
public class PanPreference extends Preference {

    public static final String TAG = "PanPreference";

    private final Map<String, Integer> otherAttributes = new HashMap<>();

    public PanPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, PanPreference.filter(attrs), defStyleAttr, defStyleRes);
        setup(attrs, context, this, otherAttributes);
    }

    public PanPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, PanPreference.filter(attrs), defStyleAttr);
        setup(attrs, context, this, otherAttributes);
    }

    public PanPreference(Context context, AttributeSet attrs) {
        super(context, PanPreference.filter(attrs));
        setup(attrs, context, this, otherAttributes);
    }

    public PanPreference(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        if (!isEnabled())
            v.setEnabled(false);
        return v;
    }

    /**
     * Helper method used by all of the Pan*Preference classes in order to
     * properly resolve the title and summary of a preference from the plugin
     * apk.
     * @param attrs the attribute set to set up.
     * @param c the context to use.
     * @param p the preference to reconfigure
     * @param other additional attributes.
     */
    public static void setup(AttributeSet attrs, Context c, Preference p,
            Map<String, Integer> other) {
        if (attrs == null)
            return;
        for (int i = 0; i < attrs.getAttributeCount(); ++i) {
            final String attrName = attrs.getAttributeName(i);
            final int resId = attrs.getAttributeResourceValue(i, -1);
            if (attrName.equals("summary") && resId > 0) {
                p.setSummary(c.getString(resId));
            } else if (attrName.equals("title") && resId > 0) {
                p.setTitle(c.getString(resId));
            } else if (p instanceof DialogPreference
                    && attrName.equals("dialogTitle") && resId > 0) {
                DialogPreference dp = (DialogPreference) p;
                dp.setDialogTitle(c.getString(resId));
            } else if (attrName.equals("pluginIcon") && resId > 0) {
                p.setIcon(c.getDrawable(resId));
            }

            if (other != null)
                other.put(attrName, resId);
        }
    }

    static AttributeSet filter(AttributeSet attrs) {
        return attrs;
    }

    /**
     * Attempt at filtering out plugin supplied resources that would trip up during the
     * build process.    However this needs to be an XmlBlock.Parser.
     * Leave this in in case it might trigger another solution.
     */
    private static class FilteredAttributeSet implements AttributeSet {
        final private int[] indicies;
        final private int length;

        final private AttributeSet as;

        public FilteredAttributeSet(final AttributeSet as) {
            this.as = as;
            indicies = new int[as.getAttributeCount()];
            int count = 0;
            for (int i = 0; i < as.getAttributeCount(); ++i) {
                final String s = as.getAttributeName(i);
                if (s.equals("title") || s.equals("summary")
                        || s.equals("icon")) {
                    // filter these out, they will be added back in later
                } else {
                    indicies[count] = i;
                    count++;
                }
            }
            length = count;
        }

        private boolean isInvalid(final String s) {
            return (s.equals("title") || s.equals("summary")
                    || s.equals("icon"));
        }

        @Override
        public int getAttributeCount() {
            return length;
        }

        @Override
        public String getAttributeName(int index) {
            return as.getAttributeName(indicies[index]);
        }

        @Override
        public String getAttributeValue(int index) {
            return as.getAttributeValue(indicies[index]);
        }

        @Override
        public String getAttributeValue(String namespace, String name) {
            return as.getAttributeValue(namespace, name);
        }

        @Override
        public String getPositionDescription() {
            return as.getPositionDescription();
        }

        @Override
        public int getAttributeNameResource(int index) {
            return as.getAttributeNameResource(indicies[index]);
        }

        @Override
        public int getAttributeListValue(String namespace, String attribute,
                String[] options, int defaultValue) {
            if (isInvalid(attribute))
                return defaultValue;
            return as.getAttributeListValue(namespace, attribute, options,
                    defaultValue);
        }

        @Override
        public boolean getAttributeBooleanValue(String namespace,
                String attribute, boolean defaultValue) {
            if (isInvalid(attribute))
                return defaultValue;
            return as.getAttributeBooleanValue(namespace, attribute,
                    defaultValue);
        }

        @Override
        public int getAttributeResourceValue(String namespace, String attribute,
                int defaultValue) {
            if (isInvalid(attribute))
                return defaultValue;
            return as.getAttributeResourceValue(namespace, attribute,
                    defaultValue);
        }

        @Override
        public int getAttributeIntValue(String namespace, String attribute,
                int defaultValue) {
            if (isInvalid(attribute))
                return defaultValue;
            return as.getAttributeIntValue(namespace, attribute, defaultValue);
        }

        @Override
        public int getAttributeUnsignedIntValue(String namespace,
                String attribute, int defaultValue) {
            if (isInvalid(attribute))
                return defaultValue;
            return as.getAttributeUnsignedIntValue(namespace, attribute,
                    defaultValue);
        }

        @Override
        public float getAttributeFloatValue(String namespace, String attribute,
                float defaultValue) {
            if (isInvalid(attribute))
                return defaultValue;
            return as.getAttributeFloatValue(namespace, attribute,
                    defaultValue);
        }

        @Override
        public int getAttributeListValue(int index, String[] options,
                int defaultValue) {
            return as.getAttributeListValue(indicies[index], options,
                    defaultValue);
        }

        @Override
        public boolean getAttributeBooleanValue(int index,
                boolean defaultValue) {
            return as.getAttributeBooleanValue(indicies[index], defaultValue);
        }

        @Override
        public int getAttributeResourceValue(int index, int defaultValue) {
            return as.getAttributeResourceValue(indicies[index], defaultValue);
        }

        @Override
        public int getAttributeIntValue(int index, int defaultValue) {
            return as.getAttributeIntValue(indicies[index], defaultValue);
        }

        @Override
        public int getAttributeUnsignedIntValue(int index, int defaultValue) {
            return as.getAttributeUnsignedIntValue(indicies[index],
                    defaultValue);
        }

        @Override
        public float getAttributeFloatValue(int index, float defaultValue) {
            return as.getAttributeFloatValue(indicies[index], defaultValue);
        }

        @Override
        public String getIdAttribute() {
            return as.getIdAttribute();
        }

        @Override
        public String getClassAttribute() {
            return as.getClassAttribute();
        }

        @Override
        public int getIdAttributeResourceValue(int defaultValue) {
            return as.getIdAttributeResourceValue(defaultValue);
        }

        @Override
        public int getStyleAttribute() {
            return as.getStyleAttribute();
        }

        public void close() {
        }
    }
}
