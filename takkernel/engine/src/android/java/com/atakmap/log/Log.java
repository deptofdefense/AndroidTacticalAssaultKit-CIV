
package com.atakmap.coremap.log;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Augmentation of the API for sending log output.
 *
 * <p>Generally, use the android.util.Log.v() android.util.Log.d() android.util.Log.i() android.util.Log.w() and android.util.Log.e()
 * methods.
 *
 * <p>The order in terms of verbosity, from least to most is
 * ERROR, WARN, INFO, DEBUG, VERBOSE.  Verbose should never be compiled
 * into an application except during development.  Debug logs are compiled
 * in but stripped at runtime.  Error, warning and info logs are always kept.
 *
 * <p><b>Tip:</b> A good convention is to declare a <code>TAG</code> constant
 * in your class:
 *
 * <pre>private static final String TAG = "MyActivity";</pre>
 *
 * and use that in subsequent calls to the log methods.
 * </p>
 *
 * <p><b>Tip:</b> Don't forget that when you make a call like
 * <pre>android.util.Log.v(TAG, "index=" + i);</pre>
 * that when you're building the string to pass into android.util.Log.d, the compiler uses a
 * StringBuilder and at least three allocations occur: the StringBuilder
 * itself, the buffer, and the String object.  Realistically, there is also
 * another buffer allocation and copy, and even more pressure on the gc.
 * That means that if your log message is filtered out, you might be doing
 * significant work and incurring significant overhead.
 */
public final class Log {

    private static final Pattern newline = Pattern
            .compile(
                    "%0A|%0a|\\n\\r|\\r\\n|[\\n\\x0B\\x0C\\r\\u0085\\u2028\\u2029]");

    public interface LogListener {
        /**
         * Log listener which can be used for metrics.
         * @param tag the tag described by the log
         * @param msg the message
         * @param tr the error
         */
        void write(String tag, String msg, Throwable tr);
    }

    private static ConcurrentLinkedQueue<LogListener> loggers = new ConcurrentLinkedQueue<>();

    /**
     * Priority constant for the println method; use Log.v.
     */
    public static final int VERBOSE = android.util.Log.VERBOSE;

    /**
     * Priority constant for the println method; use Log.d.
     */
    public static final int DEBUG = android.util.Log.DEBUG;

    /**
     * Priority constant for the println method; use Log.i.
     */
    public static final int INFO = android.util.Log.INFO;

    /**
     * Priority constant for the println method; use Log.w.
     */
    public static final int WARN = android.util.Log.WARN;

    /**
     * Priority constant for the println method; use Log.e.
     */
    public static final int ERROR = android.util.Log.ERROR;

    /**
     * Priority constant for the println method.
     */
    public static final int ASSERT = android.util.Log.ASSERT;

    private Log() {
    }

    public static void registerLogListener(final LogListener logListener) {
        if (logListener == null)
            return;

        if (!loggers.contains(logListener)) {
            loggers.add(logListener);
        }
    }

    public static void unregisterLogListener(final LogListener logListener) {
        loggers.remove(logListener);
    }

    /**
     * Writes the logcat to a file unless an error has occurred then, assumes the output is no
     * longer valid and sets it to null.
     * If onlyErrors is true, then the file will only contain log entries where a exception was
     * included in the Log call.
     * @param msg the message to write to the file
     * @param tag the tag to include as part of the log file entry
     * @param tr the throwable to include as part of the file entry
     */
    private static void write(String tag, String msg, Throwable tr) {
        for (LogListener logger : loggers) {
            try {
                logger.write(tag, msg, tr);
            } catch (Exception err) {
                android.util.Log.e(tag, "error occured with a logger", err);
            }
        }
    }

