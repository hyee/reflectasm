package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.FieldAccess;

import java.lang.reflect.Field;

public class FieldAccessBenchmark extends Benchmark {
    public static String[] result;

    public FieldAccessBenchmark() throws Exception {
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        Object[] dontCompileMeAway = new Object[count];

        FieldAccess access = FieldAccess.access(SomeClass.class);
        SomeClass someObject = new SomeClass();
        int index = access.getIndex("name");

        Field field = SomeClass.class.getField("name");

        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++) {
                access.console.accessor.set(someObject, index, "first");
                dontCompileMeAway[ii] = access.console.accessor.get(someObject, index);
            }
            for (int ii = 0; ii < count; ii++) {
                field.set(someObject, "first");
                dontCompileMeAway[ii] = field.get(someObject);
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
                field.set(someObject, "first");
                dontCompileMeAway[ii] = field.get(someObject);
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

    static public class SomeClass {
        public String name;
    }

    public static void main(String[] args) throws Exception {
        new FieldAccessBenchmark();
    }
}
