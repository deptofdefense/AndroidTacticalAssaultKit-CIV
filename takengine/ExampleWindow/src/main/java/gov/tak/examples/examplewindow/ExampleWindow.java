
package gov.tak.examples.examplewindow;

import android.content.Context;
import android.util.Log;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.*;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.formats.mapbox.MapBoxElevationSource;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.ProxyLayer;
import com.atakmap.map.layer.elevation.ElevationHeatmapLayer;
import com.atakmap.map.layer.elevation.TerrainSlopeLayer;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.raster.*;
import com.atakmap.map.layer.raster.gpkg.GeoPackageTileContainer;
import com.atakmap.map.layer.raster.mobac.MobacMapSourceLayerInfoSpi;
import com.atakmap.map.layer.raster.mobac.MobacTileClient2;
import com.atakmap.map.layer.raster.mobac.MobacTileReader;
import com.atakmap.map.layer.raster.opengl.GLDatasetRasterLayer2;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.osm.OSMDroidTileContainer;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;
import com.atakmap.map.layer.raster.tilematrix.TileContainerFactory;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTiledMapLayer2;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.net.*;
import com.atakmap.opengl.GLSLUtil;
import com.atakmap.util.ConfigOptions;
import com.jogamp.opengl.util.Animator;
import gov.tak.examples.examplewindow.overlays.FrameRateOverlay;
import gov.tak.examples.examplewindow.overlays.PointerInformationOverlay;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.atakmap.coremap.loader.NativeLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by elect on 04/03/17.
 */

public class ExampleWindow implements KeyListener {

    private static JFrame window;
    private static Animator animator;

    private static Context context;

    public static void main(String[] args) throws Throwable {

        //System.out.println("press enter to continue"); System.in.read();

        // This is a hack to move around jogl and gluegen jars so the paths are correct for version 2.1.5-01
        wrangleJogl();

        android.util.Log.minLevel = Log.INFO;

        context = new Context();

        GLRenderGlobals.appContext = context;
        GLSLUtil.setContext(context);
        NativeLoader.init(context);
        EngineLibrary.initialize();

        MapSceneModel.setPerspectiveCameraEnabled(true);

        if(System.getProperty("disable-vsync", "false").equals("true"))
            ConfigOptions.setOption("disable-vsync", 1);
        if(System.getProperty("diagnostics", "false").equals("true"))
            ConfigOptions.setOption("glmapview.render-diagnostics", 1);

        ConfigOptions.setOption("glmapview.gpu-terrain-intersect", "1");

        Security.addProvider(new BouncyCastleProvider());
        CertificateManager.setCertificateDatabase(AtakCertificateDatabase.getAdapter());
        CertificateManager.getInstance().initialize(context);

        AtakCertificateDatabase.initialize(context);
        AtakAuthenticationDatabase.initialize(context);

        // provider registration

        // Imagery Providers
        DatasetDescriptorFactory2.register(MobacMapSourceLayerInfoSpi.INSTANCE);
        TileClientFactory.registerSpi(MobacTileClient2.SPI);
        TileReaderFactory.registerSpi(MobacTileReader.SPI);
        GLMapLayerFactory.registerSpi(GLTiledMapLayer2.SPI);
        GLLayerFactory.register(GLDatasetRasterLayer2.SPI);

        TileContainerFactory.registerSpi(OSMDroidTileContainer.SPI);
        TileContainerFactory.registerSpi(GeoPackageTileContainer.SPI);

        new ExampleWindow().setup();
    }

    private Globe globe;

