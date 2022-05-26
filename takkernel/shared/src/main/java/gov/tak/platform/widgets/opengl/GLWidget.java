
package gov.tak.platform.widgets.opengl;

import gov.tak.api.engine.map.RenderContext;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.api.engine.Shader;
import gov.tak.platform.widgets.MapWidget;

/**
 * Map widget with view properties such as width, height, margin, and padding
 */
public abstract class GLWidget  implements IGLWidget,
        MapWidget.OnWidgetSizeChangedListener, MapWidget.OnWidgetPointChangedListener,
        MapWidget.OnVisibleChangedListener {

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    protected final IMapWidget subject;
    protected float _width = 0, _pWidth = 0, _height = 0, _pHeight = 0;
    protected float[] _margin, _padding;
    protected boolean _sizeChanged = true;
    float x, y;
    protected final RenderContext _renderContext;
    protected final MapRenderer _mapRenderer;

    Shader defaultShader = null;
    Shader textureShader = null;

    void initShaders() {
        if (defaultShader == null) {
            synchronized (this) {
                if(defaultShader == null)
                    defaultShader = Shader.create(Shader.FLAG_2D, _renderContext);

            }
        }
        if (textureShader == null)
            synchronized (this) {
                if(textureShader == null)
                    textureShader = Shader.create(Shader.FLAG_2D | Shader.FLAG_TEXTURED, _renderContext);
            }
    }

    public  Shader getTextureShader(){
        initShaders();
        return textureShader;
    }
    public  Shader getDefaultShader() {
        initShaders();
        return defaultShader;
    }

    protected MapRenderer getMapRenderer() {
        return _mapRenderer;
    }

    public GLWidget(IMapWidget subject, MapRenderer renderer) {
        this.subject = subject;
        setX(subject.getPointX());
        setY(subject.getPointY());
        _mapRenderer = renderer;
        _renderContext = renderer.getRenderContext();
        if(subject instanceof IMapWidget)
           onWidgetSizeChanged(subject);
    }

    public void start() {
        subject.addOnWidgetPointChangedListener(this);
        subject.addOnVisibleChangedListener(this);

        if (subject instanceof IMapWidget)
            subject.addOnWidgetSizeChangedListener(this);
    }

    public void stop() {
        subject.removeOnWidgetPointChangedListener(this);
        subject.removeOnVisibleChangedListener(this);

        if (subject instanceof IMapWidget)
            subject.removeOnWidgetSizeChangedListener(this);
    }

    @Override
    public void onWidgetSizeChanged(IMapWidget widget) {
        final float width = widget.getWidth();
        final float height = widget.getHeight();
        final float[] margin = widget.getMargins();
        final float[] padding = widget.getPadding();

        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _width = width;
                _pWidth = width + padding[LEFT] + padding[RIGHT];
                _height = height;
                _pHeight = height + padding[TOP] + padding[BOTTOM];
                _margin = margin;
                _padding = padding;
                _sizeChanged = true;
            }
        });
    }

    public void drawWidget(DrawState drawState) {
        if(subject instanceof IMapWidget &&  (_margin == null
                || _padding == null))
            return;
        if (subject != null && subject.isVisible()) {
            DrawState newDrawState = drawState.clone();
            Matrix.translateM(newDrawState.modelMatrix, 0, x, -y, 0);
            drawWidgetContent(newDrawState);
            newDrawState.recycle();
        }
    }

    @Override
    public void onWidgetPointChanged(IMapWidget widget) {
        final float xpos = widget.getPointX();
        final float ypos = widget.getPointY();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                x = xpos;
                setY(ypos);
            }
        });
    }

    @Override
    public void onVisibleChanged(IMapWidget widget) {
       this._renderContext.requestRefresh();
    }


    /**
     * Renders the widget content.
     *
     * <P><B>Must be invoked on GL render thread!</B>
     */
    public abstract void drawWidgetContent(DrawState drawState);

    /**
     * Releases any resources allocated as a result of
     * {@link #drawWidgetContent(DrawState}.
     *
     * <P><B>Must be invoked on GL render thread!</B>
     */
    public abstract void releaseWidget();

    protected RenderContext getRenderContext()
    {
        return _renderContext;
    }

    @Override
    public IMapWidget getSubject() {
        return subject;
    }

    @Override
    public float getX() {
        return x;
    }
    @Override
    public float getY() {
        return y;
    }
    @Override
    public void setX(float x) {
        this.x = x;
    }
    @Override
    public void setY(float y) {
        this.y = y;
    }

    protected void runOrQueueEvent(Runnable r) {
        if (_renderContext.isRenderThread())
            r.run();
        else
            _renderContext.queueEvent(r);
    }
}
