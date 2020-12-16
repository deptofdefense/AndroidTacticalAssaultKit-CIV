package com.atakmap.commoncommo;

import java.io.File;
import java.util.Vector;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.net.URI;
import java.util.Map;
import java.util.List;


/**
 * The main interface to the Commo library.  An instance of this class
 * will provide most functionality of the library.  As a general rule, 
 * objects implementing any of the listener interfaces must remain valid 
 * from the time they are added until either they are removed using
 * the appropriate Commo method or this Commo instance is shutdown.
 *
 * Before creating the first instance of a Commo object, callers
 * must externally load and initialize the native libraries used internally
 * by Commo.  Specifically as of the time of this writing, this is libxml2,
 * openssl, and libcurl. openssl must be initialized to support use by multiple
 * threads. A method is provided here to do a compatible, default set of
 * initialization calls to these libraries.
 */
public class Commo {
    /**
     * Value to pass to setMissionPackageLocalPort to indicate that
     * the local mission package serving web server should be disabled
     */ 
    public static final int MPIO_LOCAL_PORT_DISABLE = -1;

    private static Map<FileIOProvider, Long> ioProviders = new IdentityHashMap<FileIOProvider, Long>();

    // XXX - removed due to removal of load function, see below
    //private static boolean nativeLoaded;
    private long nativePtr;


    
    /**
     * Loads all native libraries used by the Java Commo library.
     * This method does NOT invoke basic initialization of dependent
     * libraries, it simply loads them. They MUST be initialized properly
     * before Commo instances are created!
     * initNativeLibraries can be used instead of this to load
     * and apply a default initialization routine.
     * @throw CommoException if library loading fails for some reason
     *
     * XXX: Function and capability removed because some customers don't
     *      allow System.loadLibrary()....
    public static synchronized void loadNativeLibraries() throws CommoException {
        String curLib = "";
        try {
            String vmname = System.getProperty("java.vm.name");
            if (!vmname.startsWith("Java HotSpot") && !vmname.startsWith("OpenJDK") ) {
                // Android
            }
            curLib = "commoncommojni";
            System.loadLibrary(curLib);
        } catch (Error e) {
            e.printStackTrace();
            throw new CommoException("Failed to load native library: " + curLib + "  " + e);
        }
        nativeLoaded = true;
    }
    */
    
    /**
     * Loads and attempts to do default initialization of native libraries
     * used by the Java Commo library.
     * If external custom initialization is desired, callers may use
     * loadNativeLibraries() instead to simply load the libraries without
     * invoking any of their initialization routines.
     * @throw CommoException if loading or initialization fails for
     *                       any reason
     *
     * XXX: deprecated version removed and reworked to new version below
     *      because some customers don't allow system.loadlibrary()...
    public static synchronized void initNativeLibraries() throws CommoException {
        loadNativeLibraries();
        if (!initNativeLibrariesNative())
            throw new CommoException("Failed to initialize Commo's dependent libraries");
    }
     */


    /**
     * Initializes all third party libraries used by commo.  Libraries must
     * have already been loaded externally by the client application.
     * It is dangerous to call this more than once or from multiple threads.
     * This is a convenience - the application may do the third party library
     * initialization itself instead. Refer to external commo documentation for
     * more about how to do this.
     * @throws CommoException if initialization fails
     */
    public static synchronized void initThirdpartyNativeLibraries() throws CommoException {
        if (!initNativeLibrariesNative())
            throw new CommoException("Failed to initialize Commo's dependent libraries");
    }
    

    /**
     * Create a new Commo instance with the specified logging implementation,
     * which is required.  The specified UID and callsign will be used
     * to identify this system in outbound messages generated internally
     * by the library.
     *
     * @param logger a CommoLogger implementation used to log all
     *               information from this Commo instance
     * @param ourUID string that uniquely identifies this TAK device
     * @param ourCallsign callsign to use in messages originating from
     *                    this instance
     * @throws IllegalStateException if native libraries have not been loaded
     * @throws CommoException if any other error occurs
     */
    public Commo(CommoLogger logger, String ourUID, String ourCallsign) throws CommoException
    {
        nativePtr = 0;
        // XXX removed due to removal of internal loads
        //if (!nativeLoaded)
        //    throw new IllegalStateException("Native libraries not loaded");
        if (ourUID == null || ourCallsign == null)
            throw new CommoException("UID and callsign are required");
        nativePtr = commoCreateNative(logger, ourUID, ourCallsign);
        if (nativePtr == 0)
            throw new CommoException();
    }
    
    /**
     * Overridden to invoke shutdown()
     */
    protected void finalize() throws Throwable
    {
        shutdown();
        super.finalize();
    }
    
    
    /**
     * Configure client side helper interface for mission package transfers
     * on this Commo instance.
     * The caller must supply an implementation of the 
     * MissionPackageIO interface that will remain operable
     * until shutdown() is invoked; once an implementation is setup
     * via this call, it cannot be changed/swapped out.
     * After a successful return, the mission package family of functions
     * may be invoked.
     * Throws CommoException if missionPackageIO is null, or
     * if this method has previously been called successfully for this
     * Commo instance.
     * @param missionPackageIO reference to an implementation of
     *                         the MissionPackageIO interface that
     *                         this Commo instance can use to interface
     *                         with the application during mission package
     *                         transfers
     */
    public void setupMissionPackageIO(MissionPackageIO missionPackageIO)
                                       throws CommoException
    {
        if (!setupMissionPackageIONative(nativePtr, missionPackageIO))
            throw new CommoException("Unable to enable mission package IO");
    }

    
    /**
     * Enables simple file transfers for this Commo instance.
     * Simple file transfers are transfers of files via traditional
     * client-server protocols (where commo is the client)
     * and is not associated with CoT sending/receiving or
     * mission package management.
     * Once enabled, transfers cannot be disabled.
     * The caller must supply an implementation of the 
     * SimpleFileIO interface that will remain operable
     * until shutdown() is invoked.
     * After a successful return, simpleFileTransferInit()
     * can be invoked to initiate file transfers with an external server.
     * Throws CommoException if the simpleIO is null, or
     * if this method has previously been called successfully for this
     * Commo instance.
     * @param simpleIO reference to an implementation of
     *                         the SimpleFileIO interface that
     *                         this Commo instance can use to interface
     *                         with the application during simple file
     *                         transfers
     * @throw CommoException if simpleIO is null or if this method has
     *        previously been called successfully for this Commo instance.
     */
    public void enableSimpleFileIO(SimpleFileIO simpleIO)
                                       throws CommoException
    {
        if (!enableSimpleFileIONative(nativePtr, simpleIO))
            throw new CommoException("Unable to enable simple file IO");
    }
    
    
    /**
     * Shuts down all background operations associated with this Commo object.
     * All listeners are immediately discarded, any background transfers or
     * other operations immediately cease, and all Contacts are considered
     * "gone".
     * Attempting future operations other then shutdown() on this Commo object
     * will simply result in an IllegalStateException being thrown.
     * Note that during this method's execution, any previously added 
     * Listener (for any of the several varieties of Listeners supported by 
     * this class) may still receive some method invocations from the Commo
     * library, but by the time this method returns the Commo library will
     * no longer invoke any methods on those listeners nor will it hold
     * any references to said listeners.
     * Shutdown is also recursive in that all objects created and
     * any subinterfaces by this Commo object will also be shut down,
     * no longer functional, and made eligible for garbage collection.
     */
    public synchronized void shutdown()
    {
        if (nativePtr != 0) {
            commoDestroyNative(nativePtr);
            nativePtr = 0;
        }
    }
    

    /**
     * Changes the callsign of the local system, as used for sending
     * any outbound messages generated internally by Commo.
     * @param callsign the new callsign to use in future outgoing messages
     */
    public void setCallsign(String callsign)
    {
        if (callsign == null)
            throw new IllegalArgumentException("Callsign cannot be null");
        setCallsignNative(nativePtr, callsign);
    }
    

    /**
     * Enables workarounds for quirks of operating on the 
     * Marine Air Ground TABlet (MAGTAB) from kranzetech.
     * Do NOT enable this on other devices; it will cause
     * many things to not work properly.
     * 
     * @param en true to enable workarounds, false otherwise.
     */
    public void setMagtabWorkaroundEnabled(boolean en)
    {
        setMagtabWorkaroundEnabledNative(nativePtr, en);
    }
    
    
    /**
     * Controls what type of endpoint should be preferred when sending to
     * contacts when both types of endpoints are available.  The default
     * is to NOT prefer stream endpoints (that is, mesh/local endpoints
     * are preferred).
     *
     * @param preferStream true to prefer streaming endpoints when
     *                     sending to contacts instead of mesh/local endpoints.
     */
    public void setPreferStreamEndpoint(boolean preferStream)
    {
        setPreferStreamEndpointNative(nativePtr, preferStream);
    }
    

    /**
     * Advertise our endpoint using UDP protocol instead of the
     * default of TCP. USE THIS FUNCTION WITH CAUTION:
     * enabling this advertises us to others as receiving via UDP
     * which is an unreliable protocol where messages may be corrupted
     * or discarded entirely during network transit.
     * If you do use this, it should be set as early as possible in a session
     * and changed only very infrequently as remote clients expect to see
     * a stable endpoint advertisement generally.
     *
     * @param en true to send out UDP endpoints from now on, false to
     *           send out TCP endpoints
     */
    public void setAdvertiseEndpointAsUdp(boolean en)
    {
        setAdvertiseEndpointAsUdpNative(nativePtr, en);
    }
    

