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
        instance.test();
        BaseClass.Inner inner = (BaseClass.Inner) access.invoke(instance, "newInner");

        access1.newInstance(instance);
        access = ClassAccess.access(BaseClass.StaticInner.class);
        access.newInstance(instance);
        access = ClassAccess.access(BaseClass.Inner.DeeperInner.class);
        access.newInstance(inner);
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

    static class baseClass1 {
        public void test() {}
    }

    class BaseClass {
        public void test() {}

        Inner newInner() {
            return new Inner();
        }

        class Inner {
            class DeeperInner {}
        }

        class StaticInner {}
    }
}

