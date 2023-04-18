package gov.tak.test;

import com.atakmap.map.EngineLibrary;

public abstract class KernelJniTest
{
    protected KernelJniTest()
    {
        EngineLibrary.initialize();
    }
}