    /**
     * Enables encryption/decryption for non-streaming CoT interfaces
     * using the specified keys, or disables it entirely.
     * Arrays MUST be precisely 32 bytes long and contain unique keys.
     * Specify null for both to disable encryption/decryption (the default).
     * 
     */
    public void setCryptoKeys(byte[] auth, byte[] crypt) throws CommoException
    {
        if ((auth == null) ^ (crypt == null))
            throw new CommoException("Cannot specify null for just one key");
        if (auth != null && (auth.length != 32 || crypt.length != 32))
            throw new CommoException("Keys must be exactly 32 bytes long");
        
        if (!setCryptoKeysNative(nativePtr, auth, crypt))
            throw new CommoException("Failed to set keys - keys must be unique!");
    }
    
    
    /**
     * Enables or disables the ability of datagram sockets created by
     * the system to share their local address with other sockets on the system
     * created by other applications.  Note that this option is unreliable
     * when unicast datagrams are sent to the local system, as it is indeterminate
     * which local socket will receive the unicast datagrams.  Use this option
     * with caution if you expect to be able to receive unicasted datagrams.
     * @param en true to allow address reuse, false otherwise
     */
    public void setEnableAddressReuse(boolean en)
    {
        setEnableAddressReuseNative(nativePtr, en);
    }


    /**
     * Enables or disables loopback of multicast data to the local system.
     * If enabled, multicasted data will be made available to other
     * listening sockets on the same local system. Note that this inheritly
     * means that the current Commo instance will also receive back copies
     * of the data if it is listening (addInboundInterface()) on the same
     * multicast address and port as an outbound interface
     * (addBroadcastInterface()); thus the application must be prepared to
     * receive and process copies of messages it broadcasts out.
     * This option is not supported on Windows hosts.
     * The default is disabled. Toggling this option will cause
     * all internal sockets to be rebuilt.
     * @param en true to enable looping back of multicasts,, false otherwise
     */
    public void setMulticastLoopbackEnabled(boolean en)
    {
        setMulticastLoopbackEnabledNative(nativePtr, en);
    }


    /**
     * Sets the global TTL for broadcast messages.  If the
     * ttl is out of valid range, it will be clamped to the nearest
     * valid value.
     * @param ttl the new TTL to use in future broadcast messages
     */
    public void setTTL(int ttl)
    {
        setTTLNative(nativePtr, ttl);
    }
    

    /**
     * Sets global UDP timeout value, in seconds; if the given # of seconds
     * elapses without a socket receiving data, it will be destroyed
     * and rebuilt. If the timed-out socket was a multicast socket,
     * multicast joins will be re-issued.
     * 0 means never timeout. Default is 30 seconds.
     *
     * @param seconds timeout value in seconds - use 0 for "never"
     */
    public void setUdpNoDataTimeout(int seconds)
    {
        setUdpNoDataTimeoutNative(nativePtr, seconds);
    }

    /**
     * Sets the global timeout in seconds used for all outgoing
     * TCP connections
     * (except those used to upload or download mission packages).
     * @param seconds timeout in seconds
     */
    public void setTcpConnTimeout(int seconds)
    {
        setTcpConnTimeoutNative(nativePtr, seconds);
    }
    

    /**
     * Sets the local web server port to use for outbound peer-to-peer
     * mission package transfers, or disables this functionality.
     * The local web port number must be a valid, free, local port
     * for TCP listening or the constant MPIO_LOCAL_PORT_DISABLE. 
     * On successful return, the server will be active on the specified port
     * and new outbound transfers (sendMissionPackage()) will use
     * this to host outbound transfers.
     * If this call fails, transfers using the local web server will be
     * disabled until a future call completes successfully (in other words,
     * will act as if this had been called with MPIO_LOCAL_PORT_DISABLE).
     *
     * Note carefully: calling this to change the port or disable the local
     * web server will cause all in-progress mission package sends to
     * be aborted.
     *
     * The default state is to have the local web server disabled, as if
     * this had been called with MPIO_LOCAL_PORT_DISABLE.
     *
     * @param localWebPort TCP port number to have the local web server
     *                     listen on, or MPIO_LOCAL_PORT_DISABLE.
     *                     The port must be free at the time of invocation.
     * @throw CommoException if the web port is not valid/available or
     *     setupMissionPackageIO has not yet been successfully invoked
     */
    public void setMissionPackageLocalPort(int localWebPort)
                                           throws CommoException
    {
        if (!setMPLocalPortNative(nativePtr, localWebPort))
            throw new CommoException();
    }


    /**
     * Sets the local https web server paramerters to use with the local
     * https server for outbound peer-to-peer
     * mission package transfers, or disables this functionality.
     * The local web port number must be a valid, free, local port
     * for TCP listening or the constant MPIO_LOCAL_PORT_DISABLE. 
     * The certificate data and password must be non-null except in
     * the specific case that localWebPort is MPIO_LOCAL_PORT_DISABLE.
     * Certificate data must be in pkcs#12 format and should represent
     * the complete certificate, key, and supporting certificates that
     * the server should use when negotiating with the client.
     * On successful return, the https server will be configured to use the
     * specified port and new outbound transfers (sendMissionPackage())
     * will use this to host outbound transfers.
     * Note that the https server also requires the http port to be enabled
     * and configured on a different port as the https function utilizes
     * the http server internally. If the http server is not enabled
     * (see setMissionPackageLocalPort()) the https server will remain
     * in a configured but disabled state until the http server is activated.
     * If this call fails, transfers using the local https server will be
     * disabled until a future call completes successfully (in other words,
     * will act as if this had been called with MPIO_LOCAL_PORT_DISABLE).
     *
     * Note carefully: calling this to change the port or disable the local
     * https web server will cause all in-progress mission package sends to
     * be aborted.
     *
     * The default state is to have the local https web server disabled, as if
     * this had been called with MPIO_LOCAL_PORT_DISABLE.
     *
     * @param localWebPort TCP port number to have the local https web server
     *                     listen on, or MPIO_LOCAL_PORT_DISABLE.
     *                     The port must be free at the time of invocation.
     * @throw CommoException if the web port is not valid/available,
     *     setupMissionPackageIO has not yet been successfully invoked,
     *     or the given certificate or certPass is invalid.
     */
    public void setMissionPackageLocalHttpsParams(int localWebPort,
            byte[] certificate, String certPass)
                                           throws CommoException
    {
        setMPLocalHttpsParamsNative(nativePtr, localWebPort, certificate,
                                   certificate == null ? 0 : certificate.length,
                                   certPass);
    }


    /**
     * Controls if mission package sends via TAK servers may or may
     * not be used.  The default, once mission package IO has been setup
     * via setupMissionPackageIO(), is enabled.
     * Note carefully: Disabling this after it was previously enabled
     * may cause some existing outbound mission package transfers to be
     * aborted!
     * @param enabled true to enable server-based transfers, false to disable
     */
    public void setMissionPackageViaServerEnabled(boolean enabled)
    {
        setMPViaServerEnabledNative(nativePtr, enabled);
    }


    /**
     * Sets the TCP port number to use when contacting a TAK
     * Server's http web api. Default is port 8080.
     *
     * @param serverPort tcp port to use
     * @throw CommoException if the port number is out of range
     */
    public void setMissionPackageHttpPort(int serverPort)
                                              throws CommoException
    {
        if (!setMissionPackageHttpPortNative(nativePtr, serverPort))
            throw new CommoException("value out of range");
    }
    

    /**
     * Sets the TCP port number to use when contacting a TAK
     * Server's https web api. Default is port 8443.
     *
     * @param serverPort tcp port to use
     * @throw CommoException if the port number is out of range
     */
    public void setMissionPackageHttpsPort(int serverPort)
                                               throws CommoException
    {
        if (!setMissionPackageHttpsPortNative(nativePtr, serverPort))
            throw new CommoException("value out of range");
    }
    

    /**
     * Set number of attempts to receive a mission package that a remote
     * device has given us a request to download.
     * Once this number of attempts has been exceeded, the transfer
     * will be considered failed. Minimum value is 1.
     * The default value is 10.
     *
     * @param nTries number of attempts to make
     * @throw CommoException if the value is out of range
     */
    public void setMissionPackageNumTries(int nTries) throws CommoException
    {
        if (!setMissionPackageNumTriesNative(nativePtr, nTries))
            throw new CommoException("value out of range");
    }

    /**
     * Set timeout, in seconds, for initiating connections to remote
     * hosts when transferring mission packages.  This is used when
     * receiving mission packages from TAK servers and/or other devices,
     * as well as uploading mission packages to TAK servers.
     * The minimum value is five (5) seconds; default value is 90 seconds.
     * New settings influence sunsequent transfers; currently ongoing transfers
     * use settings in place at the time they began.
     *
     * @param seconds connect timeout in seconds
     * @throw CommoException if the value is out of range
     */
    public void setMissionPackageConnTimeout(int seconds) throws CommoException
    {
        if (!setMissionPackageConnTimeoutNative(nativePtr, seconds))
            throw new CommoException("value out of range");
    }

    /**
     * Set timeout, in seconds, for data transfers. After this number
     * of seconds has elapsed with no data transfer progress taking place,
     * the transfer attempt is considered a failure.
     * This is used when receiving mission packages.
     * Minimum value is 15 seconds, default value is 120 seconds.
     * New settings influence sunsequent transfers; currently ongoing transfers
     * use settings in place at the time they began.
     *
     * @param seconds transfer timeout in seconds
     * @throw CommoException if the value is out of range
     */
    public void setMissionPackageTransferTimeout(int seconds) throws CommoException
    {
        if (!setMissionPackageTransferTimeoutNative(nativePtr, seconds))
            throw new CommoException("value out of range");
    }

