//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.esotericsoftware.reflectasm;

import test.TestObject;

public class test extends Object implements Accessor<TestObject> {
    static final ClassInfo classInfo = new ClassInfo();

    public ClassInfo getInfo() {
        return classInfo;
    }

    public test() {
    }

    static {
        classInfo.methodNames = new String[]{"func2", "func1"};
        classInfo.methodParamTypes = new Class[][]{{Integer.TYPE, Double.class, String.class, Long.TYPE}, new Class[0]};
        classInfo.returnTypes = new Class[]{String.class, Void.TYPE};
        classInfo.methodModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(8)};
        classInfo.methodDescs = new String[]{"(ILjava/lang/Double;Ljava/lang/String;J)Ljava/lang/String;", "()V"};
        classInfo.fieldNames = new String[]{"fi", "fd", "fs", "fl"};
        classInfo.fieldTypes = new Class[]{Long.TYPE, Double.class, String.class, Integer.TYPE};
        classInfo.fieldModifiers = new Integer[]{Integer.valueOf(2), Integer.valueOf(1), Integer.valueOf(8), Integer.valueOf(1)};
        classInfo.fieldDescs = new String[]{"J", "Ljava/lang/Double;", "Ljava/lang/String;", "I"};
        classInfo.constructorParamTypes = new Class[][]{new Class[0], {Integer.TYPE, Double.class, String.class, Long.TYPE}};
        classInfo.constructorModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1)};
        classInfo.constructorDescs = new String[]{"()V", "(ILjava/lang/Double;Ljava/lang/String;J)V"};
        classInfo.baseClass = TestObject.class;
        classInfo.isNonStaticMemberClass = false;
        classInfo.bucket = 4;
        ClassAccess.buildIndex(classInfo);
    }

    public Object invokeWithIndex(TestObject var1, int var2, Object... var3) {
        switch (var2) {
            case 0:
                return var1.func2(((Integer) var3[0]).intValue(), (Double) var3[1], (String) var3[2], ((Long) var3[3]).longValue());
            case 1:
                return null;
            default:
                throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    public Object get(TestObject var1, int var2) {
        switch (var2) {
            case 0:
                return 0;
            case 1:
                return var1.fd;
            case 2:
            case 3:
                return Integer.valueOf(var1.fl);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void set(TestObject var1, int var2, Object var3) {
        switch (var2) {
            case 0:
                return;
            case 1:
                var1.fd = (Double) var3;
                return;
            case 2:
                return;
            case 3:
                var1.fl = ((Integer) var3).intValue();
                return;
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public TestObject newInstanceWithIndex(int var1, Object... var2) {
        switch (var1) {
            case 0:
                return new TestObject();
            case 1:
                return new TestObject(((Integer) var2[0]).intValue(), (Double) var2[1], (String) var2[2], ((Long) var2[3]).longValue());
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public TestObject newInstance() {
        return new TestObject();
    }
}
