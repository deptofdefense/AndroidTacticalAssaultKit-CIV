package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

public interface TimeSpanControl extends MapControl {

    public final static int TIMESPAN_TYPE_INTERVAL = 0x01;
    public final static int TIMESPAN_TYPE_MOMENT = 0x02;
    
    /**
     * Returns the bitwise-OR of the supported timespan types. The underlying
     * data source will support one or both of {@link #TIMESPAN_TYPE_INTERVAL}
     * and {@link #TIMESPAN_TYPE_MOMENT}.
     * 
     * @return  The bitwise-OR of the supported timespan types.
     * 
     * @see #TIMESPAN_TYPE_INTERVAL
     * @see #TIMESPAN_TYPE_MOMENT
     */
    public int getSupportedTimespanTypes();
    
    /**
     * Returns the minimum timestamp available from the underlying data source.
     * 
     * @return  The minimum timestamp, in epoch milliseconds.
     */
    public long getMinimumTime();
    
    /**
     * Returns the maximum timestamp available from the underlying data source.
     * 
     * @return  The maximum timestamp, in epoch milliseconds.
     */
    public long getMaximumTime();
    
    /**
     * Returns the currently set timespan. Only data with a timestamp that meets
     * the criteria of the timespan will be displayed.
     * 
     * @return  The currently set timespan.
     */
    public TimeSpan getCurrentTimeSpan();
        
    /**
     * Sets the timespan of data to be displayed. Only data that has a timestamp
     * between the specified <code>startTime</code> and <code>endTime</code>
     * will be displayed. If <code>startTime</code> and <code>endTime</code> are
     * equal, only data at that particular instance will be displayed.  
     * 
     * @param startTime The start timestamp, in epoch milliseconds
     * @param endTime   The end timestamp, in epoch milliseconds
     */
    public void setTimeSpan(TimeSpan timespan);
    
    public final static class TimeSpan {
        
        private final long timestamp;
        private final long duration;
        
        private TimeSpan(long timestamp, long duration) {
            this.timestamp = timestamp;
            this.duration = duration;
        }
        
        public long getTimeStamp() {
            return this.timestamp;
        }
        
        public long getDuration() {
            return this.duration;
        }
        public static TimeSpan createMoment(long timestamp) {
            return new TimeSpan(timestamp, 0L);
        }
        
        public static TimeSpan createInterval(long start, long end) {
            if(end < start)
                throw new IllegalArgumentException();
            return new TimeSpan(start, (end-start));
        }
    }
}
