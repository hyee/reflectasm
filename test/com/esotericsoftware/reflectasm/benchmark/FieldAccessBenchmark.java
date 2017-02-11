package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.FieldAccess;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;

public class FieldAccessBenchmark extends Benchmark {
    public static String[] result;

    public FieldAccessBenchmark() throws Throwable {
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        String[] dontCompileMeAway = new String[count];

        FieldAccess<SomeClass> access = FieldAccess.access(SomeClass.class);
        SomeClass someObject = new SomeClass();
        int index = access.getIndex("name");
        MethodHandle getter = access.console.getHandleWithIndex(index, ClassAccess.GETTER);
        MethodHandle setter = access.console.getHandleWithIndex(index, ClassAccess.SETTER);

        Field field = SomeClass.class.getField("name");

        for (int i = 0; i < rounds / 3; i++) {
            for (int ii = 0; ii < count; ii++) {
                access.console.accessor.set(someObject, index, "first");
                dontCompileMeAway[ii] = access.console.accessor.get(someObject, index);
                field.set(someObject, "first");
                dontCompileMeAway[ii] = (String) field.get(someObject);
                setter.invokeExact(someObject, "first");
                dontCompileMeAway[ii] = (String) getter.invokeExact(someObject);
            }
        }
        warmup = false;
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++) {
                access.console.accessor.set(someObject, index, "first");
                dontCompileMeAway[ii] = access.console.accessor.get(someObject, index);
            }
        }
        end("Field Set+Get - ReflectASM");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++) {
                setter.invokeExact(someObject, "first");
                dontCompileMeAway[ii] = (String) getter.invokeExact(someObject);
            }
        }
        end("Field Set+Get - DirectMethodHandle");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++) {
                field.set(someObject, "first");
                dontCompileMeAway[ii] = (String) field.get(someObject);
            }
        }
        end("Field Set+Get - Reflection");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++) {
                someObject.name = "first";
                dontCompileMeAway[ii] = someObject.name;
            }
        }
        end("Field Set+Get - Direct");
        result = chart("Field Set/Get");
    }

    public static void main(String[] args) throws Throwable {
        new FieldAccessBenchmark();
    }

    static public class SomeClass {
        public String name;
    }
}
