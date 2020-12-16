
package com.atakmap.spatial.file;

import com.atakmap.coremap.log.Log;

import java.util.LinkedList;
import java.util.List;

final class DataIngestReport {

    private static final String TAG = "ErrorReport";

    private List<DataIngestError> fatalErrors = null;
    private List<DataIngestError> continuableErrors = null;
    private List<DataIngestError> continuableWarnings = null;

    private static class DataIngestError {
        public String description;
        Exception exception = null;

        public DataIngestError(String desc) {
            this.description = desc;
        }

        DataIngestError(String desc, Exception execept) {
            this.description = desc;
            this.exception = execept;
        }

        public String toString() {
            if (description != null)
                return description;
            if (this.exception != null)
                return "" + this.exception.getMessage();
            return "";
        }
    }

    public void fatalError(DataIngestError err) {
        if (fatalErrors == null)
            fatalErrors = new LinkedList<>();
        fatalErrors.add(err);
        Log.e(TAG, err.toString());
    }

    public void fatalError(String description, Exception e) {
        fatalError(new DataIngestError(description, e));
    }

    public void fatalError(String description) {
        fatalError(description, null);
    }

    public void continuableError(DataIngestError err) {
        if (continuableErrors == null)
            continuableErrors = new LinkedList<>();
        continuableErrors.add(err);
        Log.w(TAG, err.toString());
    }

    public void continuableError(String description, Exception e) {
        continuableError(new DataIngestError(description, e));
    }

    public void continuableError(String description) {
        continuableError(description, null);
    }

    public void continuableWarning(DataIngestError err) {
        if (continuableWarnings == null)
            continuableWarnings = new LinkedList<>();
        continuableWarnings.add(err);
        Log.d(TAG, err.toString());
    }

    public void continuableWarning(String description, Exception e) {
        continuableWarning(new DataIngestError(description, e));
    }

    public void continuableWarning(String description) {
        continuableWarning(description, null);
    }

    public void addReport(DataIngestReport subReport) {
        boolean shouldPrint = false;
        if (subReport.gotFatalError()) {
            for (DataIngestError err : subReport.getFatalErrors()) {
                fatalError(err);
            }
            shouldPrint = true;
        }

        if (subReport.gotContinuableError()) {
            for (DataIngestError err : subReport.getContinuableErrors()) {
                continuableError(err);
            }
            shouldPrint = true;
        }

        if (subReport.gotContinuableWarning()) {
            for (DataIngestError err : subReport.getContinuableWarnings()) {
                continuableWarning(err);
            }
        }

        if (shouldPrint) {
            Log.d(TAG, "addReport results: " + getNotificationTicker());
        }
    }

    public String getNotificationTicker() {
        return ""
                + (fatalErrors == null ? 0 : fatalErrors.size())
                + " fatal errors, "
                + (continuableErrors == null ? 0 : continuableErrors.size())
                + " errors, "
                + (continuableWarnings == null ? 0 : continuableWarnings.size())
                + " warnings.";
    }

    public String getNotificationMessage() {
        StringBuilder ret = new StringBuilder();
        if (gotFatalError())
            for (DataIngestError err : getFatalErrors())
                ret.append("FATAL: ").append(err.description).append(", ");

        if (gotContinuableError())
            for (DataIngestError err : getContinuableErrors())
                ret.append("ERROR: ").append(err.description).append(", ");

        return ret.toString();
    }

    public boolean gotFatalError() {
        return fatalErrors != null;
    }

    public boolean gotContinuableError() {
        return continuableErrors != null;
    }

    public boolean gotContinuableWarning() {
        return continuableWarnings != null;
    }

    public List<DataIngestError> getFatalErrors() {
        return fatalErrors;
    }

    public List<DataIngestError> getContinuableErrors() {
        return continuableErrors;
    }

    public List<DataIngestError> getContinuableWarnings() {
        return continuableWarnings;
    }

}