    /**
     * Indicates if stream monitoring is to be performed or not.
     * If enabled, Commo will monitor incoming traffic on each
     * configured and active streaming interface.  If no traffic
     * arrives for a substantive period of time, commo will
     * send a "ping" CoT message to the streaming server and expect
     * a response. This will repeat for a short time, and, if
     * no reply is received, the streaming connection will be
     * shut down and an attempt to re-establish the connection
     * will be made a short time later.
     * Server "pong" responses, if they arrive, are never delivered
     * to the application's CoTMessageListener.
     * Default is enabled.
     * @param en true to enable monitoring, false to disable it
     */
    public void setStreamMonitorEnabled(boolean en)
    {
        setStreamMonitorEnabledNative(nativePtr, en);
    }
    
    /**
     * Gets the TAK protocol version current in use when sending things
     * via broadcastCoT(). Zero (0) is legacy XML, > 0 is binary
     * TAK protocol.
     * @return protocol version in use for broadcast
     */
    public int getBroadcastProto()
    {
        return getBroadcastProtoNative(nativePtr);
    }
    
    
    /**
     * Add a new outbound broadcast interface.  
     * The local interface address is specified by the
     * hardware (MAC) address in hwAddress.  
     * The types of data routed to this outbound
     * address are specified in "types"; at least one type must be specified.
     * The multicast destination is specified as a string in 
     * dotted-decimal notation ("239.1.1.1").
     * Outbound broadcast messages matching any of the types given will
     * be sent out this interface to the multicast address specified
     * to the specified port.
     * @param hwAddress array specifying the hardware/MAC address
     *                  of the local network interface to be used
     *                  for the broadcast
     * @param types the CoTMessageType(s) to be sent out this interface
     * @param mcastAddr the dotted-decimal notation of the
     *                  destination multicast address
     * @param destPort the destination port to send broadcasts out on
     * @return NetInterface object uniquely identifying the added interface.
     *         Remains valid until it is passed to removeBroadcastInterface.
     * @throws CommoException if any error occurs, including 
     *                        (but not limited to)
     *                        if mcastAddr does not represent a valid
     *                        multicast address, types does not contain at
     *                        least one element, or destPort is out of range
     */
    public PhysicalNetInterface addBroadcastInterface(byte[] hwAddress, CoTMessageType[] types, String mcastAddr, int destPort) throws CommoException {
        int[] typeInts = new int[types.length];
        int i = 0;
        for (CoTMessageType t : types)
            typeInts[i++] = t.getNativeVal();

        if (mcastAddr == null)
            throw new CommoException("Broadcast interface mcast address cannot be null");

        PhysicalNetInterface ret = 
            addBroadcastNative(nativePtr, hwAddress, hwAddress.length,
                               typeInts, typeInts.length, mcastAddr, destPort);
        if (ret == null)
            throw new CommoException();
        return ret;
    }
    
    /**
     * Add a new outbound broadcast interface that directs
     * all broadcasted messages of the matching type 
     * to the given UDP unicast destination.
     * This call varies from the other form of addBroadcastInteface()
     * in that it is for unicasted destinations whereas the other form
     * is for mulicasted destinations.
     * The types of data routed to this outbound
     * address are specified in "types"; at least one type must be specified.
     * The unicast destination is specified as a string in 
     * dotted-decimal notation ("10.0.0.2").
     * Outbound broadcast messages matching any of the types given will
     * be sent to the unicast address and port specified.
     * @param types the CoTMessageType(s) to be sent out this interface
     * @param unicastAddr the dotted-decimal notation of the
     *                  destination unicast address
     * @param destPort the destination port to send broadcasts out on
     * @return NetInterface object uniquely identifying the added interface.
     *         Remains valid until it is passed to removeBroadcastInterface.
     * @throws CommoException if any error occurs, including 
     *                        (but not limited to)
     *                        if unicastAddr does not represent a valid
     *                        unicast address, types does not contain at
     *                        least one element, or destPort is out of range
     */
    public PhysicalNetInterface addBroadcastInterface(CoTMessageType[] types, String unicastAddr, int destPort) throws CommoException {
        int[] typeInts = new int[types.length];
        int i = 0;
        for (CoTMessageType t : types)
            typeInts[i++] = t.getNativeVal();

        if (unicastAddr == null)
            throw new CommoException("Broadcast interface unicast address cannot be null");

        PhysicalNetInterface ret = 
            addUniBroadcastNative(nativePtr, typeInts, typeInts.length,
                                  unicastAddr, destPort);
        if (ret == null)
            throw new CommoException();
        return ret;
    }
    
    /**
     * Remove the specified interface from attempting to be used for broadcast.
     * The iface can be removed regardless of if it is in the up or down state.
     * When this call completes, the interface will no longer be used for any outgoing
     * messages. However it is possible that previously queued messages may be sent
     * during execution of this method.
     * After completion, the supplied NetInterface is considered invalid.
     *  
     * @param iface previously added PhysicalNetInterface to remove from service
     * @throws IllegalArgumentException if the supplied NetInterface was
     *                   not created via addBroadcastInterface() or was already
     *                   removed
     */
    public void removeBroadcastInterface(PhysicalNetInterface iface)
                                         throws IllegalArgumentException
    {
        long removeMe = iface.getNativePtr();
        if (removeMe == 0)
            throw new IllegalArgumentException("Interface already removed");
        if (!removeBroadcastNative(nativePtr, iface.getNativePtr()))
            throw new IllegalArgumentException("Interface invalid");
    }
    
    /**
     * Adds a new inbound listening interface on the specified port.
     * The hwAddress indicates the local interface upon which to listen.
     * If mcastAddrs are given (non-null), each element must be a
     * dotted-decimal notation of a multicast group to listen on; 
     * the library will tell the system it is interested in
     * the given multicast groups on the specified interfaces.
     * Depending on the value of forGenericData, the interface will be
     * expecting to receive CoT messages or any sort of generic data
     * and be posted to the corresponding type of registered listeners
     * (CoTMessageListener or GenericDataListener, respectively).
     * NOTE: Generic and non-generic interfaces cannot be configured 
     * against different hwAddress-identified interfaces on the same 
     * port number!
     * @param hwAddress array representing the MAC (hardware) address of 
     *                  the local interface to listen on
     * @param port the port number to listen on
     * @param mcastAddrs one or more multicast addresses to listen for
     *              traffic on (in addition to unicasted messages);
     *              each element is specified in the "dotted decimal" form.
     *              Can be null or an empty array to indicate that
     *              the interface is only to be used for unicast data.
     * @param forGenericData true if interface will expect and post generic
     *              data to GenericDataListeners or false to expect
     *              and post CoT messages to CoTMessageListeners
     * @return PhysicalNetInterface object uniquely identifying the
     *         added interface.  Remains valid as long as it is
     *         not passed to removeInboundInterface
     * @throws CommoException if any error occurs, including but not limited to
     *                        port out of range, mcastAddrs containing
     *                        any invalid multicast addresses,
     *                        specifying different forGenericData values
     *                        on the same port,
     *                        or the specified port on the specified interface
     *                        is already in use.
     */
    public PhysicalNetInterface addInboundInterface(byte[] hwAddress, 
                                          int port, String[] mcastAddrs,
                                          boolean forGenericData)
                                          throws CommoException
    {
        int mcaLen = mcastAddrs == null ? 0 : mcastAddrs.length;
        for (int i = 0; i < mcaLen; ++i) {
            if (mcastAddrs[i] == null)
                throw new CommoException("Cannot pass null multicast address for inbound interface");
        }
    
        PhysicalNetInterface ret = addInboundNative(nativePtr, hwAddress,
                                    hwAddress.length, 
                                    port, mcastAddrs, mcaLen, forGenericData);
        if (ret == null)
            throw new CommoException();
        return ret;
    }
    
    /**
     * Remove the specified interface from being used for message reception.
     * The iface can be removed regardless of if it is in the up or down state.
     * When this call completes, the interface will no longer be used.
     * However it is possible that previously received messages may be passed
     * to CoTMessageListeners during execution of this method.
     * After completion, the supplied PhysicalNetInterface is
     * considered invalid.
     *
     * @param iface previously added NetInterface to remove from service
     * @throws IllegalArgumentException if the supplied NetInterface 
     *                     was not created via addInboundInterface() or
     *                     was already removed
     */
    public void removeInboundInterface(PhysicalNetInterface iface) {
        long removeMe = iface.getNativePtr();
        if (removeMe == 0)
            throw new IllegalArgumentException("Interface already removed");
        if (!removeInboundNative(nativePtr, iface.getNativePtr()))
            throw new IllegalArgumentException("Interface invalid");
    }
    
    /**
     * Adds a new inbound TCP-based listening interface on the specified port.
     * Listens on all network interfaces. The interface will generally
     * be up most of the time, but may go down if there is some low-level 
     * system error. In this event, commo will attempt to re-establish
     * as a listener on the given port. 
     * @param port the local port number to listen on
     * @return TcpInboundNetInterface object uniquely identifying the
     *         added interface.  Remains valid as long as it is
     *         not passed to removeTcpInboundInterface
     * @throws CommoException if any error occurs, including but not limited to
     *                        port out of range, 
     *                        or the specified port is already in use.
     */
    public TcpInboundNetInterface addTcpInboundInterface(int port)
                                          throws CommoException
    {
        TcpInboundNetInterface ret = addTcpInboundNative(nativePtr, port);
        if (ret == null)
            throw new CommoException();
        return ret;
    }
    
