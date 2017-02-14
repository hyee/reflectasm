package com.esotericsoftware.reflectasm;

import junit.framework.TestCase;
import test.Many;
import test.TestObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Will on 2017/2/6.
 */
public class ClassAccessTest extends TestCase {
    public void testBigClass() throws ClassNotFoundException {
        Class clz=Class.forName("oracle.jdbc.driver.T4CConnection");
        ClassAccess.access(clz,".");
    }
    public void testCase1() {
        //Generic style
        ClassAccess.access(TestObject.class, ".");
        ClassAccess<Many> access0 = ClassAccess.access(Many.class);
        Many many = access0.newInstance();
        access0.set(many, "x295", 123);
        int value = access0.get(many, "x295");
        assertEquals(123, value);

        //Non-generic style
        ClassAccess access5 = ClassAccess.access(Many.class);
        Object many1 = access5.newInstance();
        access5.set(many1, "x295", 456);
        value = (int) access5.get(many1, "x295");
        assertEquals(456, value);

        //Cast back to generic style
        ClassAccess<Many> access6 = access5;
        Many many2 = (Many) many1;
        access6.set(many2, "x295", 789);
        value = access6.get(many2, "x295");
        assertEquals(789, value);

        ClassAccess<BaseClass> access = ClassAccess.access(BaseClass.class, ".", "");
        ClassAccess access1 = ClassAccess.access(BaseClass.Inner.class);
        try {
            BaseClass instance = access.newInstance();
            fail();
        } catch (IllegalArgumentException e) {}
        BaseClass instance = access.newInstance(this);
        instance.test0();
        BaseClass.Inner inner = (BaseClass.Inner) access.invoke(instance, "newInner");
        access1.newInstance(instance);
        access = ClassAccess.access(BaseClass.StaticInner.class);
        access.newInstance(instance);
        access = ClassAccess.access(BaseClass.Inner.DeeperInner.class);
        access.newInstance(inner);
    }

    public void testOverload0(ClassAccess... accesses) {
        ClassAccess<BaseClass> access1 = ClassAccess.access(BaseClass.class);
        ClassAccess access2;
        if (accesses.length == 0) access2 = ClassAccess.access(ChildClass.class, ".");
        else access2 = accesses[0];

        ChildClass child = (ChildClass) access2.newInstance(this);
        assertEquals("test10", access2.invoke(child, "test0"));
        int index = access2.indexOfMethod("test1");
        assertEquals("test11", access2.invoke(child, "test1"));
        assertEquals("test02", access2.invoke(child, "test2"));
        assertEquals("test01", access1.invoke(child, "test1"));
        assertEquals(1, (int) access1.get(child, "x"));
        assertEquals(3, access2.get(child, "x"));
        assertEquals(4, access2.get(child, "y"));
        assertEquals(5, access2.get(child, "z"));
        //assertEquals(6, access2.get(child, "o"));
        //Invoke the overloaded parts of BaseClass
        int fieldIndex = access2.indexOfField(BaseClass.class, "x");
        int methodIndex = access2.indexOfMethod(BaseClass.class, "test1");
        assertEquals("test01", access2.invokeWithIndex(child, methodIndex));
        assertEquals(1, access2.get(child, fieldIndex));
        if ((Boolean) access2.isInvokeWithMethodHandle.get() == false) {
            access2.set(child, fieldIndex, 9);
            assertEquals(9, access2.get(child, fieldIndex));
            assertEquals(3, access2.get(child, "x"));
            assertEquals(6, access2.get(child, "o"));
        }
    }

    public void testOverload() {
        testOverload0();
    }

    public void testOverloadWithLambda() throws Throwable {
        ClassAccess access2 = ClassAccess.access(ChildClass.class, ".");
        access2.isInvokeWithMethodHandle.set(true);
        testOverload0(access2);
    }

