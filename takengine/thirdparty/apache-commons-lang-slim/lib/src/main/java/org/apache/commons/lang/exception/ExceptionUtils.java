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
package org.apache.commons.lang.exception;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ClassUtils;

import org.apache.commons.lang.StringUtils;


/**
 * <p>Provides utilities for manipulating and examining 
 * <code>Throwable</code> objects.</p>
 *
 * @author Apache Software Foundation
 * @author Daniel L. Rall
 * @author Dmitri Plotnikov
 * @author <a href="mailto:ggregory@seagullsw.com">Gary Gregory</a>
 * @author Pete Gieser
 * @since 1.0
 * @version $Id: ExceptionUtils.java 905837 2010-02-02 23:32:11Z niallp $
 */
public class ExceptionUtils {
    
    /**
     * <p>Used when printing stack frames to denote the start of a
     * wrapped exception.</p>
     *
     * <p>Package private for accessibility by test suite.</p>
     */
    static final String WRAPPED_MARKER = " [wrapped] ";

    // Lock object for CAUSE_METHOD_NAMES
    private static final Object CAUSE_METHOD_NAMES_LOCK = new Object();
    
    /**
     * <p>The names of methods commonly used to access a wrapped exception.</p>
     */
    private static String[] CAUSE_METHOD_NAMES = {
        "getCause",
        "getNextException",
        "getTargetException",
        "getException",
        "getSourceException",
        "getRootCause",
        "getCausedByException",
        "getNested",
        "getLinkedException",
        "getNestedException",
        "getLinkedCause",
        "getThrowable",
    };

    /**
     * <p>The Method object for Java 1.4 getCause.</p>
     */
    private static final Method THROWABLE_CAUSE_METHOD;

    /**
     * <p>The Method object for Java 1.4 initCause.</p>
     */
    private static final Method THROWABLE_INITCAUSE_METHOD;
    
    static {
        Method causeMethod;
        try {
            causeMethod = Throwable.class.getMethod("getCause", (Class)null);
        } catch (Exception e) {
            causeMethod = null;
        }
        THROWABLE_CAUSE_METHOD = causeMethod;
        try {
            causeMethod = Throwable.class.getMethod("initCause", new Class[]{Throwable.class});
        } catch (Exception e) {
            causeMethod = null;
        }
        THROWABLE_INITCAUSE_METHOD = causeMethod;
    }
    
    /**
     * <p>
     * Public constructor allows an instance of <code>ExceptionUtils</code> to be created, although that is not
     * normally necessary.
     * </p>
     */
    public ExceptionUtils() {
        super();
    }





    /**
     * Returns the given list as a <code>String[]</code>.
     * @param list a list to transform.
     * @return the given list as a <code>String[]</code>.
     */
    private static String[] toArray(List list) {
        return (String[]) list.toArray(new String[0]);
    }

    /**
     * Returns {@link #CAUSE_METHOD_NAMES} as a List.
     *
     * @return {@link #CAUSE_METHOD_NAMES} as a List.
     */
    private static ArrayList getCauseMethodNameList() {
        synchronized(CAUSE_METHOD_NAMES_LOCK) {
            return new ArrayList(Arrays.asList(CAUSE_METHOD_NAMES));
        }
    }



    //-----------------------------------------------------------------------
    /**
     * <p>Introspects the <code>Throwable</code> to obtain the cause.</p>
     *
     * <p>The method searches for methods with specific names that return a 
     * <code>Throwable</code> object. This will pick up most wrapping exceptions,
     * including those from JDK 1.4, and
     * {@link org.apache.commons.lang.exception.NestableException NestableException}.
     * The method names can be added to using {@link #addCauseMethodName(String)}.</p>
     *
     * <p>The default list searched for are:</p>
     * <ul>
     *  <li><code>getCause()</code></li>
     *  <li><code>getNextException()</code></li>
     *  <li><code>getTargetException()</code></li>
     *  <li><code>getException()</code></li>
     *  <li><code>getSourceException()</code></li>
     *  <li><code>getRootCause()</code></li>
     *  <li><code>getCausedByException()</code></li>
     *  <li><code>getNested()</code></li>
     * </ul>
     * 
     * <p>In the absence of any such method, the object is inspected for a
     * <code>detail</code> field assignable to a <code>Throwable</code>.</p>
     *
     * <p>If none of the above is found, returns <code>null</code>.</p>
     *
     * @param throwable  the throwable to introspect for a cause, may be null
     * @return the cause of the <code>Throwable</code>,
     *  <code>null</code> if none found or null throwable input
     * @since 1.0
     */
    public static Throwable getCause(Throwable throwable) {
        synchronized(CAUSE_METHOD_NAMES_LOCK) {
            return getCause(throwable, CAUSE_METHOD_NAMES);
        }
    }

    /**
     * <p>Introspects the <code>Throwable</code> to obtain the cause.</p>
     *
     * <ol>
     * <li>Try known exception types.</li>
     * <li>Try the supplied array of method names.</li>
     * <li>Try the field 'detail'.</li>
     * </ol>
     *
     * <p>A <code>null</code> set of method names means use the default set.
     * A <code>null</code> in the set of method names will be ignored.</p>
     *
     * @param throwable  the throwable to introspect for a cause, may be null
     * @param methodNames  the method names, null treated as default set
     * @return the cause of the <code>Throwable</code>,
     *  <code>null</code> if none found or null throwable input
     * @since 1.0
     */
    public static Throwable getCause(Throwable throwable, String[] methodNames) {
        if (throwable == null) {
            return null;
        }
        Throwable cause = getCauseUsingWellKnownTypes(throwable);
        if (cause == null) {
            if (methodNames == null) {
                synchronized(CAUSE_METHOD_NAMES_LOCK) {
                    methodNames = CAUSE_METHOD_NAMES;
                }
            }
            for (int i = 0; i < methodNames.length; i++) {
                String methodName = methodNames[i];
                if (methodName != null) {
                    cause = getCauseUsingMethodName(throwable, methodName);
                    if (cause != null) {
                        break;
                    }
                }
            }

            if (cause == null) {
                cause = getCauseUsingFieldName(throwable, "detail");
            }
        }
        return cause;
    }

