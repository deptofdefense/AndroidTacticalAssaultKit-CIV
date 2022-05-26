package gov.tak.api.widgets.opengl;

import com.atakmap.coremap.log.Log;
import com.atakmap.util.ResourcePool;

import gov.tak.api.engine.map.MapSceneModel;

import gov.tak.api.widgets.IMapWidget;

public interface IGLWidget {
    String TAG = IGLWidget.class.getSimpleName();

    void releaseWidget();

    void drawWidget(DrawState drawState);

    IMapWidget getSubject();

    float getX();
    float getY();
    void setX(float x);
    void setY(float y);

    void start();
    void stop();

    final class DrawState {
        public float[] projectionMatrix = null;
        public float[] modelMatrix = null;
        public float alphaMod = 1f;
        public MapSceneModel scene;

        public DrawState(MapSceneModel mapSceneModel) {
            scene = mapSceneModel;
        }

        @Override
        public DrawState clone() {
            DrawState cloned = DrawStateStack.get();
            cloned.scene = scene;
            cloned.projectionMatrix = projectionMatrix.clone();
            cloned.modelMatrix = modelMatrix.clone();
            cloned.alphaMod = alphaMod;
            return cloned;
        }

        public void recycle() {
            DrawStateStack.put(this);
        }
    }

    final class DrawStateStack {
        private static ResourcePool<DrawState> _stack = new ResourcePool<>(10);

        public static DrawState get() {
            DrawState drawState = _stack.get();
            if (drawState == null) {
                drawState = new DrawState(null);
            }

            return drawState;
        }

        public static boolean put(DrawState drawState) {
            return _stack.put(drawState);
        }
    }
}
