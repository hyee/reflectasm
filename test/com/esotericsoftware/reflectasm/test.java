package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.MethodAccessTest.SomeClass;

public class test extends Object implements Accessor<SomeClass> {
    final static ClassInfo classInfo = new ClassInfo();

    static {
        classInfo.methodNames = new String[]{"getName", "setName", "setValue", "getIntValue", "staticMethod", "methodWithVarArgs", "methodWithManyArguments", "methodWithManyArguments", "test"};
        classInfo.methodParamTypes = new Class[][]{new Class[0], {String.class}, {Integer.TYPE, Boolean.class}, new Class[0], {String.class, Integer.TYPE}, {Character.TYPE, Double.class, Long.class, Integer[].class}, {Integer.TYPE, Float.TYPE, Integer[].class, SomeClass[].class}, {Integer.TYPE, Float.TYPE, Integer[].class, Float.class, SomeClass[].class, Boolean.class, int[].class}, new Class[0]};
        classInfo.returnTypes = new Class[]{String.class, Void.TYPE, Void.TYPE, Integer.TYPE, String.class, Integer.TYPE, String.class, String.class, Void.TYPE};
        classInfo.methodModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(9), Integer.valueOf(129), Integer.valueOf(1), Integer.valueOf(129), Integer.valueOf(1)};
        classInfo.fieldNames = new String[]{"name", "intValue", "bu", "x"};
        classInfo.fieldTypes = new Class[]{String.class, Integer.TYPE, Boolean.TYPE, Boolean.TYPE};
        classInfo.fieldModifiers = new Integer[]{Integer.valueOf(0), Integer.valueOf(2), Integer.valueOf(8), Integer.valueOf(9)};
        classInfo.constructorParamTypes = new Class[][]{new Class[0], {String.class}, {Integer.TYPE, Integer.TYPE}};
        classInfo.constructorModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1)};
        classInfo.baseClass = SomeClass.class;
        classInfo.isNonStaticMemberClass = true;
        ClassAccess.buildIndex(classInfo);
    }

    public ClassInfo getInfo() {
        return classInfo;
    }

    public test() {

    }

    public Object invoke(SomeClass var1, int var2, Object... var3) {
        switch (var2) {
            case 0:
                return var1.getName();
            case 1:
                var1.setName((String) var3[0]);
                return null;
            case 2:
                var1.setValue(((Integer) var3[0]).intValue(), (Boolean) var3[1]);
                return null;
            case 3:
                return Integer.valueOf(var1.getIntValue());
            case 4:
                return SomeClass.staticMethod((String) var3[0], ((Integer) var3[1]).intValue());
            case 5:
                return Integer.valueOf(var1.methodWithVarArgs(((Character) var3[0]).charValue(), (Double) var3[1], (Long) var3[2], (Integer[]) var3[3]));
            case 6:
                return var1.methodWithManyArguments(((Integer) var3[0]).intValue(), ((Float) var3[1]).floatValue(), (Integer[]) var3[2], (SomeClass[]) var3[3]);
            case 7:
                return var1.methodWithManyArguments(((Integer) var3[0]).intValue(), ((Float) var3[1]).floatValue(), (Integer[]) var3[2], (Float) var3[3], (SomeClass[]) var3[4], (Boolean) var3[5], (int[]) var3[6]);
            case 8:
                var1.test();
                return null;
            default:
                throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    public Object get(SomeClass var1, int var2) {
        switch (var2) {
            case 0:
                return var1.name;
            case 1:
            case 2:
                return Boolean.valueOf(SomeClass.bu);
            case 3:
                return Boolean.valueOf(SomeClass.x);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void set(SomeClass var1, int var2, Object var3) {
        switch (var2) {
            case 0:
                var1.name = (String) var3;
                return;
            case 1:

                return;
            case 2:
                SomeClass.bu = ((Boolean) var3).booleanValue();
                return;
            case 3:
                SomeClass.x = ((Boolean) var3).booleanValue();
                return;
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public SomeClass newInstanceWithIndex(int var1, Object... var2) {
        switch (var1) {
            case 0:
                return new SomeClass();
            case 1:
                return new SomeClass((String) var2[0]);
            case 2:
                return new SomeClass(((Integer) var2[0]).intValue(), ((Integer) var2[1]).intValue());
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public SomeClass newInstance() {
        return new SomeClass();
    }
}
