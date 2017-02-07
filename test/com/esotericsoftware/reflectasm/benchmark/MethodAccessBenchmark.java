package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Method;

public class MethodAccessBenchmark extends Benchmark {
    public static String[] result;

    public MethodAccessBenchmark() throws Exception {
        //ClassAccess.IS_STRICT_CONVERT=true;
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        Object[] dontCompileMeAway = new Object[count];
        Object[] args = new Object[0];

        ClassAccess<SomeClass> access = ClassAccess.access(SomeClass.class);
        SomeClass someObject = new SomeClass();
        int index = access.indexOfMethod("getName");

        Method method = SomeClass.class.getMethod("getName");
        // method.setAccessible(true); // Improves reflection a bit.

        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = access.accessor.invokeWithIndex(someObject, index, args);
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = method.invoke(someObject, args);
        }
        warmup = false;
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = access.accessor.invokeWithIndex(someObject, index, args);
        }
        end("Method Call - ReflectASM");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = method.invoke(someObject, args);

        }
        end("Method Call - Reflection");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = someObject.getName();

        }
        end("Method Call - Direct");
        result = chart("Method Call");
    }

    static public class SomeClass {
        private String name = "something";

        public String getName() {
            return name;
        }
    }

    public static void main(String[] args) throws Exception {
        new MethodAccessBenchmark();
    }
}
