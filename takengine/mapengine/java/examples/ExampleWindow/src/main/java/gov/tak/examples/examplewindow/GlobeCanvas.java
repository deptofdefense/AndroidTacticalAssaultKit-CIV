package gov.tak.examples.examplewindow;

import android.opengl.JOGLGLES;
import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.opengl.GLBaseMap;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.util.ConfigOptions;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.awt.GLJPanel;

public class GlobeCanvas extends GLCanvas implements GLEventListener, GlobeComponent {
    private Globe globe;
    private GLMapView glglobe;
    private OnRendererInitializedListener listener;

    private MouseMapInputHandler defaultInputHandler;

    public GlobeCanvas() {
        this(new Globe());
    }

    public GlobeCanvas(Globe globe) {
        super(createDefaultCapabilities());

        this.globe = globe;

        this.addGLEventListener(this);
    }


    @Override
    public void setOnRendererInitializedListener(GlobeComponent.OnRendererInitializedListener l) {
        this.listener = l;
    }

    @Override
    public Globe getGlobe() {
        return this.globe;
    }

    @Override
    public MapRenderer2 getRenderer() {
        return this.glglobe;
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        JOGLGLES.init(drawable);
        if(ConfigOptions.getOption("disable-vsync", 0) == 1) {
            GL gl = drawable.getGL();
            gl.setSwapInterval(0);
        }

        // create the Globe renderer and start it
        glglobe = new GLMapView(new JOGLRenderContext(drawable), globe, 0, 0, this.getWidth(), this.getHeight());
        glglobe.setContinuousRenderEnabled(true);
        glglobe.setDisplayMode(MapRenderer2.DisplayMode.Globe);


        this.defaultInputHandler = new MouseMapInputHandler(glglobe);
        this.addMouseListener(this.defaultInputHandler);
        this.addMouseMotionListener(this.defaultInputHandler);
        this.addMouseWheelListener(this.defaultInputHandler);

        glglobe.start();

        glglobe.setBaseMap(new GLBaseMap());

        // set the clear color (black)
        drawable.getGL().getGL().glClearColor(0f, 0f, 0f, 1f);

        if(this.listener != null)
            this.listener.onRendererInitialized(this, glglobe);
    }

    @Override
    public void display(GLAutoDrawable drawable) {

        GL gl = drawable.getGL();
        gl.glClear(GL.GL_COLOR_BUFFER_BIT|GL.GL_DEPTH_BUFFER_BIT|GL.GL_STENCIL_BUFFER_BIT);

        if(glglobe != null)
            glglobe.render();

        //checkError(gl, "display");
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL3 gl = drawable.getGL().getGL3();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();

        if(glglobe != null)
            glglobe.stop();
        if(this.defaultInputHandler != null) {
            this.removeMouseListener(this.defaultInputHandler);
            this.removeMouseMotionListener(this.defaultInputHandler);
            this.removeMouseWheelListener(this.defaultInputHandler);
            this.defaultInputHandler = null;
        }
    }

    static GLCapabilities createDefaultCapabilities() {
        GLProfile glProfile = GLProfile.getMaxProgrammable(true);
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glCapabilities.setDepthBits(24);
        return glCapabilities;
    }
}
