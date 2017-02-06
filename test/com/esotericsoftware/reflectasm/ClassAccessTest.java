package com.esotericsoftware.reflectasm;

import junit.framework.TestCase;

/**
 * Created by Will on 2017/2/6.
 */
public class ClassAccessTest extends TestCase {
    public void testBase() {
        ClassAccess<BaseClass> access = ClassAccess.get(BaseClass.class, ".", "");
        ClassAccess access1 = ClassAccess.get(BaseClass.Inner.class);
        try {
            BaseClass instance = access.newInstance();
            fail();
        } catch (IllegalArgumentException e) {}
        BaseClass instance = access.newInstance(this);
        instance.test();
        BaseClass.Inner inner = (BaseClass.Inner) access.invoke(instance, "newInner");

        access1.newInstance(instance);
        access = ClassAccess.get(BaseClass.StaticInner.class);
        access.newInstance(instance);
        access = ClassAccess.get(BaseClass.Inner.DeeperInner.class);
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