    /**
     * https://www.securecoding.cert.org/confluence/display/java/IDS03-J.+Do+not+log+unsanitized+user+input
     * A log injection vulnerability arises when a log entry contains unsanitized 
     * user input. A malicious user can insert fake log data and consequently deceive  
     * system administrators as to the system's behavior [OWASP 2008]. For example, 
     * an attacker might split a legitimate log entry into two log entries by entering 
     * a carriage return and line feed (CRLF) sequence to mislead an auditor. 
     * Log injection attacks can be prevented by sanitizing and validating any 
     * untrusted input sent to a log.
     * Logging unsanitized user input can also result in leaking sensitive data across
     * a trust boundary. For example, an attacker might inject a script into a log file 
     * such that when the file is viewed using a web browser, the browser could provide 
     * the attacker with a copy of the administrator's cookie so that the attacker might 
     * gain access as the administrator.
     */
    private static String sanitize(String msg) {
        if (msg != null) {
            //msg = msg.trim().replaceAll("[^A-Za-z0-9-_. /\u0600-\u06FF]", "")
            //    .replaceAll("\\.\\.", "");
            Matcher matcher = newline.matcher(msg);
            return matcher.replaceAll("\n'-->");
        } else {
            return "[null message logged]";
        }
    }

    /**
     * Send a {@link #VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int v(String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.v(tag, msg);
    }

    /**
     * Send a {@link #VERBOSE} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int v(String tag, String msg, Throwable tr) {
        msg = sanitize(msg);
        write(tag, msg, tr);
        return android.util.Log.v(tag, msg, tr);
    }

    /**
     * Send a {@link #DEBUG} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int d(String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.d(tag, msg);
    }

    /**
     * Send a {@link #DEBUG} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int d(String tag, String msg, Throwable tr) {
        msg = sanitize(msg);
        write(tag, msg, tr);
        return android.util.Log.d(tag, msg, tr);
    }

    /**
     * Send an {@link #INFO} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int i(String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.i(tag, msg);
    }

    /**
     * Send a {@link #INFO} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int i(String tag, String msg, Throwable tr) {
        msg = sanitize(msg);
        write(tag, msg, tr);
        return android.util.Log.i(tag, msg, tr);
    }

    /**
     * Send a {@link #WARN} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int w(String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.w(tag, msg);
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int w(String tag, String msg, Throwable tr) {
        msg = sanitize(msg);
        write(tag, msg, tr);
        return android.util.Log.w(tag, msg, tr);
    }

    /*
     * Send a {@link #WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param tr An exception to log
     */
    public static int w(String tag, Throwable tr) {
        write(tag, "", tr);
        return android.util.Log.w(tag, tr);
    }

    /**
     * Send an {@link #ERROR} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.e(tag, msg);
    }

    /**
     * Send a {@link #ERROR} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int e(String tag, String msg, Throwable tr) {
        msg = sanitize(msg);
        write(tag, msg, tr);
        return android.util.Log.e(tag, msg, tr);
    }

    /**
     * What a Terrible Failure: Report a condition that should never happen.
     * The error will always be logged at level ASSERT with the call stack.
     * Depending on system configuration, a report may be added to the
     * {@link android.os.DropBoxManager} and/or the process may be terminated
     * immediately with an error dialog.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     */
    public static int wtf(String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.e(tag, msg);
    }

    /**
     * What a Terrible Failure: Report an exception that should never happen.
     * Similar to {@link #wtf(String, String)}, with an exception to log.
     * @param tag Used to identify the source of a log message.
     * @param tr An exception to log.
     */
    public static int wtf(String tag, Throwable tr) {
        write(tag, "", tr);
        return android.util.Log.wtf(tag, tr);
    }

    /**
     * What a Terrible Failure: Report an exception that should never happen.
     * Similar to {@link #wtf(String, Throwable)}, with a message as well.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log.  May be null.
     */
    public static int wtf(String tag, String msg, Throwable tr) {
        msg = sanitize(msg);
        write(tag, msg, tr);
        return android.util.Log.wtf(tag, msg, tr);
    }

    /**
     * Low-level logging call.
     * @param priority The priority/type of this log message
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return The number of bytes written.
     */
    public static int println(int priority, String tag, String msg) {
        msg = sanitize(msg);
        write(tag, msg, null);
        return android.util.Log.println(priority, tag, msg);
    }

}
