package gov.tak.platform.marshal;

import gov.tak.api.marshal.IMarshalService;

final class PlatformMarshals {

    private PlatformMarshals() {}

    public static void registerAll(IMarshalService svc) {
        svc.registerMarshal(new MotionEventMarshal.Portable(), android.view.MotionEvent.class, gov.tak.platform.ui.MotionEvent.class);
        svc.registerMarshal(new MotionEventMarshal.Platform(), gov.tak.platform.ui.MotionEvent.class, android.view.MotionEvent.class);
    }
}
