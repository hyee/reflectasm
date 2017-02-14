//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.MethodAccessTest.EmptyClass;

import java.lang.invoke.MethodHandle;
import java.util.EnumMap;

public final class test implements Accessor<EmptyClass> {
    Class<? super EmptyClass> super_;
    static final ClassInfo<EmptyClass> classInfo;
    static MethodHandle[][] methodHandles;

    public test castTo(Class<? super EmptyClass> clz) {
        return new test();
    };

    public test() {
    }

    static void methodNames() {
        classInfo.methodNames = new String[0];
    }

    static {
        methodHandles = new MethodHandle[3][12345];
        classInfo = new ClassInfo();
        methodNames();
        classInfo.methodParamTypes = new Class[0][];
        classInfo.returnTypes = new Class[0];
        classInfo.methodModifiers = new Integer[0];
        classInfo.methodDescs = new String[0][];
        classInfo.fieldNames = new String[0];
        classInfo.fieldTypes = new Class[0];
        classInfo.fieldModifiers = new Integer[0];
        classInfo.fieldDescs = new String[0][];
        classInfo.constructorParamTypes = new Class[][]{new Class[0]};
        classInfo.constructorModifiers = new Integer[]{Integer.valueOf(1)};
        classInfo.constructorDescs = new String[]{"()V"};
        classInfo.baseClass = EmptyClass.class;
        classInfo.isNonStaticMemberClass = false;
        classInfo.bucket = 8;
        ClassAccess.buildIndex(classInfo);
    }

    public ClassInfo<EmptyClass> getInfo() {
        return classInfo;
    }

    @Override
    public MethodHandle[][] getMethodHandles() {
        return methodHandles;
    }

    public final <T, V> T invokeWithIndex(EmptyClass var1, int var2, V... var3) {
        throw new IllegalArgumentException("Method not found: " + var2);
    }

    public final <T> T get(EmptyClass var1, int var2) {
        throw new IllegalArgumentException("Field not found: " + var2);
    }

    public final <T, V> void set(EmptyClass var1, int var2, V var3) {
        throw new IllegalArgumentException("Field not found: " + var2);
    }

    public final <T> EmptyClass newInstanceWithIndex(int var1, T... var2) {
        switch (var1) {
            case 0:
                return new EmptyClass();
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public final EmptyClass newInstance() {
        return new EmptyClass();
    }
}
