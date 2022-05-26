package gov.tak.platform.marshal;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationMode;

import java.util.Collection;

import gov.tak.api.marshal.IMarshal;

@RunWith(MockitoJUnitRunner.class)
public class MarshalServiceTest {
    @Test
    public void registerPing() {
        MarshalService service = new MarshalService();

        final Integer input = new Integer(1);
        final String output = "output";

        IMarshal m = Mockito.mock(IMarshal.class);
        Mockito.when(m.marshal(input)).thenReturn(output);

        Assert.assertNull(service.marshal(input, Integer.class, String.class));
        service.registerMarshal(m, Integer.class, String.class);

        Assert.assertSame(output, service.marshal(input, Integer.class, String.class));
    }

    @Test
    public void registerMiss() {
        MarshalService service = new MarshalService();

        final Integer input = new Integer(1);

        IMarshal m = Mockito.mock(IMarshal.class);

        Assert.assertNull(service.marshal(input, Integer.class, String.class));
        service.registerMarshal(m, Integer.class, String.class);

        Assert.assertNull(service.marshal(input, Integer.class, Collection.class));

        Mockito.verify(m, Mockito.never()).marshal(input);
    }

    @Test
    public void unregisteredMiss() {
        MarshalService service = new MarshalService();

        final Integer input = new Integer(1);

        IMarshal m = Mockito.mock(IMarshal.class);

        Assert.assertNull(service.marshal(input, Integer.class, String.class));
        service.registerMarshal(m, Integer.class, String.class);
        service.unregisterMarshal(m);
        Assert.assertNull(service.marshal(input, Integer.class, String.class));

        Mockito.verify(m, Mockito.never()).marshal(input);
    }

    @Test
    public void marshalDoesNotSupport() {
        MarshalService service = new MarshalService();

        final Integer input = new Integer(1);

        IMarshal m = Mockito.mock(IMarshal.class);
        Mockito.when(m.marshal(input)).thenReturn(null);

        Assert.assertNull(service.marshal(input, Integer.class, String.class));
        service.registerMarshal(m, Integer.class, String.class);

        Assert.assertNull(service.marshal(input, Integer.class, String.class));

        Mockito.verify(m, Mockito.atLeastOnce()).marshal(input);
    }
}