    /**
     * Remove the specified inbound tcp-based interface from being 
     * used for message reception.
     * The iface can be removed regardless of if it is in the up or down state.
     * When this call completes, the interface will no longer be used.
     * However it is possible that previously received messages may be passed
     * to CoTMessageListeners during execution of this method.
     * After completion, the supplied TcpInboundNetInterface is
     * considered invalid.
     *
     * @param iface previously added NetInterface to remove from service
     * @throws IllegalArgumentException if the supplied NetInterface 
     *                     was not created via addTcpInboundInterface() or
     *                     was already removed
     */
    public void removeTcpInboundInterface(TcpInboundNetInterface iface) {
        long removeMe = iface.getNativePtr();
        if (removeMe == 0)
            throw new IllegalArgumentException("Interface already removed");
        if (!removeTcpInboundNative(nativePtr, iface.getNativePtr()))
            throw new IllegalArgumentException("Interface invalid");
    }
    
    /**
     * Adds a new streaming interface to a remote TAK server. 
     *
     * This Commo instance will attempt to establish and maintain
     * a connection with a TAK server at the specified hostname on the
     * specified remote port number.  The connection will be used to
     * receive CoT messages of all types from the TAK server, for non-broadcast
     * sending of all types of CoT messages to contacts known via
     * this interface, as well as for broadcasting CoTMessages of the types
     * specified. Depending on the arguments given, attempts to connect will
     * be made with plain TCP, SSL encrypted TCP, or SSL encrypted with
     * server authentication: specifying null for clientCert, trustStore,
     * username, and password results in a plain TCP connection.
     * Specifying non-null for clientCert requires non-null
     * trustStore to be non-null; this results in an SSL connection.
     * Additionally specifying non-null username and password will result
     * in an SSL connection that sends a TAK server authentication message
     * when connecting. Regardless of the type of connection, 
     * this Commo instance will attempt to create and restore the
     * server connection as needed until this interface is removed or this
     * Commo object is shutdown.
     * The returned interface is valid until removed via
     * removeStreamingInterfaceor this Commo object is shutdown.
     *  
     * @param hostname the resolvable hostname or "dotted decimal"
     *                 notation of the IP address of the server
     * @param port remote port number of the TAK server to connect to
     * @param types one or more CoTMessageTypes specifying the types
     *              of broadcast cot messages that will be sent out
     *              this interface;  may be null/empty if no broadcast
     *              messages should go out this interface
     * @param clientCert if the connection is SSL based, this holds the 
     *                   client's certificate chain in
     *                   PKCS #12 format. Use null for non-SSL connections
     * @param caCert if the connection is SSL based, this holds the
     *               certificate of the CA that signed the clientCert
     *               and that is to be used to verify the server cert,
     *               in PKCS #12 format. 
     *               Use null for non-SSL connections
     * @param certPassword for SSL connections, the passphrase to decode
     *                     the ca cert.
     *                     Use null for non-SSL connections
     * @param caCertPassword for SSL connections, the passphrase to decode
     *                     the ca cert.
     *                     Use null for non-SSL connections
     * @param username if non-null, an authentication message with the 
     *                 username and password specified will be sent to the
     *                 TAK server upon connection. If non-null, password
     *                 MUST also be non-null. Not used for non-SSL connections.
     * @param password the accompanying password to go with the username
     *                 in the authentication message
     * @return StreamingNetInterface object uniquely identifying
     *         the added interface.  Remains valid as long as it is
     *         not passed to removeStreamingInterface
     * @throws CommoException if an error occurs, including but not limited
     *                       to invalid certificates, invalid port numbers,
     *                       or invalid combinations of arguments
     */
    public StreamingNetInterface addStreamingInterface(String hostname,
            int port, CoTMessageType[] types, byte[] clientCert,
            byte[] caCert, String certPassword, 
            String caCertPassword,
            String username, String password) throws CommoException
    {
        int[] typeInts = new int[types.length];
        int i = 0;
        for (CoTMessageType t : types)
            typeInts[i++] = t.getNativeVal();
        
        if (hostname == null)
            throw new CommoException("Streaming interface hostname cannot be null");

        StreamingNetInterface ret = addStreamingNative(nativePtr,
                                 hostname, port,
                                 typeInts,
                                 typeInts != null ? typeInts.length : 0,
                                 clientCert,
                                 clientCert != null ? clientCert.length : 0,
                                 caCert,
                                 caCert != null ? caCert.length : 0,
                                 certPassword,
                                 caCertPassword,
                                 username, password);
        if (ret == null)
            // Should've thrown in native so should never see this
            throw new CommoException("Unknown error");
        return ret;
    }
    
