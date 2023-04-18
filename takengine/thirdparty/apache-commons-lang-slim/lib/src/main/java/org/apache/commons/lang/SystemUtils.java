/**
 * Extremely reduced stub or shortcut implementation without pulling
 * in commons-lang.
 */


/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.lang;

/**
 * <p>
 * Helpers for <code>java.lang.System</code>.
 * </p>
 * 
 * <p>
 * If a system property cannot be read due to security restrictions,
 * the corresponding field in this class will be set to <code>null</code>
 * and a message will be written to <code>System.err</code>.
 * </p>
 * 
 * <p>
 * #ThreadSafe#
 * </p>
 * 
 * @author Apache Software Foundation
 * @author Based on code from Avalon Excalibur
 * @author Based on code from Lucene
 * @author <a href="mailto:sdowney@panix.com">Steve Downey</a>
 * @author Gary Gregory
 * @author Michael Becke
 * @author Tetsuya Kaneuchi
 * @author Rafal Krupinski
 * @author Jason Gritman
 * @since 1.0
 * @version $Id: SystemUtils.java 1056988 2011-01-09 17:58:53Z niallp $
 */
public class SystemUtils {



    // System property constants
    // -----------------------------------------------------------------------
    // These MUST be declared first. Other constants depend on this.



    /**
     * The System property key for the Java IO temporary directory.
     */
    private static final String JAVA_IO_TMPDIR_KEY = "java.io.tmpdir";

    /**
     * The System property key for the Java home directory.
     */
    private static final String JAVA_HOME_KEY = "java.home";


    public static final String JAVA_EXT_DIRS = getSystemProperty("java.ext.dirs");


    /**
     * <p>
     * The <code>file.separator</code> System Property. File separator (<code>&quot;/&quot;</code> on UNIX).
     * </p>
     *
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     *
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     *
     * @since Java 1.1
     */
    public static final String FILE_SEPARATOR = getSystemProperty("file.separator");

    /**
     * <p>
     * The <code>line.separator</code> System Property. Line separator (<code>&quot;\n&quot;</code> on UNIX).
     * </p>
     *
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     *
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     *
     * @since Java 1.1
     */
    public static final String LINE_SEPARATOR = getSystemProperty("line.separator");




    /**
     * <p>
     * The <code>java.home</code> System Property. Java installation directory.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.1
     */
    public static final String JAVA_HOME = getSystemProperty(JAVA_HOME_KEY);

    /**
     * <p>
     * The <code>java.io.tmpdir</code> System Property. Default temp file path.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.2
     */
    public static final String JAVA_IO_TMPDIR = getSystemProperty(JAVA_IO_TMPDIR_KEY);

    /**
     * <p>
     * The <code>java.library.path</code> System Property. List of paths to search when loading libraries.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.2
     */
    public static final String JAVA_LIBRARY_PATH = getSystemProperty("java.library.path");

    /**
     * <p>
     * The <code>java.runtime.name</code> System Property. Java Runtime Environment name.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since 2.0
     * @since Java 1.3
     */
    public static final String JAVA_RUNTIME_NAME = getSystemProperty("java.runtime.name");

    /**
     * <p>
     * The <code>java.runtime.version</code> System Property. Java Runtime Environment version.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since 2.0
     * @since Java 1.3
     */
    public static final String JAVA_RUNTIME_VERSION = getSystemProperty("java.runtime.version");

    /**
     * <p>
     * The <code>java.specification.name</code> System Property. Java Runtime Environment specification name.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.2
     */
    public static final String JAVA_SPECIFICATION_NAME = getSystemProperty("java.specification.name");

    /**
     * <p>
     * The <code>java.specification.vendor</code> System Property. Java Runtime Environment specification vendor.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.2
     */
    public static final String JAVA_SPECIFICATION_VENDOR = getSystemProperty("java.specification.vendor");

    /**
     * <p>
     * The <code>java.specification.version</code> System Property. Java Runtime Environment specification version.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.3
     */
    public static final String JAVA_SPECIFICATION_VERSION = getSystemProperty("java.specification.version");

    /**
     * <p>
     * The <code>java.util.prefs.PreferencesFactory</code> System Property. A class name.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since 2.1
     * @since Java 1.4
     */
    public static final String JAVA_UTIL_PREFS_PREFERENCES_FACTORY = 
        getSystemProperty("java.util.prefs.PreferencesFactory");

    /**
     * <p>
     * The <code>java.vendor</code> System Property. Java vendor-specific string.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.1
     */
    public static final String JAVA_VENDOR = getSystemProperty("java.vendor");

    /**
     * <p>
     * The <code>java.vendor.url</code> System Property. Java vendor URL.
     * </p>
     * 
     * <p>
     * Defaults to <code>null</code> if the runtime does not have
     * security access to read this property or the property does not exist.
     * </p>
     * 
     * <p>
     * This value is initialized when the class is loaded. If {@link System#setProperty(String,String)} or
     * {@link System#setProperties(java.util.Properties)} is called after this class is loaded, the value
     * will be out of sync with that System property.
     * </p>
     * 
     * @since Java 1.1
     */
    public static final String JAVA_VENDOR_URL = getSystemProperty("java.vendor.url");





    // -----------------------------------------------------------------------
    /**
     * <p>
     * Gets a System property, defaulting to <code>null</code> if the property cannot be read.
     * </p>
     * 
     * <p>
     * If a <code>SecurityException</code> is caught, the return value is <code>null</code> and a message is written to
     * <code>System.err</code>.
     * </p>
     * 
     * @param property
     *            the system property name
     * @return the system property value or <code>null</code> if a security problem occurs
     */
    private static String getSystemProperty(String property) {
        try {
            return System.getProperty(property);
        } catch (SecurityException ex) {
            // we are not allowed to look at this property
            System.err.println("Caught a SecurityException reading the system property '" + property
                    + "'; the SystemUtils property value will default to null.");
            return null;
        }
    }



    // -----------------------------------------------------------------------
    /**
     * <p>
     * SystemUtils instances should NOT be constructed in standard programming. Instead, the class should be used as
     * <code>SystemUtils.FILE_SEPARATOR</code>.
     * </p>
     * 
     * <p>
     * This constructor is public to permit tools that require a JavaBean instance to operate.
     * </p>
     */
    public SystemUtils() {
        super();
    }

    /**
     * Android is guaranteed to be Java 5 or better.
     */
    public static  boolean isJavaVersionAtLeast(float f) { 
           return true;
    }

}
