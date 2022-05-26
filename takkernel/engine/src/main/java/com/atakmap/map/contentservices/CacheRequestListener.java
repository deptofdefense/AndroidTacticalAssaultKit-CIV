package com.atakmap.map.contentservices;

public interface CacheRequestListener {
    public void onRequestStarted();
    public void onRequestComplete();
    public void onRequestProgress(int taskNum, int numTasks, int taskProgress, int maxTaskProgress, int totalProgress, int maxTotalProgress);
    public boolean onRequestError(Throwable t, String message, boolean fatal);
    public void onRequestCanceled();
}
