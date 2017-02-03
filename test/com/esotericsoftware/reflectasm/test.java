package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.ClassInfo;
import com.esotericsoftware.reflectasm.ClassAccess.Accessor;
import com.esotericsoftware.reflectasm.MethodAccessTest.SomeClass;
import com.esotericsoftware.reflectasm.util.NumberUtils;


public class test implements Accessor {
    ClassInfo classInfo = new ClassInfo();

    public ClassInfo getInfo() {
        return this.classInfo;
    }

    public void setInfo(ClassInfo var1) {
        this.classInfo = var1;
    }

    public test() {
        this.classInfo.methodNames = new String[]{"getName", "setName", "getIntValue", "setIntValue", "methodWithManyArguments", "varArgsMethod"};
        this.classInfo.parameterTypes = new Class[][]{new Class[0], {String.class}, new Class[0], {Integer.TYPE}, {Integer.TYPE, Float.TYPE, Integer.class, Float.class, SomeClass.class, SomeClass.class, SomeClass.class}, {Integer.TYPE, Integer.TYPE, Object[].class}};
        this.classInfo.returnTypes = new Class[]{String.class, Void.TYPE, Integer.TYPE, Void.TYPE, String.class, String.class};
        this.classInfo.methodModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(145)};
        this.classInfo.fieldNames = new String[]{"name", "intValue"};
        this.classInfo.fieldTypes = new Class[]{String.class, Integer.TYPE};
        this.classInfo.fieldModifiers = new Integer[]{Integer.valueOf(2), Integer.valueOf(2)};
        this.classInfo.constructorParameterTypes = new Class[][]{new Class[0]};
        this.classInfo.constructorModifiers = new Integer[]{Integer.valueOf(1)};
        this.classInfo.baseClass = SomeClass.class;
        this.classInfo.isNonStaticMemberClass = true;
        ClassAccess.buildIndex(this.classInfo);
    }

    public SomeClass newInstance(int var1, java.lang.Object... var2) {
        switch(var1) {
            case 0:
                return new SomeClass();
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public SomeClass newInstance() {
        return new SomeClass();
    }

    public java.lang.Object invoke(java.lang.Object var1, int var2, java.lang.Object... var3) {
        switch(var2) {
            case 0:
                return ((SomeClass)var1).getName();
            case 1:
                ((SomeClass)var1).setName((String)NumberUtils.convert(var3[0], String.class));
                return null;
            case 2:
                return Integer.valueOf(((SomeClass)var1).getIntValue());
            case 3:
                ((SomeClass)var1).setIntValue((Integer) NumberUtils.convert(var3[0], Integer.TYPE));
                return null;
           default:
                throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    @Override
    public void set(Object instance, int fieldIndex, Object value) {

    }

    public boolean getBoolean(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as boolean: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void setBoolean(java.lang.Object var1, int var2, boolean var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as boolean: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public byte getByte(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as byte: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void setByte(java.lang.Object var1, int var2, byte var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as byte: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public short getShort(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as short: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    @Override
    public int getInt(Object instance, int fieldIndex) {
        return 0;
    }

    public void setShort(java.lang.Object var1, int var2, short var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as short: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    @Override
    public void setInt(Object instance, int fieldIndex, int value) {

    }

    public long getLong(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as long: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void setLong(java.lang.Object var1, int var2, long var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as long: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public double getDouble(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as double: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void setDouble(java.lang.Object var1, int var2, double var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as double: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public float getFloat(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as float: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void setFloat(java.lang.Object var1, int var2, float var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as float: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public char getChar(java.lang.Object var1, int var2) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as char: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public void setChar(java.lang.Object var1, int var2, char var3) {
        switch(var2) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Field not declared as char: " + var2);
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    @Override
    public Object get(Object instance, int fieldIndex) {
        return null;
    }
}