    /**
     * <p>Introspects the <code>Throwable</code> to obtain the root cause.</p>
     *
     * <p>This method walks through the exception chain to the last element,
     * "root" of the tree, using {@link #getCause(Throwable)}, and
     * returns that exception.</p>
     *
     * <p>From version 2.2, this method handles recursive cause structures
     * that might otherwise cause infinite loops. If the throwable parameter
     * has a cause of itself, then null will be returned. If the throwable
     * parameter cause chain loops, the last element in the chain before the
     * loop is returned.</p>
     *
     * @param throwable  the throwable to get the root cause for, may be null
     * @return the root cause of the <code>Throwable</code>,
     *  <code>null</code> if none found or null throwable input
     */
    public static Throwable getRootCause(Throwable throwable) {
        List list = getThrowableList(throwable);
        return (list.size() < 2 ? null : (Throwable)list.get(list.size() - 1));
    }

    /**
     * <p>Finds a <code>Throwable</code> for known types.</p>
     * 
     * <p>Uses <code>instanceof</code> checks to examine the exception,
     * looking for well known types which could contain chained or
     * wrapped exceptions.</p>
     *
     * @param throwable  the exception to examine
     * @return the wrapped exception, or <code>null</code> if not found
     */
    private static Throwable getCauseUsingWellKnownTypes(Throwable throwable) {
         if (throwable instanceof SQLException) {
            return ((SQLException) throwable).getNextException();
        } else if (throwable instanceof InvocationTargetException) {
            return ((InvocationTargetException) throwable).getTargetException();
        } else {
            return null;
        }
    }

    /**
     * <p>Finds a <code>Throwable</code> by method name.</p>
     *
     * @param throwable  the exception to examine
     * @param methodName  the name of the method to find and invoke
     * @return the wrapped exception, or <code>null</code> if not found
     */
    private static Throwable getCauseUsingMethodName(Throwable throwable, String methodName) {
        Method method = null;
        try {
            method = throwable.getClass().getMethod(methodName, (Class)null);
        } catch (NoSuchMethodException ignored) {
            // exception ignored
        } catch (SecurityException ignored) {
            // exception ignored
        }

        if (method != null && Throwable.class.isAssignableFrom(method.getReturnType())) {
            try {
                return (Throwable) method.invoke(throwable, ArrayUtils.EMPTY_OBJECT_ARRAY);
            } catch (IllegalAccessException ignored) {
                // exception ignored
            } catch (IllegalArgumentException ignored) {
                // exception ignored
            } catch (InvocationTargetException ignored) {
                // exception ignored
            }
        }
        return null;
    }

    /**
     * <p>Finds a <code>Throwable</code> by field name.</p>
     *
     * @param throwable  the exception to examine
     * @param fieldName  the name of the attribute to examine
     * @return the wrapped exception, or <code>null</code> if not found
     */
    private static Throwable getCauseUsingFieldName(Throwable throwable, String fieldName) {
        Field field = null;
        try {
            field = throwable.getClass().getField(fieldName);
        } catch (NoSuchFieldException ignored) {
            // exception ignored
        } catch (SecurityException ignored) {
            // exception ignored
        }

        if (field != null && Throwable.class.isAssignableFrom(field.getType())) {
            try {
                return (Throwable) field.get(throwable);
            } catch (IllegalAccessException ignored) {
                // exception ignored
            } catch (IllegalArgumentException ignored) {
                // exception ignored
            }
        }
        return null;
    }



    /**
     * <p>Returns the list of <code>Throwable</code> objects in the
     * exception chain.</p>
     *
     * <p>A throwable without cause will return a list containing
     * one element - the input throwable.
     * A throwable with one cause will return a list containing
     * two elements. - the input throwable and the cause throwable.
     * A <code>null</code> throwable will return a list of size zero.</p>
     *
     * <p>This method handles recursive cause structures that might
     * otherwise cause infinite loops. The cause chain is processed until
     * the end is reached, or until the next item in the chain is already
     * in the result set.</p>
     *
     * @param throwable  the throwable to inspect, may be null
     * @return the list of throwables, never null
     * @since Commons Lang 2.2
     */
    public static List getThrowableList(Throwable throwable) {
        List list = new ArrayList();
        while (throwable != null && list.contains(throwable) == false) {
            list.add(throwable);
            throwable = ExceptionUtils.getCause(throwable);
        }
        return list;
    }







    //-----------------------------------------------------------------------
    /**
     * Gets a short message summarising the exception.
     * <p>
     * The message returned is of the form
     * {ClassNameWithoutPackage}: {ThrowableMessage}
     *
     * @param th  the throwable to get a message for, null returns empty string
     * @return the message, non-null
     * @since Commons Lang 2.2
     */
    public static String getMessage(Throwable th) {
        if (th == null) {
            return "";
        }
        String clsName = ClassUtils.getShortClassName(th, null);
        String msg = th.getMessage();
        return clsName + ": " + StringUtils.defaultString(msg);
    }



}
