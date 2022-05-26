package com.atakmap.nio;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public final class Buffers {
    
    private Buffers() {}
    
    public static Buffer skip(Buffer arg, int n) {
        arg.position(arg.position()+n);
        return arg;
    }
    
    /**
     * Shifts the content at the current <I>position</I> through the <I>limit<I>
     * to position <code>0</code> in the buffer. When this method returns,
     * the <I>position</I> will be set to <code>0</code> and the <I>limit</I>
     * will be set to <I>remaining</I>
     * 
     * @param arg
     * @return
     */
    public static ByteBuffer shift(ByteBuffer arg) {
        final int pos = arg.position();
        final int rem = arg.remaining();

        if(arg.hasArray()) {
            // XXX - not sure whether or not we may be better off with an
            //       iterative element reassignment due to a possible underlying
            //       array allocation/GC
            System.arraycopy(arg.array(), 0, arg.array(), pos, rem);
        } else {
            for(int i = 0; i < rem; i++)
                arg.put(i, arg.get(pos+i));
        }
        
        arg.position(0);
        arg.limit(rem);

        return arg;        
    }
}
