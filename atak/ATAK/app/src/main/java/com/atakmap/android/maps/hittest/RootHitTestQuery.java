
package com.atakmap.android.maps.hittest;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestControl;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.Layer2;
import com.atakmap.util.Collections2;
import com.atakmap.util.Visitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An implementation of {@link DeepHitTestQuery} that utilizes
 * {@link HitTestControl} and other {@link DeepHitTestQuery}
 * sub-queries within a GL event.
 */
public class RootHitTestQuery implements DeepHitTestQuery {

    @Override
    public final SortedSet<MapItem> deepHitTest(MapView mapView,
            HitTestQueryParameters params) {
        return deepHitTest(mapView, params, null);
    }

    /**
     * Perform a deep hit test for map items with optional sub-queries
     *
     * @param mapView Map view
     * @param params Hit test query parameters
     * @param subQueries Additional hit test queries to run within the renderer thread
     * @return List of hit map items sorted by z-order
     */
    public final SortedSet<MapItem> deepHitTest(final MapView mapView,
            final HitTestQueryParameters params,
            final Collection<? extends DeepHitTestQuery> subQueries) {

        // Only map item results supported
        if (!params.acceptsResult(MapItem.class))
            return null;

        final SortedSet<MapItem> results = new TreeSet<>(
                MapItem.ZORDER_HITTEST_COMPARATOR);

        final MapRenderer3 renderer = mapView.getRenderer3();
        renderer.visitControls(
                new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                    @Override
                    public void visit(
                            Iterator<Map.Entry<Layer2, Collection<MapControl>>> iter) {

                        // Map all hit test controls to layers which support them
                        // This allows sub-classes to query controls without using the
                        // map control sync lock (which results in deadlock on GL thread)
                        Map<Layer2, Collection<HitTestControl>> controls = new IdentityHashMap<>();
                        while (iter.hasNext()) {
                            Map.Entry<Layer2, Collection<MapControl>> entry = iter
                                    .next();
                            Collection<HitTestControl> hitControls = Collections2
                                    .newIdentityHashSet();
                            for (MapControl ctrl : entry.getValue()) {
                                if (ctrl instanceof HitTestControl)
                                    hitControls.add((HitTestControl) ctrl);
                            }
                            if (!hitControls.isEmpty())
                                controls.put(entry.getKey(), hitControls);
                        }

                        // Queue hit test on renderer thread
                        renderer.getRenderContext()
                                .queueEventSync(new Runnable() {
                                    @Override
                                    public void run() {
                                        hitTestImpl(mapView, params, controls,
                                                subQueries, results);
                                    }
                                });
                    }
                });

        return results;
    }

    private void hitTestImpl(final MapView mapView,
            final HitTestQueryParameters params,
            final Map<Layer2, Collection<HitTestControl>> controls,
            final Collection<? extends DeepHitTestQuery> subQueries,
            final SortedSet<MapItem> results) {

        MapRenderer3 renderer = mapView.getRenderer3();

        // Instantiate geo point on GL thread (if one isn't already set)
        params.initGeoPoint(renderer);

        // Execute hit test controls
        for (Collection<HitTestControl> ctrls : controls.values()) {
            for (HitTestControl c : ctrls) {
                if (params.hitLimit(results))
                    return;
                visitControl(renderer, params, c, results);
            }
        }

        // Run additional queries if available
        if (subQueries != null) {
            for (DeepHitTestQuery query : subQueries) {
                if (params.hitLimit(results))
                    return;
                SortedSet<MapItem> res;
                if (query instanceof DeepHitTestControlQuery)
                    res = ((DeepHitTestControlQuery) query).deepHitTest(
                            mapView, params, controls);
                else
                    res = query.deepHitTest(mapView, params);
                if (res != null)
                    results.addAll(res);
            }
        }
    }

    private void visitControl(final MapRenderer3 renderer,
            final HitTestQueryParameters params,
            final HitTestControl ctrl,
            final Collection<MapItem> results) {

        // Begin hit test
        List<HitTestResult> hit = new ArrayList<>();
        ctrl.hitTest(renderer, params, hit);

        // Convert to map items
        for (HitTestResult t : hit) {
            if (t.subject instanceof MapItem) {
                MapItem mi = (MapItem) t.subject;
                mi.setMetaString("hit_type", t.type.toString());
                mi.setMetaInteger("hit_index", t.index);
                mi.setMetaInteger("hit_count", t.count);
                mi.setClickPoint(t.point);
                results.add(mi);
            }
        }
    }
}
