
package gov.tak.examples.examplewindow;

import android.content.Context;
import android.graphics.PointF;
import android.opengl.JOGLGLES;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.*;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.formats.mapbox.MapBoxElevationSource;
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
import com.atakmap.map.opengl.GLBaseMap;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.net.*;
import com.atakmap.opengl.GLSLUtil;
import com.jogamp.opengl.*;
import com.jogamp.opengl.util.Animator;
import gov.tak.examples.examplewindow.overlays.PointerInformationOverlay;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import uno.glsl.Program;

import com.atakmap.coremap.loader.NativeLoader;
import com.atakmap.map.opengl.GLMapView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.security.Security;
import java.util.Properties;

/**
 * Created by elect on 04/03/17.
 */

public class ExampleWindow implements KeyListener {

    private static JFrame window;
    private static Animator animator;

    private static Context context;

    public static void main(String[] args) throws Throwable {
        //System.out.println("press enter to continue"); System.in.read();

        context = new Context();

        GLRenderGlobals.appContext = context;
        GLSLUtil.setContext(context);
        NativeLoader.init(context);
        EngineLibrary.initialize();

        Security.addProvider(new BouncyCastleProvider());
        CertificateManager.setCertificateDatabase(AtakCertificateDatabase.getAdapter());
        CertificateManager.getInstance().initialize(context);

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

    private Program program;

    private Globe globe;

    private void setup() {
        this.globe = new Globe();
        File appDataDir = getAppDataDir();

        final String mapboxToken = "pk.eyJ1IjoiY2hyaXN0b3BoZXJwbGF3cmVuY2UiLCJhIjoiY2tjY2xvODhiMDV1NzJ5bnpxYTllNDk0eCJ9.JTC0gihP5n4ssILzOMlwiw";

        // create a simple raster layer
        try {
            final File imageryCacheDir = new File(appDataDir, "imagecache");
            if(!imageryCacheDir.exists())
                imageryCacheDir.mkdirs();

            // mapbox satellite as the base
            final String mapboxSatelliteUrl =  "https://api.mapbox.com/v4/mapbox.satellite/{$z}/{$x}/{$y}.jpg90?access_token=";
            globe.addLayer(Imagery.createTiledImageryLayer("MapBox Satellite", mapboxSatelliteUrl+mapboxToken, imageryCacheDir));
        } catch(IOException e) {
            System.err.println("Failed to extract WMS pointer");
        }

        PointerInformationOverlay pointerInfo = new PointerInformationOverlay();
        globe.addLayer(pointerInfo);

        // add an elevation source
        final MapBoxElevationSource elevationSource = new MapBoxElevationSource(mapboxToken, new File(appDataDir, "mapbox/terrainrgb"));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                ElevationSourceManager.detach(elevationSource);
            }
        });
        ElevationSourceManager.attach(elevationSource);

        GlobeComponent panel = new GlobeCanvas(globe);
        panel.setOnRendererInitializedListener(new GlobeComponent.OnRendererInitializedListener() {
            @Override
            public void onRendererInitialized(Component comp, MapRenderer2 renderer) {
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

        window.setVisible(true);

        window.addKeyListener(this);

        animator = new Animator(panel);
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
        GeoPoint focus = sm.inverse(new PointF(sm.focusx, sm.focusy), null);

        Properties viewprops = new Properties();
        viewprops.put("focus.latitude", String.valueOf(focus.getLatitude()));
        viewprops.put("focus.longitude", String.valueOf(focus.getLongitude()));
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
            final String resolution = viewprops.getProperty("resolution", null);
            if(resolution == null)
                return;
            final String rotation = viewprops.getProperty("rotation", null);
            if(rotation == null)
                return;
            final String tilt = viewprops.getProperty("tilt", null);
            if(tilt == null)
                return;

            GeoPoint focus = new GeoPoint(Double.parseDouble(focus_latitude), Double.parseDouble(focus_longitude));

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
}