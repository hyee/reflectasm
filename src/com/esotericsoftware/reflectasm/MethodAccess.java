package com.esotericsoftware.reflectasm;

import java.lang.reflect.Method;

@SuppressWarnings("UnusedDeclaration")
public class MethodAccess<ANY> {
    public final ClassAccess<ANY> console;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return console.toString();
    }

    protected MethodAccess(ClassAccess<ANY> console) {
        this.console = console;
        this.classInfo = console.getInfo();
    }

    public Object invokeWithIndex(ANY object, int methodIndex, Object... args) {
        return console.invokeWithIndex(object, methodIndex, args);
    }

    /**
     * Invokes the method with the specified name and the specified param types.
     */
    public Object invokeWithTypes(ANY object, String methodName, Class[] paramTypes, Object... args) {
        return console.invokeWithTypes(object, methodName, paramTypes, args);
    }

    /**
     * Invokes the first method with the specified name and the specified number of arguments.
     */
    public Object invoke(ANY object, String methodName, Object... args) {
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
        return console.indexOfMethod(methodName, paramTypes);
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int getIndex(String methodName, int paramsCount) {
        return console.indexOfMethod(methodName, paramsCount);
    }

    public String[] getMethodNames() {
        return console.getMethodNames();
    }

    public Class[][] getParameterTypes() {
        return console.getParameterTypes();
    }

    public Class[] getReturnTypes() {
        return console.getReturnTypes();
    }

    static public <ANY> MethodAccess access(Class<ANY> type, String... dumpFile) {
        return new MethodAccess(ClassAccess.access(type, dumpFile));
    }
}