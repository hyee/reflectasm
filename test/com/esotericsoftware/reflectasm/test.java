//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.MethodAccessTest.SomeClass;
import com.esotericsoftware.reflectasm.MethodAccessTest.baseClass;

import java.lang.invoke.MethodHandle;

public final class test implements Accessor<SomeClass> {
    static MethodHandle[][] methodHandles = new MethodHandle[3][10];
    static final ClassInfo<SomeClass> classInfo = new ClassInfo();

    public test() {
    }

    public MethodHandle[][] getMethodHandles() {
        return methodHandles;
    }

    public ClassInfo<SomeClass> getInfo() {
        return classInfo;
    }

    static {
        classInfo.baseClass = SomeClass.class;
        classInfo.isNonStaticMemberClass = false;
        classInfo.bucket = 7;
        methodNames();
        methodParamTypes();
        returnTypes();
        methodModifiers();
        methodDescs();
        fieldNames();
        fieldTypes();
        fieldModifiers();
        fieldDescs();
        constructorParamTypes();
        constructorModifiers();
        constructorDescs();
        ClassAccess.buildIndex(classInfo);
    }

    static void methodNames() {
        classInfo.methodNames = new String[]{"getName", "setName", "setValue", "methodWithManyArguments", "methodWithManyArguments", "methodWithManyArguments", "methodWithVarArgs", "getIntValue", "staticMethod", "test"};
    }

    static void methodParamTypes() {
        classInfo.methodParamTypes = new Class[][]{new Class[0], {String.class}, {Integer.TYPE, Boolean.class}, {Integer.TYPE, Float.TYPE, Integer[].class, Integer.TYPE}, {Integer.TYPE, Float.TYPE, Integer[].class, SomeClass[].class}, {Integer.TYPE, Float.TYPE, Integer[].class, Float.class, SomeClass[].class, Boolean.class, int[].class}, {Character.TYPE, Double.class, Long.class, Integer[].class}, new Class[0], {String.class, Integer.TYPE}, new Class[0]};
    }

    static void returnTypes() {
        classInfo.returnTypes = new Class[]{String.class, Void.TYPE, Void.TYPE, String.class, String.class, String.class, Integer.TYPE, Integer.TYPE, String.class, Void.TYPE, null, null, null, null, null, null, null, null, null, baseClass.class};
    }

    static void methodModifiers() {
        classInfo.methodModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(262273), Integer.valueOf(262273), Integer.valueOf(1), Integer.valueOf(9), Integer.valueOf(1)};
    }

    static void methodDescs() {
        classInfo.methodDescs = new String[][]{{"getName", "()Ljava/lang/String;"}, {"setName", "(Ljava/lang/String;)V"}, {"setValue", "(ILjava/lang/Boolean;)V"}, {"methodWithManyArguments", "(IF[Ljava/lang/Integer;I)Ljava/lang/String;"}, {"methodWithManyArguments", "(IF[Ljava/lang/Integer;[Lcom/esotericsoftware/reflectasm/MethodAccessTest$SomeClass;)Ljava/lang/String;"}, {"methodWithManyArguments", "(IF[Ljava/lang/Integer;Ljava/lang/Float;[Lcom/esotericsoftware/reflectasm/MethodAccessTest$SomeClass;Ljava/lang/Boolean;[I)Ljava/lang/String;"}, {"methodWithVarArgs", "(CLjava/lang/Double;Ljava/lang/Long;[Ljava/lang/Integer;)I"}, {"getIntValue", "()I"}, {"staticMethod", "(Ljava/lang/String;I)Ljava/lang/String;"}, {"test", "()V"}};
    }

    static void fieldNames() {
        classInfo.fieldNames = new String[]{"x", "bu", "name", "intValue"};
    }

    static void fieldTypes() {
        classInfo.fieldTypes = new Class[]{Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE, null, null, null, null};
    }

    static void fieldModifiers() {
        classInfo.fieldModifiers = new Integer[]{Integer.valueOf(9), Integer.valueOf(8), Integer.valueOf(0), Integer.valueOf(2)};
    }

    static void fieldDescs() {
        classInfo.fieldDescs = new String[][]{{"x", "Z"}, {"bu", "Z"}, {"name", "Ljava/lang/String;"}, {"intValue", "I"}};
    }

    static void constructorParamTypes() {
        classInfo.constructorParamTypes = new Class[][]{{String.class}, {Integer.TYPE, Integer.TYPE}, new Class[0]};
    }

    static void constructorModifiers() {
        classInfo.constructorModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1)};
    }

    static void constructorDescs() {
        classInfo.constructorDescs = new String[]{"(Ljava/lang/String;)V", "(II)V", "()V"};
    }

    public final <T, V> T invokeWithIndex(SomeClass var1, int var2, V... var3) {
        switch (var2) {
            case 0:
                return (T) var1.getName();
            case 1:
                var1.setName((String) var3[0]);
                return null;
            case 2:
                var1.setValue(((Integer) var3[0]).intValue(), (Boolean) var3[1]);
                return null;
            case 3:
                return (T) var1.methodWithManyArguments(((Integer) var3[0]).intValue(), ((Float) var3[1]).floatValue(), (Integer[]) var3[2], ((Integer) var3[3]).intValue());
            case 4:
                return (T) var1.methodWithManyArguments(((Integer) var3[0]).intValue(), ((Float) var3[1]).floatValue(), (Integer[]) var3[2], (SomeClass[]) var3[3]);
            case 5:
                return (T) var1.methodWithManyArguments(((Integer) var3[0]).intValue(), ((Float) var3[1]).floatValue(), (Integer[]) var3[2], (Float) var3[3], (SomeClass[]) var3[4], (Boolean) var3[5], (int[]) var3[6]);
            case 6:
                return (T) Integer.valueOf(var1.methodWithVarArgs(((Character) var3[0]).charValue(), (Double) var3[1], (Long) var3[2], (Integer[]) var3[3]));
            case 7:
                return (T) Integer.valueOf(var1.getIntValue());
            case 8:
                return (T) SomeClass.staticMethod((String) var3[0], ((Integer) var3[1]).intValue());
            case 9:
                var1.test();
                return null;
            default:
                throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    public final <T> T get(SomeClass var1, int var2) {
        switch (var2) {
            case 0:
                return (T) Boolean.valueOf(SomeClass.x);
            case 1:
                return (T) Boolean.valueOf(SomeClass.bu);
            case 2:
                return (T) var1.name;
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public final <T, V> void set(SomeClass var1, int var2, V var3) {
        switch (var2) {
            case 0:
                SomeClass.x = ((Boolean) var3).booleanValue();
                return;
            case 1:
                SomeClass.bu = ((Boolean) var3).booleanValue();
                return;
            case 2:
                var1.name = (String) var3;
                return;
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public final <T> SomeClass newInstanceWithIndex(int var1, T... var2) {
        switch (var1) {
            case 0:
                return new SomeClass((String) var2[0]);
            case 1:
                return new SomeClass(((Integer) var2[0]).intValue(), ((Integer) var2[1]).intValue());
            case 2:
                return new SomeClass();
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public final SomeClass newInstance() {
        return new SomeClass();
    }
}
