package com.esotericsoftware.reflectasm;

import java.lang.reflect.Method;

@SuppressWarnings("UnusedDeclaration")
public class MethodAccess<ANY> {
    public final ClassAccess<ANY> accessor;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return accessor.toString();
    }

    protected MethodAccess(ClassAccess<ANY> accessor) {
        this.accessor = accessor;
        this.classInfo = accessor.getInfo();
    }

    public Object invoke(ANY object, int methodIndex, Object... args) {
        return accessor.invoke(object, methodIndex, args);
    }

    /**
     * Invokes the method with the specified name and the specified param types.
     */
    public Object invokeWithTypes(ANY object, String methodName, Class[] paramTypes, Object... args) {
        return accessor.invokeWithTypes(object, methodName, paramTypes, args);
    }

    /**
     * Invokes the first method with the specified name and the specified number of arguments.
     */
    public Object invoke(ANY object, String methodName, Object... args) {
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

    static public <ANY>MethodAccess get(Class<ANY> type, String... dumpFile) {
        return new MethodAccess(ClassAccess.get(type, dumpFile));
    }
}