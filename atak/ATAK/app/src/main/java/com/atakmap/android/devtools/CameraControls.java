
package com.atakmap.android.devtools;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;

import gov.tak.api.engine.map.IMapRendererEnums;

final class CameraControls extends DevToolGroup {
    final MapRenderer3 _renderer;

    public CameraControls(MapRenderer3 renderer) {
        super("Camera Controls");
        _renderer = renderer;
        _children.add(new DevToolToggle("Backward", "CameraControls.Backward") {
            @Override
            protected boolean isEnabled() {
                return false;
            }

            @Override
            protected void setEnabled(boolean v) {
                MapSceneModel sm = _renderer.getMapSceneModel(false,
                        IMapRendererEnums.DisplayOrigin.UpperLeft);

                GeoPoint cam = sm.mapProjection.inverse(sm.camera.location,
                        null);
                cam.set(cam.getLatitude() - .01, cam.getLongitude());
                _renderer.lookFrom(cam, sm.camera.azimuth, sm.camera.elevation,
                        IMapRendererEnums.CameraCollision.Ignore, false);
            }
        });

        _children.add(new DevToolToggle("Forward", "CameraControls.Forward") {
            @Override
            protected boolean isEnabled() {
                return false;
            }

            @Override
            protected void setEnabled(boolean v) {
                MapSceneModel sm = _renderer.getMapSceneModel(false,
                        IMapRendererEnums.DisplayOrigin.UpperLeft);

                GeoPoint cam = sm.mapProjection.inverse(sm.camera.location,
                        null);
                cam.set(cam.getLatitude() + .01, cam.getLongitude());
                _renderer.lookFrom(cam, sm.camera.azimuth, sm.camera.elevation,
                        IMapRendererEnums.CameraCollision.Ignore, false);
            }
        });
        _children.add(new DevToolToggle("Look Up", "CameraControls.LookUp") {
            @Override
            protected boolean isEnabled() {
                return false;
            }

            @Override
            protected void setEnabled(boolean v) {
                MapSceneModel sm = _renderer.getMapSceneModel(false,
                        IMapRendererEnums.DisplayOrigin.UpperLeft);
                if (sm.camera.elevation + 5d >= 90d)
                    return;
                GeoPoint cam = sm.mapProjection.inverse(sm.camera.location,
                        null);
                _renderer.lookFrom(cam, sm.camera.azimuth,
                        sm.camera.elevation + 5d,
                        IMapRendererEnums.CameraCollision.Ignore, false);
            }
        });
        _children
                .add(new DevToolToggle("Look Down", "CameraControls.LookDown") {
                    @Override
                    protected boolean isEnabled() {
                        return false;
                    }

                    @Override
                    protected void setEnabled(boolean v) {
                        MapSceneModel sm = _renderer.getMapSceneModel(false,
                                IMapRendererEnums.DisplayOrigin.UpperLeft);
                        if (sm.camera.elevation - 5d <= -90d)
                            return;
                        GeoPoint cam = sm.mapProjection
                                .inverse(sm.camera.location, null);
                        _renderer.lookFrom(cam, sm.camera.azimuth,
                                sm.camera.elevation - 5d,
                                IMapRendererEnums.CameraCollision.Ignore,
                                false);
                    }
                });
        _children
                .add(new DevToolToggle("Look Left", "CameraControls.LookLeft") {
                    @Override
                    protected boolean isEnabled() {
                        return false;
                    }

                    @Override
                    protected void setEnabled(boolean v) {
                        MapSceneModel sm = _renderer.getMapSceneModel(false,
                                IMapRendererEnums.DisplayOrigin.UpperLeft);
                        double az = sm.camera.azimuth - 5d;
                        if (az < -360d)
                            az += 360d;
                        GeoPoint cam = sm.mapProjection
                                .inverse(sm.camera.location, null);
                        _renderer.lookFrom(cam, az, sm.camera.elevation,
                                IMapRendererEnums.CameraCollision.Ignore,
                                false);
                    }
                });
        _children.add(
                new DevToolToggle("Look Right", "CameraControls.LookRight") {
                    @Override
                    protected boolean isEnabled() {
                        return false;
                    }

                    @Override
                    protected void setEnabled(boolean v) {
                        MapSceneModel sm = _renderer.getMapSceneModel(false,
                                IMapRendererEnums.DisplayOrigin.UpperLeft);
                        double az = sm.camera.azimuth + 5d;
                        if (az > 360d)
                            az -= 360d;
                        GeoPoint cam = sm.mapProjection
                                .inverse(sm.camera.location, null);
                        _renderer.lookFrom(cam, az, sm.camera.elevation,
                                IMapRendererEnums.CameraCollision.Ignore,
                                false);
                    }
                });
    }
}
