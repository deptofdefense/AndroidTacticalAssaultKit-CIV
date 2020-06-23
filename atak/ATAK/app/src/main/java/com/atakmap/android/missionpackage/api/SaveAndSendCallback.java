
package com.atakmap.android.missionpackage.api;

import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;

/**
 * Interface exposed via Intent API Invoked on sender when upon success or failure when a Mission
 * Package is saved and sent. Implementations must have a parameterless constructor
 * 
 * 
 */
public interface SaveAndSendCallback extends MissionPackageBaseTask.Callback {

}
