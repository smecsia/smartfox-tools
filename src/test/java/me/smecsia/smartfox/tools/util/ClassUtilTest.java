package me.smecsia.smartfox.tools.util;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static me.smecsia.smartfox.tools.util.ClassUtil.invokeAnyMethod;
import static me.smecsia.smartfox.tools.util.ClassUtil.setPrivateField;
import static me.smecsia.smartfox.tools.util.ExceptionUtil.formatStackTrace;

/**
 * @author Ilya Sadykov
 *         Date: 19.09.12
 *         Time: 13:53
 *         Copyright (c) 2012 i-Free. All Rights Reserved.
 */
public class ClassUtilTest {

    @Test
    public void testInvokeAnyMethod() {
        TestClass testObject = new TestClass();

        try {
            invokeAnyMethod(testObject, "someMethod", "arg0", 10);
            String res = (String) invokeAnyMethod(testObject, "someMethod", "arg0", 10, new Person("Mike"));
            assertEquals("returnedValue", res);
            invokeAnyMethod(testObject, "somePublicMethod", new Class[]{Integer.TYPE}, 10);
            setPrivateField(testObject, "privateField", "set");
            setPrivateField(TestClass.class, "privateStatic", "set");
            assertEquals("set", testObject.privateField);
            assertEquals("set", TestClass.privateStatic);
        } catch (Exception e) {
            fail("Method call must not throw an exception! \n" + formatStackTrace(e));
        }
    }

    private static class TestClass {
        private static String privateStatic = "notSet";
        private String privateField = "notSet";

        private void someMethod(String arg1, Integer arg2) {
            System.out.println("someMethod(" + arg1 + "," + arg2 + ")");
        }

        private String someMethod(String arg1, Integer arg2, Person arg3) {
            System.out.println("someMethod(" + arg1 + "," + arg2 + "," + arg3 + ")");
            return "returnedValue";
        }

        public void somePublicMethod(int val) {
            System.out.println("somePublicMethod(" + val + ")");
        }
    }

    private static class Person {
        public String name;

        private Person(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Person[name=" + name + "]";
        }
    }
}
