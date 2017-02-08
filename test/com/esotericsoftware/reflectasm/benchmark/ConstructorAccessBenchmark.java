package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ConstructorAccess;

public class ConstructorAccessBenchmark extends Benchmark {
    public static String[] result;

    public ConstructorAccessBenchmark() throws Exception {
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        Object[] dontCompileMeAway = new Object[count];

        Class type = SomeClass.class;
        ConstructorAccess<SomeClass> access = ConstructorAccess.access(type);

        for (int i = 0; i < rounds; i++)
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = access.console.accessor.newInstance();
        for (int i = 0; i < rounds; i++)
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = type.newInstance();
        warmup = false;
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = access.newInstance();
        }
        end("Constructor - ReflectASM");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = type.newInstance();
        }
        end("Constructor - Reflection");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = new SomeClass();
        }
        end("Constructor - Direct");
        result = chart("Constructor");
    }

    public static void main(String[] args) throws Exception {
        new ConstructorAccessBenchmark();
    }

    static public class SomeClass {
        public String name;
    }
}
