package gov.tak.platform.marshal;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MarshalManagerTest {
    @Test
    public void serviceNotNull() {
        Assert.assertNotNull(MarshalManager.service());
    }
}
