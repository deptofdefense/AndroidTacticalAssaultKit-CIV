package com.atakmap.map.layer.control;

import java.util.Collection;

public interface Controls {
    public <T> T getControl(Class<T> controlClazz);
    public void getControls(Collection<Object> controls);
}
