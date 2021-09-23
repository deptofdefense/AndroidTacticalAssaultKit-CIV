package com.atakmap.map.layer.feature.gpkg;


import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.spi.InteractivePriorityServiceProviderRegistry2;


public final
class GeoPackageSchemaHandlerRegistry
  {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================


    public static synchronized
    GeoPackageSchemaHandler
    getHandler (GeoPackage geoPackage)
      {
        if(registry.isSupported(geoPackage, 1))
          {
            return registry.create (geoPackage);
          }
        return new DefaultGeoPackageSchemaHandler2 (geoPackage);
      }

    public static
    void
    register (GeoPackageSchemaHandler.Factory factory)
      { registry.register (factory, factory.getPriority()); }

    public static synchronized
    void
    unregister (GeoPackageSchemaHandler.Factory factory)
      { registry.unregister (factory); }


    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================


    private
    GeoPackageSchemaHandlerRegistry ()
      { }


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    private static
    InteractivePriorityServiceProviderRegistry2<GeoPackageSchemaHandler, GeoPackage, GeoPackageSchemaHandler.Factory> registry
        = new InteractivePriorityServiceProviderRegistry2<GeoPackageSchemaHandler, GeoPackage, GeoPackageSchemaHandler.Factory> ();
  }
