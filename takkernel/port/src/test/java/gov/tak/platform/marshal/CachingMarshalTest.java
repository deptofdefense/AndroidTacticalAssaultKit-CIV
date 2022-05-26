package gov.tak.platform.marshal;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import gov.tak.api.marshal.IMarshal;

@RunWith(MockitoJUnitRunner.class)
public class CachingMarshalTest {
    @Test
    public void marshalNull() {
        IMarshal impl = Mockito.mock(IMarshal.class);
        CachingMarshal<String, Integer> cm = new CachingMarshal<>(impl);

        Assert.assertNull(cm.<String, Integer>marshal(null));

        Mockito.verify(impl, Mockito.never()).marshal(null);
    }

    @Test
    public void marshalWithData() {
        final String input = "input";
        final Integer output = new Integer(1);

        IMarshal impl = Mockito.mock(IMarshal.class);
        Mockito.when(impl.marshal(input)).thenReturn(output);

        CachingMarshal<String, Integer> cm = new CachingMarshal<>(impl);

        // marshal the input, expect valid output and the underlying impl to have been invoked once
        Mockito.verify(impl, Mockito.never()).marshal(null);
        Assert.assertSame(output, cm.<Integer, String>marshal(input));
        Mockito.verify(impl, Mockito.times(1)).marshal(input);

        // marshal the input, expect valid output and cache hit
        Assert.assertSame(output, cm.<Integer, String>marshal(input));
        Mockito.verify(impl, Mockito.times(1)).marshal(input);
    }
}
