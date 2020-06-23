/*
 * OpenNMEA - A Java library for parsing NMEA 0183 data sentences
 * Copyright (C)2006 Joey Gannon
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package gnu.nmea;

/**
 * A generic runtime exception for OpenNMEA classes
 * 
 * @author Joey Gannon
 * @version 1.00, 08/11/06
 * @since OpenNMEA v0.1
 */
class OpenNMEAException extends RuntimeException
{
    private static final long serialVersionUID = 1181307900355786772L;

    /** Constructs a new runtime exception with <code>null</code> as its
     * detail message.  The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause}.
     * @see java.lang.RuntimeException#RuntimeException()
     */
    public OpenNMEAException()
    {
    }

    /** Constructs a new runtime exception with the specified detail message.
     * The cause is not initialized, and may subsequently be initialized by a
     * call to {@link #initCause}.
     *
     * @param s the detail message. The detail message is saved for 
     *          later retrieval by the {@link #getMessage()} method.
     * @see java.lang.RuntimeException#RuntimeException(String)
     */
    public OpenNMEAException(String s)
    {
        super(s);
    }

    /** Constructs a new runtime exception with the specified cause and a
     * detail message of <tt>(cause==null ? null : cause.toString())</tt>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).  This constructor is useful for runtime exceptions
     * that are little more than wrappers for other throwables.
     *
     * @param t the cause (which is saved for later retrieval by the
     *          {@link #getCause()} method).  (A <tt>null</tt> value is
     *          permitted, and indicates that the cause is nonexistent or
     *          unknown.)
     * @see java.lang.RuntimeException#RuntimeException(Throwable)
     */
    public OpenNMEAException(Throwable t)
    {
        super(t);
    }

    /**
     * Constructs a new runtime exception with the specified detail message and
     * cause.  <p>Note that the detail message associated with
     * <code>cause</code> is <i>not</i> automatically incorporated in
     * this runtime exception's detail message.
     *
     * @param s the detail message (which is saved for later retrieval
     *          by the {@link #getMessage()} method).
     * @param t the cause (which is saved for later retrieval by the
     *          {@link #getCause()} method).  (A <tt>null</tt> value is
     *          permitted, and indicates that the cause is nonexistent or
     *          unknown.)
     * @see java.lang.RuntimeException#RuntimeException(String, Throwable)
     */
    public OpenNMEAException(String s, Throwable t)
    {
        super(s, t);
    }
}
