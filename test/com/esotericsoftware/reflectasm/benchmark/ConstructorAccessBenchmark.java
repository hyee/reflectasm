package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.ConstructorAccess;

import java.lang.invoke.MethodHandle;

public class ConstructorAccessBenchmark extends Benchmark {
    public static String[] result;

    public ConstructorAccessBenchmark() throws Throwable {
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        Object[] dontCompileMeAway = new Object[count];

        Class type = SomeClass.class;
        ConstructorAccess<SomeClass> access = ConstructorAccess.access(type);
        MethodHandle handle = access.console.getHandle(null, null, ClassAccess.NEW);

        for (int i = 0; i < rounds / 3; i++)
            for (int ii = 0; ii < count; ii++) {
                dontCompileMeAway[ii] = access.console.accessor.newInstance();
                dontCompileMeAway[ii] = type.newInstance();
                dontCompileMeAway[ii] = (SomeClass) handle.invokeExact();
            }

        for (int i = 0; i < rounds; i++)
            for (int ii = 0; ii < count; ii++)

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
                dontCompileMeAway[ii] = (SomeClass) handle.invokeExact();
        }
        end("Constructor - DirectMethodHandle");
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

    public static void main(String[] args) throws Throwable {
        new ConstructorAccessBenchmark();
    }

    static public class SomeClass {
        public String name;
    }
}