    /**
     * Remove a previously added streaming interface. 
     * The iface can be removed regardless of if it is in the up or down state.
     * When this call completes, the interface will no longer be used.
     * However it is possible that previously received messages may be passed
     * to CoTMessageListeners during execution of this method.
     * After completion, the supplied StreamingNetInterface is
     * considered invalid.
     *  
     * @param iface previously added StreamingNetInterface to 
     *              remove from service
     * @throws IllegalArgumentException if the supplied
     *         StreamingNetInterface was not created via
     *         addStreamingInterface() or was already removed
     */
    public void removeStreamingInterface(StreamingNetInterface iface)
    {
        long removeMe = iface.getNativePtr();
        if (removeMe == 0)
            throw new IllegalArgumentException("Interface already removed");
        if (!removeStreamingNative(nativePtr, iface.getNativePtr()))
            throw new IllegalArgumentException("Interface invalid");
    }
    
    
    /**
     * Register an InterfaceStatusListener to receive notifications 
     * of interface status changes.
     * @param listener the InterfaceStatusListener to add
     * @throws IllegalArgumentException if the specified listener
     *         was already added
     */
    public void addInterfaceStatusListener(InterfaceStatusListener listener)
    {
        if (!addInterfaceStatusListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener already added");
    }
    
    /**
     * Remove a previously registered InterfaceStatusListener; upon completion
     * of this method, no additional status change messages will be sent to
     * the specified listener. Some state change messages may be delivered
     * to the listener during execution of this method, however.
     * 
     * @param listener the InterfaceStatusListener to remove
     * @throws IllegalArgumentException if the specified listener was not previously added
     *         using addInterfaceStatusListener
     */
    public void removeInterfaceStatusListener(InterfaceStatusListener listener)
    {
        if (!removeInterfaceStatusListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener not valid");
    }

    /**
     * Registers a ContactPresenceListener to be notified when new Contacts
     * are discovered and no longer valid Contacts are removed.
     * @param listener ContactPresenceListener to register
     * @throws IllegalArgumentException if the specified listener
     *         was already added
     */
    public void addContactPresenceListener(ContactPresenceListener listener)
    {
        if (!addContactListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener already added");
    }
    
    /**
     * Remove a previously registered ContactPresenceListener; upon completion
     * of this method, no additional contact presence change messages will be sent to
     * the specified listener. Some messages may be delivered to the
     * listener during execution of this method, however.
     * 
     * @param listener the ContactPresenceListener to remove
     * @throws IllegalArgumentException if the specified listener was not previously added
     *         using addContactPresenceListener
     */
    public void removeContactPresenceListener(ContactPresenceListener listener)
    {
        if (!removeContactListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener not valid");
    }
    

    /**
     * Adds an instance of CoTMessageListener which desires to be notified
     * when new CoT messages are received. See CoTMessageListener
     * interface.
     * 
     * @param listener the listener to add
     * @throws IllegalArgumentException if the specified listener
     *         was already added
     */
    public void addCoTMessageListener(CoTMessageListener listener) {
        if (!addCoTListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener already added");
    }

    /**
     * Removes a previously added instance of CoTMessageListener;
     * upon completion of this method, the listener will no longer
     * receive any further event updates.  The listener may
     * receive events while this method is being executed.
     * 
     * @param listener the listener to remove
     * @throws IllegalArgumentException if the specified listener
     *                 was not previously added
     */
    public void removeCoTMessageListener(CoTMessageListener listener) {
        if (!removeCoTListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener not valid");
    }
    
    
    /**
     * Adds an instance of GenericDataListener which desires to be notified
     * when new data is received on any inbound interface created
     * for generic data reception. See GenericDataListener
     * interface.
     * 
     * @param listener the listener to add
     * @throws IllegalArgumentException if the specified listener
     *         was already added
     */
    public void addGenericDataListener(GenericDataListener listener) {
        if (!addGenericDataListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener already added");
    }

    /**
     * Removes a previously added instance of GenericDataListener;
     * upon completion of this method, the listener will no longer
     * receive any further event updates.  The listener may
     * receive events while this method is being executed.
     * 
     * @param listener the listener to remove
     * @throws IllegalArgumentException if the specified listener
     *                 was not previously added
     */
    public void removeGenericDataListener(GenericDataListener listener) {
        if (!removeGenericDataListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener not valid");
    }
    
    
    /**
     * Adds an instance of CoTSendFailureListener which desires to be notified
     * if a failure to send a CoT message to a specific contact or TCP
     * endpoint is detected. See CoTSendFailureListener interface.
     * 
     * @param listener the listener to add
     * @throws IllegalArgumentException if the specified listener
     *         was already added
     */
    public void addCoTSendFailureListener(CoTSendFailureListener listener) {
        if (!addCoTSendFailureListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener already added");
    }

    /**
     * Removes a previously added instance of CoTSendFailureListener;
     * upon completion of this method, the listener will no longer
     * receive any further failure notifications.  The listener may
     * receive events while this method is being executed.
     * 
     * @param listener the listener to remove
     * @throws IllegalArgumentException if the specified listener
     *                 was not previously added
     */
    public void removeCoTSendFailureListener(CoTSendFailureListener listener) {
        if (!removeCoTSendFailureListenerNative(nativePtr, listener))
            throw new IllegalArgumentException("Listener not valid");
    }
    
    
    /**
     * Send a CoT-formatted message to the specific Contact.
     * The message is queued for transmission immediately, but the 
     * actual transmission is done asynchronously. Because of the
     * nature of CoT messaging, there may be no indication returned
     * of the success or failure of the transmission. If the means
     * by which the destination contact is reachable allows for
     * error detection, transmission errors will be posted to any
     * CoTSendFailureListeners that are registered with this Commo instance.
     * 
     * @param destination Contact to send to
     * @param cotMessage CoT message to send
     * @throws CommoContactGoneException if the specified contact is no longer valid
     * @throws CommoException if the cotMessage is not validly formatted
     */
    public void sendCoT(Contact destination, String cotMessage) throws CommoException
    {
        if (cotMessage == null)
            throw new CommoException("Cannot send null cotMessage");

        String[] gone = sendCoTNative(nativePtr, new String[] { destination.contactUID }, 1, cotMessage, CoTSendMethod.ANY.getNativeVal());
        if (gone == null)
            throw new CommoException();
        if (gone.length != 0)
            throw new CommoContactGoneException();
    }

    /**
     * Send a CoT-formatted message to the specified Contacts.
     * The message is queued for transmission immediately, but the 
     * actual transmission is done asynchronously. Because of the
     * nature of CoT messaging, there may be no indication returned
     * of the success or failure of the transmission. If the means
     * by which the destination contact(s) is/are reachable allows for
     * error detection, transmission errors will be posted to any
     * CoTSendFailureListeners that are registered with this Commo instance.
     * 
     * @param destinations Contacts to send to. This list is updated to
     *                     contain only the Contacts that are known to be
     *                     "gone" at the time of this invocation. If the 
     *                     message is queued for all Contacts, this Vector
     *                     will be empty on return
     * @param cotMessage CoT message to send
     * @throws CommoException if the cotMessage is not validly formatted
     */
    public void sendCoT(Vector<Contact> destinations, String cotMessage) throws CommoException
    {
        sendCoT(destinations, cotMessage, CoTSendMethod.ANY);
    }
    
    
    /**
     * Send a CoT-formatted message to the specified Contacts, but only
     * utilize the transmission method specified. If one of the
     * destination contacts is only reachable by a method that is not
     * specified, the message will not be sent to that contact.
     * The message is queued for transmission immediately, but the 
     * actual transmission is done asynchronously. Because of the
     * nature of CoT messaging, there may be no indication returned
     * of the success or failure of the transmission. If the means
     * by which the destination contact(s) is/are reachable allows for
     * error detection, transmission errors will be posted to any
     * CoTSendFailureListeners that are registered with this Commo instance.
     * 
     * @param destinations Contacts to send to. This list is updated to
     *                     contain only the Contacts that are known to be
     *                     "gone" or who are unknown via the 
     *                     specified send method at the time of this
     *                     invocation. If the 
     *                     message is queued for all Contacts, OR
     *                     an exception is thrown, this Vector
     *                     will be empty on return
     * @param cotMessage CoT message to send
     * @param method method by which to send the message
     *               to the destination contacts
     * @throws CommoException if the cotMessage is not validly formatted
     */
    public void sendCoT(Vector<Contact> destinations, String cotMessage,
                        CoTSendMethod method) throws CommoException
    {
        if (cotMessage == null)
            throw new CommoException("Cannot send null cot message");

        String[] uids = new String[destinations.size()];
        HashMap<String, Contact> uidToContact = new HashMap<String, Contact>();
        int i = 0;
        for (Contact c : destinations) {
            uidToContact.put(c.contactUID, c);
            uids[i++] = c.contactUID;
        }
    
        String[] gone = sendCoTNative(nativePtr, uids, uids.length,
                                      cotMessage, method.getNativeVal());

        destinations.clear();
        if (gone == null)
            throw new CommoException();

        if (gone.length != 0) {
            for (String uid : gone) {
                destinations.add(uidToContact.get(uid));
            }
        }
    }
    
    /**
     * Attempt to send a CoT-formatted message to the specified host
     * on the specified TCP port number.  
     * The message is queued for transmission; the actual transmission happens
     * asynchronously.
     * A connection is made to the host on the specified port, the message
     * is sent, and the connection is closed.
     * If the transmission fails,
     * a failure will be posted to any registered CoTSendFailureListeners.
     * Use of this method is deprecated in favor of sendCoT().
     *
     * @param hostname host or ip number in string form designating the remote
     *                 system to send to
     * @param port destination TCP port number
     * @param cotMessage CoT message to send
     * @throws CommoException if cotMessage is not valid
     */
    public void sendCoTTcpDirect(String hostname, int port, String cotMessage) throws CommoException
    {
        if (cotMessage == null)
            throw new CommoException("Cannot send null cot message");
        if (hostname == null)
            throw new CommoException("Cannot send cot message to null hostname");

        if (!sendCoTTcpDirectNative(nativePtr, hostname, port, cotMessage))
            throw new CommoException();
    }
    
    /**
     * Attempts to send a CoT-formatted message to all connected
     * streaming interfaces with a special destination tag
     * that indicates to TAK server that this message is intended
     * as a control message for the server itself.
     * The message is queued for transmission; the actual transmission
     * happens asynchronously.
     * @param streamId the streamId of the StreamingNetInterface to send to, 
     *                 or null to send to all streams.
     *                 This must correspond to a currently
     *                 valid StreamingNetInterface
     * @param cotMessage CoT message to send
     * @throws CommoException if cotMessage is not valid
     */
    public void sendCoTServerControl(String streamId,
                                     String cotMessage) throws CommoException {
        if (cotMessage == null)
            throw new CommoException("Cannot send null cot message");

        if (!sendCoTServerControlNative(nativePtr, streamId, cotMessage))
            throw new CommoException();
    }
    
    /**
     * Attempts to send a CoT-formatted message to a mission-id
     * destination on a single, or all connected, streaming interfaces.
     * The message is queued for transmission; the actual transmission happens
     * asynchronously.
     * @param streamId the streamId of the StreamingNetInterface to send to,
     *                 or null to send to all streams.
     *                 This must correspond to a currently
     *                 valid StreamingNetInterface
     * @param mission the mission identifier indicating the mission
     *                to deliver to
     * @param cotMessage CoT message to send
     * @throws CommoException if cotMessage is not valid
     */
    public void sendCoTToServerMissionDest(String streamId, String mission,
                                          String cotMessage) throws CommoException {
        if (cotMessage == null)
            throw new CommoException("Cannot send null cot message");
        if (mission == null)
            throw new CommoException("Cannot send cot message to null mission");

        if (!sendCoTToServerMissionDestNative(nativePtr, streamId, mission, cotMessage))
            throw new CommoException();
    }
    
    /**
     * Sends the provided CoT-formatted message out all broadcast interfaces
     * and streams configured for the CoTMessageType of the cotMessage.
     * The message is queued for delivery immediately, but the actual
     * transmission is done asynchronously.
     * 
     * @param cotMessage the CoT message content
     * @throws CommoException if the cotMessage is not
     *                                  validly formatted
     */
    public void broadcastCoT(String cotMessage) throws CommoException
    {
        broadcastCoT(cotMessage, CoTSendMethod.ANY);
    }

    /**
     * Sends the provided CoT-formatted message out all broadcast interfaces
     * and streams configured for the CoTMessageType of the cotMessage and
     * that match the send method specified.
     * The message is queued for delivery immediately, but the actual
     * transmission is done asynchronously.
     * 
     * @param cotMessage the CoT message content
     * @param method method by which to broadcast - other broadcast
     *               interfaces or streams not matching this method
     *               will be ignored when sending this broadcast
     * @throws CommoException if the cotMessage is not
     *                                  validly formatted
     */
    public void broadcastCoT(String cotMessage, CoTSendMethod method) throws CommoException
    {
        if (cotMessage == null)
            throw new CommoException("Cannot send null cot message");
        if (!broadcastCoTNative(nativePtr, cotMessage, method.getNativeVal()))
            throw new CommoException("Invalid cot message");
    }
    
    
    /**
     * Initiate a simple file transfer.  
     * Simple file transfers are transfers of files via traditional
     * client-server protocols (where commo is the client).
     * Transfer can be an upload from this device to the server
     * (forUpload = true) or a download from the server to this device
     * (forUpload = false).  Server file location is specified using
     * remoteURI while local file location is specified via localFile.
     * localFile must be able to be accessed (read/written) for the duration
     * of the transfer and assumes the transfer is the only thing accessing
     * the file. For downloads, localFile's parent directory must already
     * exist and be writable - the library will not create directories for you.
     * Existing files will be overwritten without warning; it is the calling
     * application's responsibility to verify localFile is the user's
     * intended location, including checking for existing files!
     * Protocols currently supported are: ftp, ftps (ssl based ftp).
     * Other protocol support may be added in the future.
     * caCert is optional - if given (non-null), it is used in ssl-based
     * protocols to verify the server's certificate is signed by
     * the CA in the given cert. It must be a valid cert in PKCS#12 format.
     * User and password are optional; if given (non-null), they will be used
     * to attempt to login on the remote server. Do NOT put the
     * username/password in the URI!
     * A non-null password must be accompanied by a non-null user.
     * Upon successful return here, the transfer is configured and placed
     * in a waiting state.  The caller my commence the transfer by
     * invoking simpleTransferStart(), supplying the returned transfer id.
     *
     * @param forUpload true to initiate upload, false for download
     * @param remoteURI URI of the resource on the server
     * @param caCert optional CA certificate to check server cert against
     * @param caCertPassword password for the CA cert data
     * @param user optional username to log into remote server with
     * @param password optional password to log into the remote server with
     * @param localFile local file to upload/local file location to write
     *        remote file to
     * @throws CommoException if an error occurs, bad CA cert format,
     *        incorrect caCertPassword, the URI contains an unsupported
     *        protocol, or if this Commo instance has not yet had
     *        SimpleFileIO enabled by calling enableSimpleFileIO()
     */
    public int simpleFileTransferInit(boolean forUpload,
                                  URI remoteURI,
                                  byte[] caCert,
                                  String caCertPassword,
                                  String user,
                                  String password,
                                  File localFile) throws CommoException
    {
        if (password != null && user == null)
            throw new CommoException("Cannot give a password without a username");
        return simpleFileTransferInitNative(nativePtr, forUpload,
                                        remoteURI.toString(),
                                        caCert,
                                        caCert != null ? caCert.length : 0,
                                        caCertPassword,
                                        user,
                                        password,
                                        localFile.getAbsolutePath());
    }


    /**
     * Commences a simple file transfer previously initialized 
     * via a call to simpleFileTransferInit().
     * @param xferId id of the previously initialized transfer
     * @throws CommoException if this if this Commo instance has not yet had
     *        SimpleFileIO enabled by calling enableSimpleFileIO or
     *        the supplied xferId does not identify an initialized (but not
     *        already started) transfer
     */
    public void simpleFileTransferStart(int xferId) throws CommoException
    {
        if (!simpleFileTransferStartNative(nativePtr, xferId))
            throw new CommoException("Cannot start transfer - given id is not valid or transfers not previously enabled");
    }
    

    /**
     * Create a CloudClient to interact with a remote cloud server.
     * The client will remain valid until destroyed using destroyCloudClient()
     * or this Commo instance is shutdown. NOTE: it is invalid to let a
     * CloudClient reference be lost before being destroyed via one of the two
     * above methods!
     * The basePath is the path to cloud application on the server.
     * If your cloud service is at the root of the server on the remote host
     * simply pass the empty string or "/" for the basePath.
     * The client uses the provided parameters to interact with the server
     * and the provided callback interface to report progress on operations
     * initiated using the Client's methods.
     * Any number of clients may be active at a given time.
     * caCerts is optional - if given (non-null), it is used in ssl-based
     * protocols to verify the server's certificate. It must be a valid
     * set of certs in PKCS#12 format.
     * If not provided (null) and an SSL protocol is in use, all remote
     * certificates will be accepted.
     * The user and password are optional; if given (non-null),
     * they will be used to attempt to login on the remote server.
     * A non-null password must be accompanied by a non-null user.
     * Upon successful return here, the transfer is configured and placed
     * in a waiting state.  The caller my commence the transfer by
     * invoking simpleTransferStart(), supplying the returned transfer id.
     *
     * @param io CloudIO callback interface instance to report progress
     *           of all client operations to
     * @param proto Protocol used to interact with server
     * @param host hostname or IP of remote server
     * @param port port of remote server
     * @param basePath base path to remote cloud server on the given host.
     *                 MUST be properly URL encoded. Cannot be null!
     * @param user optional username to log into remote server with
     * @param password optional password to log into the remote server with
     * @param caCerts optional CA certificates to check server cert against
     * @param caCertsPassword password for the CA cert data
     * @throws CommoException if an error occurs, including bad CA cert format,
     *        incorrect caCertPassword
     */
    public CloudClient createCloudClient(CloudIO io,
                                  CloudIOProtocol proto,
                                  String host,
                                  int port,
                                  String basePath,
                                  String user,
                                  String password,
                                  byte[] caCerts,
                                  String caCertsPassword) throws CommoException
    {
        if (password != null && user == null)
            throw new CommoException("Cannot give a password without a username");
        if (basePath == null)
            throw new CommoException("Cannot give null basePath");
        if (io == null)
            throw new CommoException("Cannot give null callback");
        if (host == null)
            throw new CommoException("Cannot give null host");
        
        return createCloudClientNative(nativePtr, io, proto.getNativeVal(),
                host, port, basePath, user, password, caCerts,
                caCerts != null ? caCerts.length : 0,
                caCertsPassword);
    }
    
    /**
     * Destroy a CloudClient created by an earlier call to createCloudClient().
     * This will terminate all client operations, including ongoing transfers,
     * before completion.  Existing operations may or may not receive callbacks
     * during the destruction.
     * 
     * @param client the client to destroy
     * @throws CommoException if an error occurs
     */
    public void destroyCloudClient(CloudClient client) throws CommoException
    {
        long p = client.getNativePtr();
        long io = client.getNativeIOPtr();
        if (p == 0)
            throw new CommoException("client already destroyed");
        if (!destroyCloudClientNative(nativePtr, p, io))
            throw new CommoException();
    }

    
    /**
     * Initialize a Mission Package transfer of a file to the 
     * specified contact(s).  The list of contact(s) is updated
     * to indicate which contact(s) could not be sent to (considered "gone").
     * This call sets up the transfer and places
     * it into a pending state, returning an identifier that can be used
     * to correlate transfer status information back to this
     * request (it uniquely identifies this transfer).
     * To commence the actual transfer, call startMissionPackageSend() with
     * the returned id. It is guaranteed that no status info
     * will be fed back for the transfer until it has been started.
     * Once the send has been started, transmission will be initiated as
     * soon as possible, but the actual processing by and transfer to
     * the receiver will take place asynchronously in the background.
     * The file being transferred must remain available at the
     * specified location until the asynchronous transaction is
     * entirely complete.
     * 
     * @param destinations Contacts to send to. This list is updated to
     *                     contain only the Contacts that are known to be
     *                     "gone" at the time of this invocation. If the 
     *                     message is queued for all Contacts, this Vector
     *                     will be empty on return
     * @param file the Mission Package file to send
     * @param xferFileName the name by which the receiving system should refer
     *                     to the file (no path, file name only). This name
     *                     does NOT have to be the same as the local file name.
     * @param xferName the name for the transfer, used to generically refer
     *                 to the transfer both locally and remotely
     * @throws CommoException if the file is not readable, 
     *                        setupMissionPackageIO() was not yet invoked
     *                        successfully,
     *                        all destinations are unreachable,
     *                        or some other error occurs
     * @return integer identifier that uniquely identifies this transaction
     */
    public int sendMissionPackageInit(List<Contact> destinations, 
                                  File file,
                                  String xferFileName,
                                  String xferName) throws CommoException
    {
        if (xferFileName == null || xferName == null)
            throw new CommoException("Cannot send MP with null transfer parameters");

        String[] uids = new String[destinations.size()];
        HashMap<String, Contact> uidToContact = new HashMap<String, Contact>();
        int i = 0;
        for (Contact c : destinations) {
            uidToContact.put(c.contactUID, c);
            uids[i++] = c.contactUID;
        }
    
        MPNativeResult ret = sendMPInitNative(nativePtr, uids,
                                          uids.length,
                                          file.getAbsolutePath(),
                                          xferFileName, xferName);
        if (ret == null || ret.error != null)
            throw new CommoException(ret != null ? ret.error : null);

        destinations.clear();
        if (ret.goneUIDs.length != 0) {
            for (String uid : ret.goneUIDs) {
                destinations.add(uidToContact.get(uid));
            }
        }
        return ret.id;
    }

    /**
     * Initialize a Mission Package transfer of a file to the 
     * specified contact. 
     * This call sets up the transfer and places
     * it into a pending state, returning an identifier that can be used
     * to correlate transfer status information back to this
     * request (it uniquely identifies this transfer).
     * To commence the actual transfer, call startMissionPackageSend() with
     * the returned id. It is guaranteed that no status info
     * will be fed back for the transfer until it has been started.
     * Once the send has been started, transmission will be initiated as
     * soon as possible, but the actual processing by and transfer to
     * the receiver will take place asynchronously in the background.
     * The file being transferred must remain available at the
     * specified location until the asynchronous transaction is
     * entirely complete.
     * 
     * @param destination Contact to send to
     * @param file the Mission Package file to send
     * @param xferFileName the name by which the receiving system should refer
     *                     to the file (no path, file name only). This name
     *                     does NOT have to be the same as the local file name.
     * @param xferName the name for the transfer, used to generically refer
     *                 to the transfer both locally and remotely
     * @throws CommoException if the file is not readable, 
     *                        the destination is unreachable,
     *                        setupMissionPackageIO() has not yet successfully
     *                        been invoked,
     *                        or some other error occurs
     * @return integer identifier that uniquely identifies this transaction
     */
    public int sendMissionPackageInit(Contact destination, 
                                  File file,
                                  String xferFileName,
                                  String xferName) throws CommoException
    {
        List<Contact> cl = new Vector<Contact>();
        cl.add(destination);
        return sendMissionPackageInit(cl, file, xferFileName, xferName);
    }
	
    /**
     * Registers a native FileIOProvider, causing the provided FileIOProvider
     * to be used for all future IO transactions until such time where it is
     * unregistered via unregisterFileIOProvider().  The default FileIOProvider
     * if none is registered does simple file-based IO using operating system
     * provided calls.
     *
     * @param provider The FileIOProvider to register
     */
    public synchronized void registerFileIOProvider(FileIOProvider provider){
        if(provider == null)
            throw new NullPointerException("Provider may not be null");
        // check if already registered
        if(ioProviders.containsKey(provider))
            return;
        final long providerPtr = registerFileIOProviderNative(nativePtr, provider);
        if(providerPtr == 0L)
            throw new RuntimeException("Failed to register provider");
        ioProviders.put(provider, Long.valueOf(providerPtr));
    }
	
    /**
     * Unregisters a native FileIOProvider and reverts to the default provider.
     * The default FileIOProvider does simple file-based IO using operating
     * system provided calls.
     *
     * @param provider The FileIOProvider to register
     */
    public synchronized void unregisterFileIOProvider(FileIOProvider provider){
        final Long providerPtr = ioProviders.remove(provider);
        // not registered
        if(providerPtr == 0L)
            return;
        deregisterFileIOProviderNative(nativePtr, providerPtr.longValue());
    }


    /**
     * Send a Mission Package file to the specified TAK server.  
     * The server is first queried to see if the file exists on server already.
     * If not, the file is transmitted to the server.
     * The query and the transmission (if needed) are
     * initiated as soon as possible, but this takes place asynchronously 
     * in the background.  The returned ID can be used to correlate 
     * transfer status information back to this request (it
     * uniquely identifies this transfer).
     * The file being transfered must remain available at the specified location
     * until the asynchronous transaction is entirely complete.
     * 
     * @param streamId the streamId of the StreamingNetInterface to send to.
     *                 This must correspond to a currently
     *                 valid StreamingNetInterface
     * @param file the Mission Package file to send
     * @param xferFileName the name by which the receiving system should refer
     *                     to the file (no path, file name only). This name
     *                     does NOT have to be the same as the local file name.
     * @throws CommoContactGoneException if the specified streamId does not
     *                                   correspond to a currently valid
     *                                   StreamingNetInterface
     * @throws CommoException if the file is not readable, 
     *                     setupMissionPackageIO has not yet been successfully
     *                     invoked, or some other error occurs
     * @return integer identifier that uniquely identifies this transaction
     */
    public int sendMissionPackageInit(String streamId, File file,
                                  String xferFileName) throws CommoException
    {
        if (xferFileName == null || streamId == null)
            throw new CommoException("Cannot send MP with null transfer parameters");
        MPNativeResult ret = sendMPInitToServerNative(nativePtr,
                                      streamId,
                                      file.getAbsolutePath(),
                                      xferFileName);
        if (ret == null || ret.error != null)
            throw new CommoException(ret == null ? null : ret.error);
        return ret.id;
    }
    

    /**
     * Begins the transfer process for a pending mission package send
     * initialized by a prior call to sendMissionPackageInit().
     * The transmission is initiated as soon as possible, but the
     * actual processing by and transfer to the receiver will take
     * place asynchronously in the background as described in
     * sendMissionPackageInit().
     *
     * @param id the identifier of the mission package send to start
     * @throws CommoException if the id is not a valid id for a mission package
     *                        send
     */
    public void startMissionPackageSend(int id) throws CommoException
    {
        if (!startMPSendNative(nativePtr, id))
            throw new CommoException("Invalid mission package transfer identifier");
    }


    /**
     * Returns the full list of Contacts currently known to the system.
     * Contacts generally remain valid targets for the entire life
     * of this Commo instance. However, Contacts known via streaming
     * connections may become invalid if the streaming connection is
     * closed or lost.
     * This always returns a valid array; if no contacts are known,
     * a zero-length array is returned.
     * 
     * @return an array of all known Contacts
     */
    public Contact[] getContacts()
    {
        String[] uids = getContactsNative(nativePtr);
        Contact[] c = new Contact[uids.length];
        int i = 0;
        for (String s : uids) {
            c[i++] = new Contact(s);
        }
        return c;
    }
    
    /**
     * Configure and create a "known endpoint" contact. 
     * This is a non-discoverable contact for whom we already know
     * an endpoint and want to be able to communicate with them, possibly
     * unidirectionally.  The destination address must be specified in
     * "dotted decimal" IP address form; hostnames are now accepted.
     * If the address is a multicast address, messages sent to the created
     * Contact will be multicasted to all configured broadcast interfaces,
     * regardless of message type.
     * The port specifies a UDP port endpoint on the specified remote host.
     * The UID must be unique from existing known UIDs and should be chosen
     * to be unique from other potentially self-discovered contacts;
     * any message-based discovery for a contact with the same UID as the one
     * specified will be ignored - the endpoint remains fixed to the specified
     * one.
     * Specifying a UID of an already configured, known endpoint contact
     * allows it to be reconfigured to a new endpoint or callsign; specify
     * null for callsign and ipAddr (and any port number) to remove this
     * "contact". Specifying null for either callsign or ipAddr (but not both)
     * results in an exception.  Specifying a UID for an already known (via
     * discovery) contact, a null UID, null ipAddr for a contact not previously
     * configured via this call, or an unparsable ipAddr will also yield an
     * exception.
     * Upon success configuring the contact, the newly added or reconfigured
     * Contact is returned.
     * @param uid uid for the contact
     * @param callsign callsign for the contact
     * @param ipAddr ip address in "dotted decimal" form
     * @param destPort the port to send to
     */
    public Contact configKnownEndpointContact(String uid, String callsign,
                                              String ipAddr, int destPort)
                                               throws CommoException
    {
        if (uid == null)
            throw new CommoException("UID cannot be null");
        boolean result = configKnownEndpointContactNative(nativePtr, 
                                                          uid, callsign,
                                                          ipAddr, destPort);
        if (!result)
            throw new CommoException("Failed to configure known EP contact");
        return new Contact(uid);
    }
    
    
    /**
     * Generate a private key and return as a string in PEM format,
     * encoded with the given password.
     * @param password Password to use when encoding the private key
     * @param keyLength length of key to generate, in bits
     * @return String representation of the PEM formatted private key, 
     *         or null on error. 
     */
    public String generateKeyCryptoString(String password, int keyLength)
    {
        if (password == null)
            return null;
        return generateKeyNative(nativePtr, password, keyLength);
    }
    
    /**
     * Generate a CSR and return as a string in PEM format.
     * CSR will be signed by the given key, which uses the given password.
     * CSR will contain the entries specified.
     * @param csrDnEntries Map of key/value pairs of strings that will
     * be included in the CS to form the DN. The keys must be valid 
     * for CSR DN entries.
     * @param pkeyPem Private key to use for the CSR, in PEM format
     * @param password Password to used to decode the private key.
     * @return String representation of the PEM formatted CSR, or null
     * on error. 
     */
    public String generateCSRCryptoString(Map<String, String> csrDnEntries, 
                              String pkeyPem, String password)
    {
        if (csrDnEntries == null || pkeyPem == null || password == null)
            return null;
        
        int n = csrDnEntries.size();
        String[] keys = new String[n];
        String[] values = new String[n];
        int i = 0;
        for (Map.Entry<String, String> e : csrDnEntries.entrySet()) {
            keys[i] = e.getKey();
            values[i] = e.getValue();
            i++;
        }
        
        return generateCSRNative(nativePtr, keys, values, n,
                                      pkeyPem, password);
    }
    
    
    /**
     * Generate a keystore from the given constituent elements.
     * The keystore is returned as a pkcs#12 keystore encoded as a string
     * base64 format.
     * @param certPem The certificate as a string in PEM format
     * @param caPem An ordered list of ca certificates, each
     *              as a string in PEM format. This allows intermediate CAs
     *              up to a rootCA.
     * @param pkeyPem Private key as a string in PEM format
     * @param password Password used to decode the private key pkeyPem
     * @param friendlyName Friendly name to use in the keystore
     * @return String representation of the pkcs#12 keystore,
     *         encoded using base64 or null on error.
     */
    public String generateKeystoreCryptoString(String certPem, 
                              List<String> caPem, String pkeyPem,
                              String password,
                              String friendlyName)
    {
        if (certPem == null || caPem == null || pkeyPem == null || 
                password == null || friendlyName == null)
            return null;
        String[] caStrings = caPem.toArray(new String[caPem.size()]);
        return generateKeystoreNative(nativePtr, certPem, caStrings,
                                      caStrings.length,
                                      pkeyPem, password, friendlyName);
    }
    
    /**
     * Generate a self-signed certificate with private key.
     * Values in the cert are filled with nonsense data.
     * Certificates from this cannot be used to interoperate with commerical
     * tools due to lack of signature by trusted CAs.
     * The returned data is in pkcs12 format protected by the supplied
     * password, which cannot be null.
     */
    public byte[] generateSelfSignedCert(String certPass) throws CommoException
    {
        if (certPass == null)
            throw new CommoException("Password cannot be null");
        byte[] ret = generateSelfSignedCertNative(nativePtr, certPass);
        if (ret == null)
            throw new CommoException();
        return ret;
    }
    

    /**
     * Convert cot in XML form to a specific version of the TAK protocol.
     * The tak protocol data is prefaced with the tak protocol
     * broadcast header.
     * NOTE: this method does not accept version = 0 (legacy XML) since
     * that would be a silly conversion!
     * @param cotXml xml to convert
     * @param desiredVersion TAK protocol version to convert to
     * @return a new byte array containing the converted result
     * @throw CommoException if the source XML is not valid CoT,
     *                       the desired version is invalid,
     *                       or the conversion fails
     */    
    public byte[] cotXmlToTakproto(String cotXml, int desiredVersion)
                                   throws CommoException
    {
        byte[] ret = cotXmlToTakprotoNative(nativePtr, cotXml, desiredVersion);
        if (ret == null)
            throw new CommoException();
        return ret;
    }


    /**
     * Convert TAK protocol data (with TAK protocol header) to the
     * XML equivalent.
     * @param protodata the TAK protocol data. It must begin with the
     *                  tak protocol broadcast header
     * @return the CoT event portion of the TAK protocol message formatted
     *         as an XML string.
     * @throw CommoException the protodata is not valid TAK protocol data,
     *                       it's for an unsupported version,
     *                       the proto data does not contain a CoT Event,
     *                       or if the conversion fails for any other reason 
     */
    public String takprotoToCotXml(byte[] protodata) throws CommoException
    {
        String s = takprotoToCotXmlNative(nativePtr, protodata,
                                          protodata.length);
        if (s == null)
            throw new CommoException();
        return s;
    }
   


    static class MPNativeResult {
        public final String error;
        public final int id;
        public final String[] goneUIDs;
        MPNativeResult(String error, int id, String[] goneUIDs) {
            this.error = error;
            this.id = id;
            this.goneUIDs = goneUIDs;
        }
    }



    static native long commoCreateNative(CommoLogger logger, String ourUID,
                                  String ourCallsign);
    static native void commoDestroyNative(long nativePtr);
    static native boolean setupMissionPackageIONative(long nativePtr,
                                   MissionPackageIO io);
    static native boolean enableSimpleFileIONative(long nativePtr,
                                   SimpleFileIO io);
    static native void setCallsignNative(long nativePtr, String callsign);
    static native void setMagtabWorkaroundEnabledNative(long nativePtr, boolean en);
    static native void setPreferStreamEndpointNative(long nativePtr, boolean prefStream);
    static native void setAdvertiseEndpointAsUdpNative(long nativePtr, boolean en);
    static native boolean setCryptoKeysNative(long nativePtr, byte[] auth, byte[] crypt);
    static native void setEnableAddressReuseNative(long nativePtr, boolean en);
    static native void setMulticastLoopbackEnabledNative(long nativePtr, boolean en);
    static native void setTTLNative(long nativePtr, int ttl);
    static native void setUdpNoDataTimeoutNative(long nativePtr, int seconds);
    static native void setTcpConnTimeoutNative(long nativePtr, int sec);
    static native boolean setMPLocalPortNative(long nativePtr,
                                   int localWebPort);
    static native void setMPLocalHttpsParamsNative(long nativePtr,
                                   int localWebPort,
                                   byte[] cert, int certLen,
                                   String certPass) throws CommoException;
    static native void setMPViaServerEnabledNative(long nativePtr, 
                                   boolean enabled);
    static native boolean setMissionPackageHttpPortNative(long nativePtr,
                                   int serverPort);
    static native boolean setMissionPackageHttpsPortNative(long nativePtr,
                                   int serverPort);
    static native boolean setMissionPackageNumTriesNative(long nativePtr,
                                   int nTries);
    static native boolean setMissionPackageConnTimeoutNative(long nativePtr,
                                   int seconds);
    static native boolean setMissionPackageTransferTimeoutNative(long nativePtr,
                                   int seconds);
    static native void setStreamMonitorEnabledNative(long nativePtr, boolean en);
    static native int getBroadcastProtoNative(long nativePtr);
    static native PhysicalNetInterface addBroadcastNative(long nativePtr,
                                   byte[] hwAddress,
                                   int hwAddressLen,
                                   int[] types, int typesLen,
                                   String mcastAddr,
                                   int destPort);
    static native PhysicalNetInterface addUniBroadcastNative(long nativePtr,
                                   int[] types, int typesLen,
                                   String mcastAddr,
                                   int destPort);
    static native boolean removeBroadcastNative(long nativePtr,
                                   long ifaceNativePtr);
    static native PhysicalNetInterface
                  addInboundNative(long nativePtr, byte[] hwAddress,
                                   int hwAddressLen,
                                   int port, String[] mcastAddrs,
                                   int mcastAddrsLen,
                                   boolean forGenericData);
    static native boolean removeInboundNative(long nativePtr, long ifaceNativePtr);
    static native TcpInboundNetInterface
                  addTcpInboundNative(long nativePtr, int port);
    static native boolean removeTcpInboundNative(long nativePtr,
                                                 long ifaceNativePtr);
    static native StreamingNetInterface addStreamingNative(long nativePtr,
                                   String hostname,
                                   int port,
                                   int[] types, int typesLen,
                                   byte[] clientCert, int clientCertLen,
                                   byte[] caCert, int caCertLen,
                                   String certPassword,
                                   String caCertPassword,
                                   String username, String password)
                                   throws CommoException;
    static native boolean removeStreamingNative(long nativePtr,
                                             long ifaceNativePtr);
    static native String getStreamingInterfaceIdNative(long nativePtr,
                                                       long ifaceNativePtr);
    static native boolean addInterfaceStatusListenerNative(long nativePtr,
                                             InterfaceStatusListener listener);
    static native boolean removeInterfaceStatusListenerNative(long nativePtr,
                                             InterfaceStatusListener listener);
    static native boolean addContactListenerNative(long nativePtr,
                                             ContactPresenceListener listener);
    static native boolean removeContactListenerNative(long nativePtr,
                                             ContactPresenceListener listener);
    static native boolean addCoTListenerNative(long nativePtr,
                                             CoTMessageListener listener);
    static native boolean removeCoTListenerNative(long nativePtr,
                                             CoTMessageListener listener);
    static native boolean addGenericDataListenerNative(long nativePtr,
                                             GenericDataListener listener);
    static native boolean removeGenericDataListenerNative(long nativePtr,
                                             GenericDataListener listener);
    static native boolean addCoTSendFailureListenerNative(long nativePtr,
                                             CoTSendFailureListener listener);
    static native boolean removeCoTSendFailureListenerNative(long nativePtr,
                                             CoTSendFailureListener listener);
    static native String[] sendCoTNative(long nativePtr,
                                         String[] destinations,
                                         int nDestinations,
                                         String cot,
                                         int method);
    static native boolean sendCoTTcpDirectNative(long nativePtr,
                                         String host,
                                         int port,
                                         String cot);
    static native boolean sendCoTServerControlNative(long nativePtr, 
                                         String streamId,
                                         String cotMessage);
    static native boolean sendCoTToServerMissionDestNative(long nativePtr,
                                         String streamId,
                                         String mission,
                                         String cotMessage);
    static native boolean broadcastCoTNative(long nativePtr, String cot,
                                             int method);
    static native int simpleFileTransferInitNative(long nativePtr,
                                         boolean forUpload,
                                         String remoteURI,
                                         byte[] caCert,
                                         int caCertLen,
                                         String caCertPassword,
                                         String user,
                                         String password,
                                         String localFile) throws CommoException;
    static native boolean simpleFileTransferStartNative(long nativePtr,
                                         int id) throws CommoException;
    static native CloudClient createCloudClientNative(long nativePtr,
                                         CloudIO io,
                                         int proto,
                                         String host, 
                                         int port, 
                                         String basePath, 
                                         String user, 
                                         String password,
                                         byte[] caCerts,
                                         int caCertsLen,
                                         String caCertsPassword) 
                                                 throws CommoException;
    static native boolean destroyCloudClientNative(long nativePtr,
            long clientPtr, long ioPtr);
    static native MPNativeResult sendMPInitNative(long nativePtr,
                                              String[] destinations,
                                              int nDestinations,
                                              String absFilePath,
                                              String xferFileName,
                                              String xferName);
    static native MPNativeResult sendMPInitToServerNative(long nativePtr,
                                              String streamId,
                                              String absFilePath,
                                              String xferFileName);
    static native boolean startMPSendNative(long nativePtr, int id);
    static native String[] getContactsNative(long nativePtr);
    static native boolean configKnownEndpointContactNative(long nativePtr,
                                              String uid,
                                              String callsign,
                                              String ipAddr,
                                              int destPort);
    static native String generateKeyNative(long nativePtr, String password,
                                           int keyLength);
    static native String generateCSRNative(long nativePtr, String[] entryKeys,
                                              String[] entryValues,
                                              int nEntries,
                                              String pkeyPem,
                                              String password);
    static native String generateKeystoreNative(long nativePtr, 
                                              String certPem, 
                                              String[] caPem,
                                              int nCa,
                                              String pkeyPem,
                                              String password,
                                              String friendlyName);
    static native byte[] generateSelfSignedCertNative(long nativePtr,
                                              String certPass);
    static native byte[] cotXmlToTakprotoNative(long nativePtr, String cotXml,
                                              int desiredVersion);
    static native String takprotoToCotXmlNative(long nativePtr, 
                                              byte[] protodata,
                                              int protodataLength);


    static native boolean initNativeLibrariesNative();

    static synchronized native long registerFileIOProviderNative(long nativePtr, FileIOProvider provider);
	
    static synchronized native void deregisterFileIOProviderNative(long nativePtr, long providerPtr);
}
