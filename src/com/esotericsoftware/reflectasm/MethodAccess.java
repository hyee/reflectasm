package com.esotericsoftware.reflectasm;

import java.lang.reflect.Method;

@SuppressWarnings("UnusedDeclaration")
public class MethodAccess {
    public final ClassAccess accessor;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return accessor.toString();
    }

    protected MethodAccess(ClassAccess accessor) {
        this.accessor = accessor;
        this.classInfo = accessor.getInfo();
    }

    public Object invoke(Object object, int methodIndex, Object... args) {
        return accessor.invoke(object, methodIndex, args);
    }

    /**
     * Invokes the method with the specified name and the specified param types.
     */
    public Object invokeWithTypes(Object object, String methodName, Class[] paramTypes, Object... args) {
        return accessor.invokeWithTypes(object, methodName, paramTypes, args);
    }

    /**
     * Invokes the first method with the specified name and the specified number of arguments.
     */
    public Object invoke(Object object, String methodName, Object... args) {
        return accessor.invoke(object, methodName, args);
    }

    /**
     * Returns the index of the first method with the specified name.
     */
    public int getIndex(String methodName) {
        return accessor.indexOfMethod(methodName);
    }

    public int getIndex(Method method) {
        return accessor.indexOfMethod(method);
    }

    /**
     * Returns the index of the first method with the specified name and param types.
     */
    public int getIndex(String methodName, Class... paramTypes) {
        return accessor.indexOfMethod(methodName, paramTypes);
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int getIndex(String methodName, int paramsCount) {
        return accessor.indexOfMethod(methodName, paramsCount);
    }

    public String[] getMethodNames() {
        return accessor.getMethodNames();
    }

    public Class[][] getParameterTypes() {
        return accessor.getParameterTypes();
    }

    public Class[] getReturnTypes() {
        return accessor.getReturnTypes();
    }

    static public MethodAccess get(Class type, String... dumpFile) {
        return new MethodAccess(ClassAccess.get(type, dumpFile));
    }
}