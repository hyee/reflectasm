package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.FieldAccess;

import java.lang.reflect.Field;

public class FieldAccessBenchmark extends Benchmark {
    public FieldAccessBenchmark() throws Exception {
        int count = 300000;
        Object[] dontCompileMeAway = new Object[count];

        FieldAccess access = FieldAccess.get(SomeClass.class);
        SomeClass someObject = new SomeClass();
        int index = access.getIndex("name");

        Field field = SomeClass.class.getField("name");

        for (int i = 0; i < 100; i++) {
            for (int ii = 0; ii < count; ii++) {
                access.set(someObject, index, "first");
                dontCompileMeAway[ii] = access.get(someObject, index);
            }
            for (int ii = 0; ii < count; ii++) {
                field.set(someObject, "first");
                dontCompileMeAway[ii] = field.get(someObject);
            }
        }
        warmup = false;
        start();
        for (int i = 0; i < 100; i++) {
            for (int ii = 0; ii < count; ii++) {
                access.set(someObject, index, "first");
                dontCompileMeAway[ii] = access.get(someObject, index);
            }
        }
        end("FieldAccess");
        start();
        for (int i = 0; i < 100; i++) {
            for (int ii = 0; ii < count; ii++) {
                field.set(someObject, "first");
                dontCompileMeAway[ii] = field.get(someObject);
            }
        }
        end("Reflection");
        start();
        for (int i = 0; i < 100; i++) {
            for (int ii = 0; ii < count; ii++) {
                someObject.name = "first";
                dontCompileMeAway[ii] = someObject.name;
            }
        }
        end("Direct");
        chart("Field Set/Get");
    }

    static public class SomeClass {
        public String name;
    }

    public static void main(String[] args) throws Exception {
        new FieldAccessBenchmark();
    }
}
