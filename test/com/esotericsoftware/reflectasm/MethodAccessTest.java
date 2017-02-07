package com.esotericsoftware.reflectasm;

import junit.framework.TestCase;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MethodAccessTest extends TestCase {
    public void abc(char c) {
        System.out.println(c);
    }

    public void testInvoke() {
        MethodAccess<SomeClass> access1 = MethodAccess.access(ConcurrentHashMap.class, ".");

        MethodAccess<SomeClass> access = MethodAccess.access(SomeClass.class, ".");
        SomeClass someObject = access.console.newInstance();
        Object value;
        value = access.invoke(someObject, "test");
        value = access.invoke(someObject, "getName");
        assertEquals(null, value);
        value = access.invoke(someObject, "setName", "sweet");
        assertEquals(null, value);
        value = access.invoke(someObject, "getName");
        assertEquals("sweet", value);
        value = access.invoke(someObject, "setName", (Object) null);
        assertEquals(null, value);
        value = access.invoke(someObject, "getName");
        assertEquals(null, value);

        value = access.invoke(someObject, "getIntValue");
        assertEquals(0, value);
        value = access.invoke(someObject, "setValue", 1234, false);
        assertEquals(null, value);
        value = access.invoke(someObject, "getIntValue");
        assertEquals(1234, value);
        value = access.invoke(someObject, "methodWithManyArguments", 6, 2f, null, null);
        assertEquals("test0", value);
        value = access.invoke(someObject, "methodWithManyArguments", "1", 2f, new int[]{3, 4}, 4.2f, null, true);
        assertEquals("0", value);
        value = access.invoke(someObject, "methodWithManyArguments", "1", 2f, new int[]{3, 4}, 4.2f, null, true, new int[]{1, 2, 3});
        value = access.invoke(someObject, "methodWithManyArguments", "1", 2f, new int[]{3, 4}, 4.2f, null, true, 1, 2, 3);
        assertEquals(Arrays.toString(new int[]{1, 2, 3}), value);
        value = access.invoke(null, "staticMethod", "moo", 1234);
        assertEquals("meow! moo, 1234", value);
        //int methodWithVarArgs(char,Double,Long,Integer[])
        value = access.invoke(someObject, "methodWithVarArgs", 1, 2, 3);
        assertEquals(0, value);
        value = access.invoke(someObject, "methodWithVarArgs", 'x', 2, 3, 4);
        assertEquals(1, value);
        value = access.invoke(someObject, "methodWithVarArgs", "B", null, 3, 4, null);
        assertEquals(2, value);
        value = access.invoke(someObject, "methodWithVarArgs", new BigDecimal(1), 2, new BigDecimal(20), new Integer[2]);
        assertEquals(2, value);
    }

    public void testEmptyClass() {
        MethodAccess<EmptyClass> access = MethodAccess.access(EmptyClass.class, ".");
        try {
            access.getIndex("name");
            fail();
        } catch (IllegalArgumentException expected) {
            // expected.printStackTrace();
        }
        try {
            access.getIndex("name", String.class);
            fail();
        } catch (IllegalArgumentException expected) {
            // expected.printStackTrace();
        }
        try {
            access.invoke(new EmptyClass(), "meow", "moo");
            fail();
        } catch (IllegalArgumentException expected) {
            // expected.printStackTrace();
        }
        try {
            access.invokeWithIndex(new EmptyClass(), 0);
            fail();
        } catch (IllegalArgumentException expected) {
            // expected.printStackTrace();
        }
        try {
            access.invokeWithIndex(new EmptyClass(), 0, "moo");
            fail();
        } catch (IllegalArgumentException expected) {
            // expected.printStackTrace();
        }
    }

    public void testInvokeInterface() {
        MethodAccess access = MethodAccess.access(ConcurrentMap.class);
        ConcurrentHashMap<String, String> someMap = new ConcurrentHashMap<String, String>();
        someMap.put("first", "one");
        someMap.put("second", "two");
        Object value;

        // invokeWithIndex a method declared directly in the ConcurrentMap interface
        value = access.invoke(someMap, "replace", "first", "foo");
        assertEquals("one", value);
        // invokeWithIndex a method declared in the Map superinterface
        value = access.invoke(someMap, "size");
        assertEquals(someMap.size(), value);
    }

    static public class EmptyClass {

    }

    static public class baseClass extends EmptyClass {
        public void test() {}

    }

    static public class SomeClass extends baseClass {
        String name;
        private int intValue;
        static boolean bu;
        public static boolean x;

        public SomeClass() {

        }

        public SomeClass(int x, int y) {

        }

        public SomeClass(String x) {

        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setValue(int intValue, Boolean bu) {
            this.intValue = intValue;
            this.bu = bu;
        }

        public String methodWithManyArguments(int i, float f, Integer[] I, int c1) {
            return "test0";
        }

        public String methodWithManyArguments(int i, float f, Integer[] I, SomeClass[] c1) {
            return "test0";
        }

        public String methodWithManyArguments(int i, float f, Integer[] I, Float F, SomeClass[] c1, Boolean x, int... y) {
            if (y.length == 0) return "0";
            return Arrays.toString(y);
        }

        public int methodWithVarArgs(char a, Double b, Long c, Integer... d) {
            return d == null ? 0 : d.length;
        }

        static public String staticMethod(String a, int b) {
            return "meow! " + a + ", " + b;
        }
    }
}
