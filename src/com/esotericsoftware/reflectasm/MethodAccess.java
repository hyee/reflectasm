package com.esotericsoftware.reflectasm;

import java.lang.reflect.Method;

@SuppressWarnings("UnusedDeclaration")
public class MethodAccess<ANY> {
    public final ClassAccess<ANY> console;
    public final ClassInfo<ANY> classInfo;

    protected MethodAccess(ClassAccess<ANY> console) {
        this.console = console;
        this.classInfo = console.getInfo();
    }

    static public <ANY> MethodAccess access(Class<ANY> type, String... dumpFile) {
        return new MethodAccess(ClassAccess.access(type, dumpFile));
    }

    @Override
    public String toString() {
        return console.toString();
    }

    public <T, V> T invokeWithIndex(ANY object, int methodIndex, V... args) {
        return console.invokeWithIndex(object, methodIndex, args);
    }

    /**
     * Invokes the method with the specified name and the specified param types.
     */
    public <T, V> T invokeWithTypes(ANY object, String methodName, Class[] paramTypes, V... args) {
        return console.invokeWithTypes(object, methodName, paramTypes, args);
    }

    /**
     * Invokes the first method with the specified name and the specified number of arguments.
     */
    public <T, V> T invoke(ANY object, String methodName, V... args) {
        return console.invoke(object, methodName, args);
    }

    /**
     * Returns the index of the first method with the specified name.
     */
    public int getIndex(String methodName) {
        return console.indexOfMethod(methodName);
    }

    public int getIndex(Method method) {
        return console.indexOfMethod(method);
    }

    /**
     * Returns the index of the first method with the specified name and param types.
     */
    public int getIndex(String methodName, Class... paramTypes) {
        return console.indexOfMethod(null, methodName, paramTypes);
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int getIndex(String methodName, int paramsCount) {
        return console.indexOfMethod(methodName, paramsCount);
    }

    public String[] getMethodNames() {return classInfo.methodNames;}

    public Class[][] getParameterTypes() {
        return classInfo.methodParamTypes;
    }

    public Class[] getReturnTypes() {
        return classInfo.returnTypes;
    }

    public Integer[] getModifiers() {return classInfo.constructorModifiers;}

    public int getMethodCount() {
        return classInfo.methodCount;
    }
}