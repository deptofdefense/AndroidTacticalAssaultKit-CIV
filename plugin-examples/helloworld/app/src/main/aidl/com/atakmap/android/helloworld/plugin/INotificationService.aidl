// INotificationService.aidl
package com.atakmap.android.helloworld.plugin;

// Declare any non-default types here with import statements

interface INotificationService {
    void createNotification(int notificationId, String notificationText);
}