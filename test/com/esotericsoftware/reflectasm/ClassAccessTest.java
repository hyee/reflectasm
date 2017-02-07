package com.esotericsoftware.reflectasm;

import junit.framework.TestCase;
import test.Many;

/**
 * Created by Will on 2017/2/6.
 */
public class ClassAccessTest extends TestCase {
    public void testBase() {
        ClassAccess<Many> access0 = ClassAccess.access(Many.class, ".", "");
        Many many = access0.newInstance();
        access0.set(many, "x295", 123);
        assertEquals(123, access0.get(many, "x295"));

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

    class BaseClass {
        public void test() {}

        Inner newInner() {
            return new Inner();
        }

        class Inner {
            class DeeperInner {}
        }

        class StaticInner {

        }

        ;
    }

    static class baseClass1 {
        public void test() {}
    }
}

