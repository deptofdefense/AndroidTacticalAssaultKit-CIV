
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.log.Log;

/**
 * Parcelable for a request which can be retry upon failure
 * 
 * 
 */
public class RetryRequest implements Parcelable {

    private static final String TAG = "RetryRequest";

    /**
     * Note, if other operations end up needing to be delayed, lets make this more generic and build
     * it into the HTTP Operation base classes. E.g. could implement a "FixedDelay" that sleeps for
     * a set amount of time
     */
    public interface OperationDelay {

        /**
         * Delay this operation (put this thread to sleep) based on the number of times this
         * operation has previously failed
         */
        void delay();
    }

    /**
     * How many times has this request been attempted
     */
    private int mRetryCount;

    private final int mNotificationId;

    /**
     * ctor
     * 
     * @param retryCount
     * @param notificationId
     */
    public RetryRequest(int retryCount, int notificationId) {
        mRetryCount = retryCount;
        mNotificationId = notificationId;
    }

    /**
     * Based on the retry count, determin the delay used in between retries
     * @return the operational delay to be used between retries.
     */
    public OperationDelay getDelay() {
        return new BackoffDelay(mRetryCount);
    }

    /**
     * Returns true if the retry request is valid
     * @return true if the retry count is greater than or equal to 0.
     */
    public boolean isValid() {
        return mRetryCount >= 0;
    }

    public int getRetryCount() {
        return mRetryCount;
    }

    public void setRetryCount(int count) {
        mRetryCount = count;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    @Override
    public String toString() {
        return "" + mRetryCount;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeInt(mRetryCount);
            dest.writeInt(mNotificationId);
        }
    }

    public static final Parcelable.Creator<RetryRequest> CREATOR = new Parcelable.Creator<RetryRequest>() {
        @Override
        public RetryRequest createFromParcel(Parcel in) {
            return new RetryRequest(in);
        }

        @Override
        public RetryRequest[] newArray(int size) {
            return new RetryRequest[size];
        }
    };

    protected RetryRequest(Parcel in) {
        mRetryCount = in.readInt();
        mNotificationId = in.readInt();
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Operation delay that backs off more as number of attempts increases
     * 
     * 
     */
    static class BackoffDelay implements OperationDelay {

        private final int _numberAttempts;

        public BackoffDelay(int numberAttempts) {
            _numberAttempts = numberAttempts;
        }

        /**
         * Delay this operation (put this thread to sleep) based on the number of times this
         * operation has previously failed
         */
        @Override
        public void delay() {
            if (_numberAttempts <= 1)
                return;

            long delaySeconds = 0;
            if (_numberAttempts == 2)
                delaySeconds = 1;
            else if (_numberAttempts == 3)
                delaySeconds = 3;
            else if (_numberAttempts == 4)
                delaySeconds = 5;
            else if (_numberAttempts == 5)
                delaySeconds = 10;
            else if (_numberAttempts == 6)
                delaySeconds = 20;
            else if (_numberAttempts < 10)
                delaySeconds = 30;
            else if (_numberAttempts < 15)
                delaySeconds = 45;
            else if (_numberAttempts < 20)
                delaySeconds = 60;
            else
                delaySeconds = 90;

            try {
                Log.d(TAG, "Request being delayed " + delaySeconds
                        + " seconds...");
                Thread.sleep(delaySeconds * 1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Delay interrupted", e);
            }
        }
    }
}
