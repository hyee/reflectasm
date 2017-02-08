//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.ClassLoaderTest1.Test;

public class test implements Accessor<Test> {
    static final ClassInfo<Test> classInfo = ClassAccess.buildIndex(7);

    public ClassInfo<Test> getInfo() {
        return classInfo;
    }

    public test() {
    }

    public Object invokeWithIndex(Test var1, int var2, Object... var3) {
        switch (var2) {
            case 0:
                return var1.toString();
            default:
                throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    public Object get(Test var1, int var2) {
        switch (var2) {
            case 0:
                return var1.name;
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void set(Test var1, int var2, Object var3) {
        switch (var2) {
            case 0:
                var1.name = (String) var3;
                return;
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public Test newInstanceWithIndex(int var1, Object... var2) {
        switch (var1) {
            case 0:
                return new Test();
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public Test newInstance() {
        return new Test();
    }
}
