package gov.tak.platform.marshal;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AbstractMarshalTest {
    @Test
    public void constructorRoundtrip() {
        AbstractMarshal m = Mockito.mock(AbstractMarshal.class, Mockito.withSettings()
                .useConstructor(String.class, Integer.class)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        Assert.assertEquals(String.class, m._inType);
        Assert.assertEquals(Integer.class, m._outType);
    }

    @Test
    public void marshalNull() {
        AbstractMarshal m = Mockito.mock(AbstractMarshal.class, Mockito.withSettings()
                .useConstructor(String.class, Integer.class)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        Assert.assertNull(m.<String, Integer>marshal(null));
    }

    @Test
    public void marshalDerivedRoundtrips() {
        AbstractMarshal m = Mockito.mock(AbstractMarshal.class, Mockito.withSettings()
                .useConstructor(String.class, CharSequence.class)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        final String input = "somestring";
        Assert.assertSame(input, m.<String, CharSequence>marshal(input));
    }

    @Test
    public void marshalImpl() {
        final String input = "input";
        final Integer output = new Integer(1);

        AbstractMarshal m = Mockito.mock(AbstractMarshal.class, Mockito.withSettings()
                .useConstructor(String.class, Integer.class)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        Mockito.when(m.marshalImpl(input)).thenReturn(output);

        Assert.assertSame(output, m.marshal(input));
    }
}
