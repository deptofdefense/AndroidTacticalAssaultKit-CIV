
package com.atakmap.comms;

import com.atakmap.coremap.log.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

/**
 * Factory for creating a variety of standard sockets for use by plugins and by core
 * ATAK.
 */
public class SocketFactory {

    private final static String TAG = "SocketFactory";

    private static SocketFactory _instance;

    private SocketFactory() {
    }

    /**
     * Provides the current socket factory.
     * @return SocketFactory the socket factory
     */
    public synchronized static SocketFactory getSocketFactory() {
        if (_instance == null)
            _instance = new SocketFactory();
        return _instance;
    }

    /**
     * Creates a multicast socket with the reuse flag set.    If an error occurs setting the reuse
     * flag, the socket is still created.
     * @return the multicast socket
     * @throws IOException thrown if the socket cannot be created
     */
    public MulticastSocket createMulticastSocket() throws IOException {
        MulticastSocket retVal = new MulticastSocket(null);
        try {
            retVal.setReuseAddress(true);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
        return retVal;
    }

    /**
     * Creates a multicast socket with the reuse flag set on a specific port.    If an error occurs
     * setting the reuse flag, the socket is still created.
     * @param port the multicast socket port
     * @return the multicast socket
     * @throws IOException thrown if the socket cannot be created
     */
    public MulticastSocket createMulticastSocket(final int port)
            throws IOException {
        MulticastSocket retVal = new MulticastSocket(null);
        try {
            retVal.setReuseAddress(true);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
        retVal.bind(new InetSocketAddress(port));

        return retVal;
    }

    /**
     * Creates a datagram socket with the reuse flag set on a specific port.    If an error occurs
     * setting the reuse flag, the socket is still created.   Please note, the reuse flag does not
     * guarantee that multiple sockets will receive the data.   It only makes sure that the socket
     * can be created.
     * @return the datagram socket
     * @throws SocketException thrown if the socket cannot be created
     */
    public DatagramSocket createDatagramSocket() throws SocketException {
        DatagramSocket retVal = new DatagramSocket();
        try {
            retVal.setReuseAddress(true);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
        return retVal;
    }

    /**
     * Creates a datagram socket with the reuse flag set on a specific port.    If an error occurs
     * setting the reuse flag, the socket is still created.   Please note, the reuse flag does not
     * guarantee that multiple sockets will receive the data.   It only makes sure that the socket
     * can be created.
     * @param port the multicast socket port
     * @return the datagram socket
     * @throws SocketException thrown if the socket cannot be created
     */
    public DatagramSocket createDatagramSocket(final int port)
            throws SocketException {
        DatagramSocket retval = new DatagramSocket(null);
        try {
            retval.setReuseAddress(true);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
        retval.bind(new InetSocketAddress(port));

        return retval;
    }

}
