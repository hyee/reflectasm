package com.esotericsoftware.reflectasm;

/**
 * Created by Will on 2017/2/2.
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

public class ClassInfo {
    public String[] fieldNames;
    public Class[] fieldTypes;
    public Integer[] fieldModifiers;
    public String[] methodNames;
    public Class[][] parameterTypes;
    public Class[] returnTypes;
    public Integer[] methodModifiers;
    public Integer[] constructorModifiers;
    public Class[][] constructorParameterTypes;
    public boolean isNonStaticMemberClass;
    public Class baseClass;
    public Map<String,Integer[]> attrIndex;
    public Map<Method, Integer> methods;
    public Map<Field, Integer> fields;
    public Map<Constructor<?>, Integer> constructors;
}
