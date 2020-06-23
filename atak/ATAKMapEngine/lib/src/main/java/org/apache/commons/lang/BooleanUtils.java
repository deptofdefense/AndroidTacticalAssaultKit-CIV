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
 * <p>Operations on boolean primitives and Boolean objects.</p>
 *
 * <p>This class tries to handle <code>null</code> input gracefully.
 * An exception will not be thrown for a <code>null</code> input.
 * Each method documents its behaviour in more detail.</p>
 * 
 * <p>#ThreadSafe#</p>
 * @author Apache Software Foundation
 * @author Matthew Hawthorne
 * @author Gary Gregory
 * @since 2.0
 * @version $Id: BooleanUtils.java 1057037 2011-01-09 21:35:32Z niallp $
 */
public class BooleanUtils {

    /**
     * <p><code>BooleanUtils</code> instances should NOT be constructed in standard programming.
     * Instead, the class should be used as <code>BooleanUtils.toBooleanObject(true);</code>.</p>
     *
     * <p>This constructor is public to permit tools that require a JavaBean instance
     * to operate.</p>
     */
    public BooleanUtils() {
      super();
    }






    //-----------------------------------------------------------------------
    /**
     * <p>Boolean factory that avoids creating new Boolean objecs all the time.</p>
     * 
     * <p>This method was added to JDK1.4 but is available here for earlier JDKs.</p>
     *
     * <pre>
     *   BooleanUtils.toBooleanObject(false) = Boolean.FALSE
     *   BooleanUtils.toBooleanObject(true)  = Boolean.TRUE
     * </pre>
     *
     * @param bool  the boolean to convert
     * @return Boolean.TRUE or Boolean.FALSE as appropriate
     */
    public static Boolean toBooleanObject(boolean bool) {
        return bool ? Boolean.TRUE : Boolean.FALSE;
    }




    
    // Boolean to Integer methods
    //-----------------------------------------------------------------------
    /**
     * <p>Converts a boolean to an int using the convention that
     * <code>zero</code> is <code>false</code>.</p>
     *
     * <pre>
     *   BooleanUtils.toInteger(true)  = 1
     *   BooleanUtils.toInteger(false) = 0
     * </pre>
     *
     * @param bool  the boolean to convert
     * @return one if <code>true</code>, zero if <code>false</code>
     */
    public static int toInteger(boolean bool) {
        return bool ? 1 : 0;
    }
    


    /**
     * <p>Converts a boolean to an int specifying the conversion values.</p>
     * 
     * <pre>
     *   BooleanUtils.toInteger(true, 1, 0)  = 1
     *   BooleanUtils.toInteger(false, 1, 0) = 0
     * </pre>
     *
     * @param bool  the to convert
     * @param trueValue  the value to return if <code>true</code>
     * @param falseValue  the value to return if <code>false</code>
     * @return the appropriate value
     */
    public static int toInteger(boolean bool, int trueValue, int falseValue) {
        return bool ? trueValue : falseValue;
    }
    
    /**
     * <p>Converts a Boolean to an int specifying the conversion values.</p>
     * 
     * <pre>
     *   BooleanUtils.toInteger(Boolean.TRUE, 1, 0, 2)  = 1
     *   BooleanUtils.toInteger(Boolean.FALSE, 1, 0, 2) = 0
     *   BooleanUtils.toInteger(null, 1, 0, 2)          = 2
     * </pre>
     *
     * @param bool  the Boolean to convert
     * @param trueValue  the value to return if <code>true</code>
     * @param falseValue  the value to return if <code>false</code>
     * @param nullValue  the value to return if <code>null</code>
     * @return the appropriate value
     */
    public static int toInteger(Boolean bool, int trueValue, int falseValue, int nullValue) {
        if (bool == null) {
            return nullValue;
        }
        return bool.booleanValue() ? trueValue : falseValue;
    }
    

    

    

    /**
     * <p>Converts a Boolean to a String returning one of the input Strings.</p>
     * 
     * <pre>
     *   BooleanUtils.toString(Boolean.TRUE, "true", "false", null)   = "true"
     *   BooleanUtils.toString(Boolean.FALSE, "true", "false", null)  = "false"
     *   BooleanUtils.toString(null, "true", "false", null)           = null;
     * </pre>
     *
     * @param bool  the Boolean to check
     * @param trueString  the String to return if <code>true</code>,
     *  may be <code>null</code>
     * @param falseString  the String to return if <code>false</code>,
     *  may be <code>null</code>
     * @param nullString  the String to return if <code>null</code>,
     *  may be <code>null</code>
     * @return one of the three input Strings
     */
    public static String toString(Boolean bool, String trueString, String falseString, String nullString) {
        if (bool == null) {
            return nullString;
        }
        return bool.booleanValue() ? trueString : falseString;
    }
    

    /**
     * <p>Converts a boolean to a String returning one of the input Strings.</p>
     * 
     * <pre>
     *   BooleanUtils.toString(true, "true", "false")   = "true"
     *   BooleanUtils.toString(false, "true", "false")  = "false"
     * </pre>
     *
     * @param bool  the Boolean to check
     * @param trueString  the String to return if <code>true</code>,
     *  may be <code>null</code>
     * @param falseString  the String to return if <code>false</code>,
     *  may be <code>null</code>
     * @return one of the two input Strings
     */
    public static String toString(boolean bool, String trueString, String falseString) {
        return bool ? trueString : falseString;
    }


}