    public void testCase2() throws InterruptedException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        final int count = 100;
        final int rounds = 300;
        ExecutorService pool = Executors.newFixedThreadPool(count);
        final CountDownLatch latch = new CountDownLatch(count);
        Runnable R = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < rounds; i++) ClassAccess.access(Many.class).newInstance();
                latch.countDown();
            }
        };
        Long s = System.nanoTime();
        for (int i = 0; i < count; i++) {
            pool.submit(R);
        }

        latch.await();
        assertEquals(1, ClassAccess.activeAccessClassLoaders());
        System.out.println(ClassAccess.totalAccesses + " invokes from ClassAccess.access() and " + ClassAccess.cacheHits + " hits from cache");
        System.out.println("Creating " + (count * rounds) + " same proxies with parallel 100 takes " + String.format("%.3f", (System.nanoTime() - s) / 1e6) + " ms.");

        ClassAccess.totalAccesses = 0;
        ClassAccess.cacheHits = 0;
        s = System.nanoTime();
        for (int i = 0; i < rounds * count; i++) ClassAccess.access(Many.class).newInstance();
        System.out.println(ClassAccess.totalAccesses + " invokes from ClassAccess.access() and " + ClassAccess.cacheHits + " hits from cache");
        System.out.println("Creating " + (count * rounds) + " same proxies#1 in serial mode takes " + String.format("%.3f", (System.nanoTime() - s) / 1e6) + " ms.");

        ClassAccess.totalAccesses = 0;
        ClassAccess.cacheHits = 0;
        ClassAccess.IS_CACHED = false;
        s = System.nanoTime();
        for (int i = 0; i < rounds * count; i++) ClassAccess.access(Many.class).newInstance();
        System.out.println(ClassAccess.totalAccesses + " invokes from ClassAccess.access() and " + ClassAccess.loaderHits + " hits from loader");
        System.out.println("Creating " + (count * rounds) + " same proxies#1 in serial mode takes " + String.format("%.3f", (System.nanoTime() - s) / 1e6) + " ms.");

        ClassAccess.totalAccesses = 0;
        ClassAccess.cacheHits = 0;
        ClassAccess.IS_CACHED = true;
        s = System.nanoTime();
        for (int i = 0; i < rounds * count; i++) {
            ClassLoader testClassLoader = new ClassLoaderTest.TestClassLoader1();
            Class testClass = testClassLoader.loadClass(Many.class.getName());
            ClassAccess<Many> access = ClassAccess.access(Many.class);
            Many many = access.newInstance();
            access.set(many, "x1", i);
        }
        System.out.println(ClassAccess.totalAccesses + " invokes from ClassAccess.access() and " + ClassAccess.cacheHits + " hits from cache");
        System.out.println("Creating " + (count * rounds) + " same proxies#2 in serial mode takes " + String.format("%.3f", (System.nanoTime() - s) / 1e6) + " ms.");
    }

    public void testCase3() throws Exception {
        {
            ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
            TestObject obj;
            // Construction
            obj = access.newInstance();
            obj = access.newInstance(1, 2, 3, 4);

            // Set+Get field
            access.set(null, "fs", 1); // static field
            System.out.println((String) access.get(null, "fs"));

            access.set(obj, "fd", 2);
            System.out.println(access.get(obj, "fd").toString());

            // Method invoke
            access.invoke(null, "func1", "a"); //static call
            System.out.println((String) access.invoke(obj, "func2", 1, 2, 3, 4));
        }

        {
            ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
            TestObject obj;
            //Identify the indexes for further use
            int newIndex = access.indexOfConstructor(int.class, Double.class, String.class, long.class);
            int fieldIndex = access.indexOfField("fd");
            int methodIndex = access.indexOfMethod("func1", String.class);
            //Now use the index to access object in loop or other part
            for (int i = 0; i < 100; i++) {
                obj = access.newInstanceWithIndex(newIndex, 1, 2, 3, 4);
                access.set(obj, fieldIndex, 123);
                String result = access.invokeWithIndex(null, methodIndex, "x");
            }
        }

        {
            ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
            TestObject obj;
            //Identify the indexes for further use
            int newIndex = access.indexOfConstructor(int.class, Double.class, String.class, long.class);
            int fieldIndex = access.indexOfField("fd");
            int methodIndex = access.indexOfMethod("func1", String.class);
            //Now use the index to access object in loop or other part
            for (int i = 0; i < 100; i++) {
                obj = access.accessor.newInstanceWithIndex(newIndex, 1, Double.valueOf(2), "3", 4L);
                access.accessor.set(obj, fieldIndex, Double.valueOf(123));
                String result = access.accessor.invokeWithIndex(null, methodIndex, "x");
            }
        }
    }

    interface BaseInterface {
        int o = 6;

        String test0();
    }

    class BaseClass implements BaseInterface {
        public int x = 1;
        int y = 2;
        int z = 5;

        public String test0() {return "test00";}

        private String test1() {return "test01";}

        private String test2() {return "test02";}

        Inner newInner() {
            return new Inner();
        }

        class Inner {
            class DeeperInner {}
        }

        class StaticInner {}
    }

    class ChildClass extends BaseClass {
        public int x = 3;
        int y = 4;

        public String test0() {return "test10";}

        private String test1() {
            return "test11";
        }
    }
}

