
package com.atakmap;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import android.os.Environment;

/**
 * Common Class that will initialize a basic MapView
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
        Marker.class, Log.class, NetworkUtils.class, MapItem.class,
        MapView.class,
        FileSystemUtils.class, Environment.class, android.util.Log.class
})
public class MapViewMocker {

    /**
     * Gets a Mocked MapView class
     *
     * @return A Mocked mapview class
     */
    public MapView getMapView() {
        PowerMockito.mockStatic(android.util.Log.class);
        PowerMockito.mockStatic(Environment.class);
        PowerMockito.mockStatic(MapItem.class);
        PowerMockito.mockStatic(Marker.class);
        PowerMockito.mockStatic(NetworkUtils.class);
        PowerMockito.when(NetworkUtils.getIP()).thenReturn("127.0.0.1");
        PowerMockito.mockStatic(FileSystemUtils.class);
        File fileMock = PowerMockito.mock(File.class);
        PowerMockito.when(fileMock.getAbsolutePath()).thenReturn("filePath");
        PowerMockito.when(FileSystemUtils.getItem(Matchers.anyString()))
                .thenReturn(fileMock);
        PowerMockito.mockStatic(MapView.class);

        MapView mapViewMock = PowerMockito.mock(MapView.class);
        PowerMockito.when(MapView.getMapView()).thenReturn(mapViewMock);
        return mapViewMock;
    }

    /**
     * Sample test for mapview to satisy the test class since there must be at least one test in the
     * class. The test passing indicates there was no issue mocking mapview
     */
    @Test
    public void testMapView() {
        MapView mapView = getMapView();
        Assert.assertNotNull(mapView);
    }
}
