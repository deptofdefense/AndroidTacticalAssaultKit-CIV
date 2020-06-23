
package com.atakmap.android.hierarchy.action;

public interface ActionSpi {
    Action create(String command, String token);
}