    private static void wrangleJogl() throws IOException {

        Pattern joglPattern = Pattern.compile("jogl-all-(.+)\\.jar");
        Pattern gluegenPattern = Pattern.compile("gluegen-rt-(.+)\\.jar");

        URL[] classPath = ((URLClassLoader) (Thread.currentThread().getContextClassLoader())).getURLs();
        java.util.List<File> joglSrcs = new ArrayList<>();
        java.util.List<File> gluegenSrcs = new ArrayList<>();
        File joglDst = null;
        File gluegenDst = null;

        // XXX-- other versions as well?
        final String copyVersion = "2.1.5-01";

        for (URL path : classPath) {
            try {
                File file = new File(path.getFile());
                String fileName = file.getName();
                Matcher matched = joglPattern.matcher(fileName);
                if (!matched.matches())
                    matched = gluegenPattern.matcher(fileName);
                if (matched.matches()) {
                    String versionPlat = matched.group(1);
                    if (versionPlat.startsWith(copyVersion)) {
                        if (versionPlat.equals(copyVersion)) {
                            if (joglPattern == matched.pattern())
                                joglDst = file.getParentFile();
                            else if (gluegenPattern == matched.pattern())
                                gluegenDst = file.getParentFile();
                        } else {
                            if (joglPattern == matched.pattern())
                                joglSrcs.add(file);
                            else if (gluegenPattern == matched.pattern())
                                gluegenSrcs.add(file);
                        }
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
        }

        copyFiles(joglSrcs, joglDst);
        copyFiles(gluegenSrcs, gluegenDst);
    }

    private static void copyFiles(java.util.List<File> srcFiles, File dstDir) throws IOException {
        if (dstDir != null) {
            for (File srcFile : srcFiles) {
                File dst = new File(dstDir, srcFile.getName());
                if (!dst.exists())
                    FileSystemUtils.copyFile(srcFile, dst);
            }
        }
    }

    private void setup() {
        this.globe = new Globe();
        File appDataDir = getAppDataDir();

        final String mapboxToken = "pk.eyJ1IjoiY2hyaXN0b3BoZXJwbGF3cmVuY2UiLCJhIjoiY2tjY2xvODhiMDV1NzJ5bnpxYTllNDk0eCJ9.JTC0gihP5n4ssILzOMlwiw";

        // create a simple raster layer
        try {
            File imageryCacheDir = new File(appDataDir, "imagecache");
            if(!imageryCacheDir.exists())
                imageryCacheDir.mkdirs();

            // mapbox satellite as the base
            final String mapboxSatelliteUrl =  "https://api.mapbox.com/v4/mapbox.satellite/{$z}/{$x}/{$y}.jpg90?access_token=";
            globe.addLayer(Imagery.createTiledImageryLayer("MapBox Satellite", mapboxSatelliteUrl+mapboxToken, imageryCacheDir));
        } catch(Throwable e) {
            System.err.println("Failed to extract WMS pointer");
        }

        ElevationHeatmapLayer heatmap = new ElevationHeatmapLayer("Heat Map");
        heatmap.setDynamicRange();
        globe.addLayer(heatmap);

        TerrainSlopeLayer terrainSlope = new TerrainSlopeLayer("Terrain Slope Angle");
        globe.addLayer(terrainSlope);

        PointerInformationOverlay pointerInfo = new PointerInformationOverlay();
        globe.addLayer(pointerInfo);

        FrameRateOverlay fps = new FrameRateOverlay();
        globe.addLayer(fps);

        // add an elevation source
        final MapBoxElevationSource elevationSource = new MapBoxElevationSource(mapboxToken, new File(appDataDir, "mapbox/terrainrgb"));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                ElevationSourceManager.detach(elevationSource);
            }
        });
        ElevationSourceManager.attach(elevationSource);

        GlobeComponent panel;
        if(System.getProperty("heavyweight-globe", "false").equals("true"))
            panel = new GlobeCanvas(globe);
        else
            panel = new GlobePanel(globe);
        panel.setOnRendererInitializedListener(new GlobeComponent.OnRendererInitializedListener() {
            @Override
            public void onRendererInitialized(Component comp, MapRenderer2 renderer) {
                if(window.getJMenuBar() == null) {
                    window.setJMenuBar(buildMenuBar(globe, renderer));
                    window.revalidate();
                }

                // restore the last view on initialization
                try {
                    loadView(renderer, "last");
                } catch(Throwable ignored) {}
            }
        });

        window = new JFrame();

        window.setTitle("TAK Globe Example");
        window.setSize(1024, 768);
        window.setResizable(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        window.getContentPane().setLayout(new BorderLayout());
        window.getContentPane().add((Component)panel, BorderLayout.CENTER);

        if(panel.getRenderer() != null)
            window.setJMenuBar(buildMenuBar(globe, panel.getRenderer()));

        window.setVisible(true);

        window.addKeyListener(this);

        animator = new Animator(panel);
        animator.setRunAsFastAsPossible(true);
        animator.start();

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    storeView(panel.getRenderer(), "last");
                } catch(Throwable ignored) {}

                animator.stop();
            }
        });

        ((Component)panel).addMouseMotionListener(pointerInfo);
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            new Thread(() -> {
                window.dispose();
            }).start();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    private static File extractWmsPointer(Context ctx, String name) throws IOException {
        InputStream strm = ctx.getAssets().open("wms/" + name);
        if(strm == null)
            throw new FileNotFoundException("wms/" + name);
        File dst = File.createTempFile("wms", "." + FileSystemUtils.getExtension(new File(name), false, false));
        FileSystemUtils.copyStream(strm, true, new FileOutputStream(dst), true);
        return dst;
    }

    private static void storeView(MapRenderer2 camera, String viewname) throws IOException {
        File savedViewsDir = new File(getAppDataDir(), "views");
        if(!savedViewsDir.exists())
            savedViewsDir.mkdirs();

        MapSceneModel sm = camera.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
        GeoPoint focus = sm.mapProjection.inverse(sm.camera.target, null);

        Properties viewprops = new Properties();
        viewprops.put("focus.latitude", String.valueOf(focus.getLatitude()));
        viewprops.put("focus.longitude", String.valueOf(focus.getLongitude()));
        if(!Double.isNaN(focus.getAltitude()))
            viewprops.put("focus.altitude", String.valueOf(focus.getAltitude()));
        viewprops.put("tilt", String.valueOf(90d+sm.camera.elevation));
        viewprops.put("rotation", String.valueOf(sm.camera.azimuth));
        viewprops.put("resolution", String.valueOf(sm.gsd));

        try(FileOutputStream fos = new FileOutputStream(new File(savedViewsDir, viewname))) {
            viewprops.store(fos, null);
        }
    }

    private static void loadView(MapRenderer2 camera, String viewname) throws IOException {
        File savedViewsDir = new File(getAppDataDir(), "views");
        if(!savedViewsDir.exists())
            return;

        final File viewFile = new File(savedViewsDir, viewname);
        if(!viewFile.exists())
            return;

        Properties viewprops = new Properties();
        try (FileInputStream fis = new FileInputStream(new File(savedViewsDir, viewname))) {
            viewprops.load(fis);
        }

        try {
            final String focus_latitude = viewprops.getProperty("focus.latitude", null);
            if(focus_latitude == null)
                return;
            final String focus_longitude = viewprops.getProperty("focus.longitude", null);
            if(focus_longitude == null)
                return;
            final String focus_altitude = viewprops.getProperty("focus.altitude", null);
            final String resolution = viewprops.getProperty("resolution", null);
            if(resolution == null)
                return;
            final String rotation = viewprops.getProperty("rotation", null);
            if(rotation == null)
                return;
            final String tilt = viewprops.getProperty("tilt", null);
            if(tilt == null)
                return;

            GeoPoint focus = GeoPoint.createMutable();
            focus.set(Double.parseDouble(focus_latitude), Double.parseDouble(focus_longitude));
            if(focus_altitude != null)
                focus.set(Double.parseDouble(focus_altitude));

            camera.lookAt(focus, Double.parseDouble(resolution), Double.parseDouble(rotation), Double.parseDouble(tilt), true);
        } catch(Throwable t) {
            return;
        }
    }

    private static File getAppDataDir() {
        File cacheDir = new File(System.getProperty("user.home"));
        if(!cacheDir.exists())
            cacheDir = context.getCacheDir();
        else
            cacheDir = new File(cacheDir, "TAK Example Globe");
        if(!cacheDir.exists())
            cacheDir.mkdirs();
        return cacheDir;
    }

    private static JMenuBar buildMenuBar(final Globe globe, final MapRenderer2 renderer) {
        JMenuBar menubar = new JMenuBar();

        JMenu viewMenu = new JMenu("View");
        JMenu cameraMenu = new JMenu("Camera");
        ButtonGroup cameraGroup = new ButtonGroup();
        JRadioButtonMenuItem orthoCameraOption = new JRadioButtonMenuItem("Orthographic");
        orthoCameraOption.setSelected(!MapSceneModel.isPerspectiveCameraEnabled());
        JRadioButtonMenuItem perspectiveCameraOption = new JRadioButtonMenuItem("Perspective");
        perspectiveCameraOption.setSelected(MapSceneModel.isPerspectiveCameraEnabled());
        cameraGroup.add(orthoCameraOption);
        cameraGroup.add(perspectiveCameraOption);
        orthoCameraOption.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                MapSceneModel.setPerspectiveCameraEnabled(!((JRadioButtonMenuItem)e.getItem()).isSelected());
            }
        });
        perspectiveCameraOption.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                MapSceneModel.setPerspectiveCameraEnabled(((JRadioButtonMenuItem)e.getItem()).isSelected());
            }
        });

        cameraMenu.add(orthoCameraOption);
        cameraMenu.add(perspectiveCameraOption);

        viewMenu.add(cameraMenu);

        JMenu projectionMenu = new JMenu("Projection");
        ButtonGroup projectionGroup = new ButtonGroup();
        JRadioButtonMenuItem flatProjectionOption = new JRadioButtonMenuItem("Flat");
        flatProjectionOption.setSelected(renderer.getDisplayMode() == MapRenderer2.DisplayMode.Flat);
        JRadioButtonMenuItem globeProjectionOption = new JRadioButtonMenuItem("Globe");
        globeProjectionOption.setSelected(renderer.getDisplayMode() == MapRenderer2.DisplayMode.Globe);
        projectionGroup.add(flatProjectionOption);
        projectionGroup.add(globeProjectionOption);
        flatProjectionOption.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                final boolean flatTarget = ((JRadioButtonMenuItem)e.getItem()).isSelected();
                final boolean flatCurrent = (renderer.getDisplayMode() == MapRenderer2.DisplayMode.Flat);
                if(flatTarget != flatCurrent)
                    renderer.setDisplayMode(flatTarget ? MapRenderer2.DisplayMode.Flat : MapRenderer2.DisplayMode.Globe);
            }
        });
        globeProjectionOption.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                final boolean globeTarget = ((JRadioButtonMenuItem)e.getItem()).isSelected();
                final boolean globeCurrent = (renderer.getDisplayMode() == MapRenderer2.DisplayMode.Globe);
                if(globeCurrent != globeCurrent)
                    renderer.setDisplayMode(globeTarget ? MapRenderer2.DisplayMode.Globe : MapRenderer2.DisplayMode.Flat);
            }
        });

        projectionMenu.add(flatProjectionOption);
        projectionMenu.add(globeProjectionOption);

        viewMenu.add(projectionMenu);

        JMenuItem imageryCheckbox = createLayerToggle(globe, RasterLayer2.class);
        if(imageryCheckbox != null)
            viewMenu.add(imageryCheckbox);

        JMenuItem heatmapCheckbox = createLayerToggle(globe, ElevationHeatmapLayer.class);
        if(heatmapCheckbox != null)
            viewMenu.add(heatmapCheckbox);

        JMenuItem terrainSlopeAngleCheckbox = createLayerToggle(globe, TerrainSlopeLayer.class);
        if(terrainSlopeAngleCheckbox != null)
            viewMenu.add(terrainSlopeAngleCheckbox);

        JMenuItem showFramerateCheckbox = createLayerToggle(globe, FrameRateOverlay.class);
        if(showFramerateCheckbox != null)
            viewMenu.add(showFramerateCheckbox);

        JMenuItem showPointerInfoCheckbox = createLayerToggle(globe, PointerInformationOverlay.class);
        if(showPointerInfoCheckbox != null)
            viewMenu.add(showPointerInfoCheckbox);

        menubar.add(viewMenu);

        return menubar;
    }

    static Layer findLayer(Collection<Layer> layers, Class<? extends Layer> type) {
        if(layers == null)
            return null;
        for(Layer l : layers) {
            if(type.isAssignableFrom(l.getClass())) {
                return l;
            } else if(l instanceof MultiLayer) {
                final Layer hit = findLayer(((MultiLayer) l).getLayers(), type);
                if (hit != null)
                    return hit;
            } else if(l instanceof ProxyLayer) {
                if(type.isAssignableFrom(((ProxyLayer)l).get().getClass()))
                    return ((ProxyLayer)l).get();
            }
        }
        return null;
    }

    static boolean isVisible(Globe globe, Class<? extends Layer> type) {
        Layer l = findLayer(globe.getLayers(), type);
        return (l != null) && l.isVisible();
    }

    static JMenuItem createLayerToggle(Globe globe, Class<? extends Layer> clazz) {
        final Layer l = findLayer(globe.getLayers(), clazz);
        if(l == null)
            return null;
        JCheckBoxMenuItem toggleCheckbox = new JCheckBoxMenuItem(l.getName(), l.isVisible());
        toggleCheckbox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                    l.setVisible(((JCheckBoxMenuItem)e.getSource()).isSelected());
            }
        });
        return toggleCheckbox;
    }
}