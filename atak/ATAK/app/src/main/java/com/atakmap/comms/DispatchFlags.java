
package com.atakmap.comms;

public class DispatchFlags {

    /**
     * Specifies the CoT event should be dispatched to all internal clients of the CoT service.
     */
    public static final int DISPATCH_INTERNAL = 1;

    /**
     * Specifies the CoT event should be dispatched to all persistent external outputs of the CoT
     * service.
     */
    public static final int DISPATCH_EXTERNAL = 1 << 1;

    /**
     * Specifies the CoT event should be dispatched to all persistent external outputs of the CoT
     * service.
     */
    public static final int DISPATCH_UNRELIABLE = 1 << 2;

    /**
     * Specifies the CoT event should be dispatched to all persistent external outputs of the CoT
     * service.
     */
    public static final int DISPATCH_RELIABLE = 1 << 3;

    /**
     * Indicates the CoT will be 'routed' to the outputs (systems pushing cot to).
     */
    public static final int EXTERNAL = DISPATCH_EXTERNAL;

    /**
     * Indicates the CoT will be routed to all internal consumers (other listening Apps).
     */
    public static final int INTERNAL = DISPATCH_INTERNAL;

    /**
     * Indicates the CoT will be routed to all internal consumers (other listening Apps).
     */
    public static final int UNRELIABLE = DISPATCH_UNRELIABLE;

    /**
     * Indicates the CoT will be routed to all internal consumers (other listening Apps).
     */
    public static final int RELIABLE = DISPATCH_RELIABLE;

}
