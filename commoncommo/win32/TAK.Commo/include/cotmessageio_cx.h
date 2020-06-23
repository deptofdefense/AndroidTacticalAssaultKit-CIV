#pragma once

namespace TAK {
    namespace Commo {
    
        public ref class CoTPointData sealed {
        public:
            // Can be used for hae, ce, or le to indicate a lack of value
            static property double NoValue {double get() { return 9999999.0; }}

            CoTPointData(double lat, double lon, double hae,
                double ce, double le) : _lat(lat), _lon(lon),
                _hae(hae), _ce(ce), _le(le)
            {
            };

            CoTPointData(CoTPointData^ rhs)
                : _lat(rhs->Lat), _lon(rhs->Lon), 
                _hae(rhs->Hae), _ce(rhs->Ce), _le(rhs->Le)
            {
            };

            property double Lat {double get() { return _lat; }}
            property double Lon {double get() { return _lon; }}
            property double Hae {double get() { return _hae; }}
            property double Ce {double get() { return _ce; }}
            property double Le {double get() { return _le; }}

        private:
            double _lat;
            double _lon;
            double _hae;
            double _ce;
            double _le;
        };
        
        public enum class CoTMessageType {
            SituationalAwareness,
            Chat,
        };
        
        public interface class ICoTMessageListener
        {
        public:
            virtual void cotMessageReceived(Platform::String ^cotMessage);
        };

    }
}