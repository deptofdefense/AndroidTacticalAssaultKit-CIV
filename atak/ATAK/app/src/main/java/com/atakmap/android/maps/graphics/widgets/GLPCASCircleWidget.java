
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.os.SystemClock;

import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.List;

public class GLPCASCircleWidget {

    GLPCASCircleWidget(float innerRadius, float outerRadius, int startColor,
            int endColor) {
        _build(innerRadius, outerRadius, startColor, endColor);
    }

    public void setAlpha(float alpha) {
        _colorA.animateTo(alpha);
    }

    public void draw() {
        _radius.update();
        _colorA.update();
        _colorAnimation.update();

        float alpha = _colorA.get();
        boolean blend = false;
        if (alpha < 1f) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
            blend = true;
        }
        GLES20FixedPipeline.glColor4f(
                _colorR.get() / 255f,
                _colorG.get() / 255f,
                _colorB.get() / 255f,
                alpha);

        GLES20FixedPipeline.glLineWidth(2f);

        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        // Fill:
        _verts.rewind();
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _verts);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                0, _vertCount);

        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        if (blend) {
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }
    }

    private void _build(float innerRadius, float outerRadius, int startColor,
            int endColor) {
        _radius.animateTo(1f);
        _colorA.animateTo(0.6f);

        int lineCount = Math.max((int) ((outerRadius / 20f) * 12f), 4);
        int vertCount = lineCount + 1;
        double theta = 0;
        double step = (360d / lineCount) * Math.PI / 180d;

        _vertCount = vertCount * 2;
        _verts = com.atakmap.lang.Unsafe.allocateDirect(_vertCount * 2 * 4);
        _verts.order(ByteOrder.nativeOrder());
        FloatBuffer fb = _verts.asFloatBuffer();

        // Outputs triangle strip:
        for (int i = 0; i < vertCount; ++i) {
            float cosa = (float) Math.cos(theta);
            float sina = (float) Math.sin(theta);

            // Outer:
            fb.put(outerRadius * cosa); // x
            fb.put(outerRadius * sina); // y

            // Inner:
            fb.put(innerRadius * cosa); // x
            fb.put(innerRadius * sina); // y

            theta += step;
        }
        fb.rewind();

        _colorAnimation = new Animation(1 / 30f, 3f, true);
        _colorAnimation.addKeyFrame(0f, 0.9f, _radius);
        _colorAnimation.addKeyFrame(0f, Color.red(startColor), _colorR);
        _colorAnimation.addKeyFrame(0f, Color.green(startColor), _colorG);
        _colorAnimation.addKeyFrame(0f, Color.blue(startColor), _colorB);
        // _circleColor.addKeyFrame(0f, 1f, _circleColorA);
        _colorAnimation.addKeyFrame(1f, Color.red(endColor), _colorR);
        _colorAnimation.addKeyFrame(1f, Color.green(endColor), _colorG);
        _colorAnimation.addKeyFrame(1f, Color.blue(endColor), _colorB);
        // _circleColor.addKeyFrame(1f, 1f, _circleColorA);
        _colorAnimation.addKeyFrame(1.0f, 1f, _radius);
        _colorAnimation.addKeyFrame(2f, Color.red(startColor), _colorR);
        _colorAnimation.addKeyFrame(2f, Color.green(startColor), _colorG);
        _colorAnimation.addKeyFrame(2f, Color.blue(startColor), _colorB);
        _colorAnimation.addKeyFrame(0f, 0.9f, _radius);
    }

    static private class AnimatedFloat {

        public AnimatedFloat(float init, float transTime, boolean isWrapped) {
            set(init);
            _target = get();
            _transTime = Math.max(0f, transTime);
            _range = _max - _min;
            _isWrapped = isWrapped;
        }

        public void animateTo(float target) {
            float wtarget = wrap(normalize(target));
            float wcurrent = wrap(get());
            _target = target;
            _start = get();
            _error = wrap(wtarget - wcurrent);
            if (!_isAnimating) {
                _setTimeNS = System.nanoTime();
            }
            _isAnimating = true;
        }

        public float get() {
            return _current;
        }

        protected void set(float current) {
            _current = normalize(current);
        }

        public void update() {

            if (_isAnimating) {
                long nowNS = System.nanoTime();
                float elapsedS = ((nowNS - _setTimeNS) / 1000000L) / 1000f;
                float alpha = elapsedS / _transTime;
                float t = _scurve(alpha);
                if (alpha >= 1f) {
                    set(_target);
                    _isAnimating = false;
                } else {
                    set(_start + _error * t);
                }
            }
        }

        protected float normalize(float value) {
            float v = value;
            if (v < _min) {
                while (v < _min) {
                    v += _max;
                }
            } else if (v >= _max) {
                while (v >= _max) {
                    v -= _max;
                }
            }
            return v;
        }

        protected float wrap(float value) {
            if (_isWrapped) {
                if (value > _range / 2f) {
                    return value - _max;
                } else if (value < -(_range / 2f)) {
                    return value + _max;
                }
            }
            return value;
        }

        private float _scurve(float x) {
            float xx = x * x;
            float xxx = xx * x;
            return 3 * xx - 2 * xxx;
        }

        private boolean _isAnimating = false;
        private float _current;
        private float _start;
        private float _target;
        private float _error;
        private float _transTime = 2f;
        private long _setTimeNS = 0;
        private final float _min = -Float.MAX_VALUE;
        private final float _max = Float.MAX_VALUE;
        private float _range = 0f;
        private boolean _isWrapped = false;
    }

    static private class AnimationKeyFrame {
        public AnimationKeyFrame(float time, float value, AnimatedFloat ref) {
            _time = time;
            _value = value;
            _ref = ref;
        }

        public float getTime() {
            return _time;
        }

        public float getValue() {
            return _value;
        }

        public AnimatedFloat getRef() {
            return _ref;
        }

        public void mark() {
            _marked = true;
        }

        public boolean isMarked() {
            return _marked;
        }

        public void reset() {
            _marked = false;
        }

        private final float _time;
        private final float _value;
        private final AnimatedFloat _ref;
        private boolean _marked = false;
    }

    static private class Animation {
        public Animation(float stepSize, float durationS, boolean loop) {
            _stepS = stepSize;
            _durationS = (float) Math.floor(durationS / _stepS) * _stepS;
            _loop = loop;
        }

        public void update() {
            long nowMS = _getTimeMS();
            float deltaS = (float) (nowMS - _lastTimeMS) / 1000f;
            _lastTimeMS = nowMS;
            _currentTimeS += deltaS;
            _accumulatedTimeS += deltaS;

            while (_accumulatedTimeS >= _stepS) {
                for (AnimationKeyFrame f : _keyFrames) {
                    if (!f.isMarked() && _currentTimeS > f.getTime()) {
                        f.getRef().animateTo(f.getValue());
                        f.mark();
                    }

                    f.getRef().update();
                }

                _accumulatedTimeS -= _stepS;
            }

            // Loop:
            if (_loop && _currentTimeS > _durationS) {
                reset();
            }
        }

        public void reset() {
            _currentTimeS = 0f;
            for (AnimationKeyFrame f : _keyFrames) {
                f.reset();
            }
        }

        public void addKeyFrame(float time, float value, AnimatedFloat ref) {
            float stime = (float) Math.floor(time / _stepS) * _stepS;
            _keyFrames.add(new AnimationKeyFrame(stime, value, ref));
        }

        private long _getTimeMS() {
            return SystemClock.elapsedRealtime();
        }

        private final List<AnimationKeyFrame> _keyFrames = new LinkedList<>();
        float _durationS = 1f;
        float _currentTimeS = 0f;
        float _accumulatedTimeS = 0f;
        long _lastTimeMS = _getTimeMS();
        boolean _loop = false;

        private float _stepS = 1f / 30f;
    }

    private ByteBuffer _verts;
    private int _vertCount = 0;
    private Animation _colorAnimation;
    private final AnimatedFloat _colorR = new AnimatedFloat(1f, 1.0f, false);
    private final AnimatedFloat _colorG = new AnimatedFloat(1f, 1.0f, false);
    private final AnimatedFloat _colorB = new AnimatedFloat(1f, 1.0f, false);
    private final AnimatedFloat _colorA = new AnimatedFloat(0f, 1.0f, false);
    private final AnimatedFloat _radius = new AnimatedFloat(1f, 1.0f, false);
}
