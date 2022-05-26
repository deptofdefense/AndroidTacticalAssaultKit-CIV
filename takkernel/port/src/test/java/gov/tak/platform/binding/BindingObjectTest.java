package gov.tak.platform.binding;

import gov.tak.api.binding.IPropertyBindingObject;
import gov.tak.api.util.Visitor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BindingObjectTest {

    static class TestBindingObject extends AbstractPropertyBindingObject<TestBindingObject> {

        private static final PropertyInfo PROPERTY_TEST = new PropertyInfo("test", String.class);

        private String testValue = "testValue";

        @Override
        public Object getPropertyValue(String propertyName) {
            if (PROPERTY_TEST.hasName(propertyName))
                return testValue;
            return null;
        }

        @Override
        public void visitPropertyInfos(Visitor<PropertyInfo> visitor) {
            visitor.visit(PROPERTY_TEST);
        }

        @Override
        protected void applyPropertyChange(String propertyName, Object newValue) {
            if (PROPERTY_TEST.canAssign(propertyName, newValue.getClass())) {
                this.testValue = (String)newValue;
            } else {
                super.applyPropertyChange(propertyName, newValue);
            }
        }
    }

    @Test
    public void listenerOnChange() {

        boolean[] listenerCalled = {false};

        TestBindingObject tbo = new TestBindingObject();
        tbo.testValue = "initial";
        tbo.addOnPropertyValueChangedListener(new IPropertyBindingObject.OnPropertyValueChangedListener<TestBindingObject>() {
            @Override
            public void onPropertyValueChanged(TestBindingObject bindingObject, String propertyName, Object oldValue, Object newValue) {
                Assert.assertEquals(oldValue, "initial");
                Assert.assertEquals(newValue, "changed");
                listenerCalled[0] = true;
            }
        });
        tbo.setPropertyValue(TestBindingObject.PROPERTY_TEST.getName(), "changed");

        Assert.assertTrue(listenerCalled[0]);
    }
}
