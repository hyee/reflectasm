
package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ConstructorAccess;

public class ConstructorAccessBenchmark extends Benchmark {
	public ConstructorAccessBenchmark () throws Exception {
		int count = 300000;
		Object[] dontCompileMeAway = new Object[count];

		Class type = SomeClass.class;
		ConstructorAccess<SomeClass> access = ConstructorAccess.get(type);

		for (int i = 0; i < 100; i++)
			for (int ii = 0; ii < count; ii++)
				dontCompileMeAway[ii] = access.newInstance();
		for (int i = 0; i < 100; i++)
			for (int ii = 0; ii < count; ii++)
				dontCompileMeAway[ii] = type.newInstance();
		warmup = false;
		start();
		for (int i = 0; i < 100; i++) {

			for (int ii = 0; ii < count; ii++)
				dontCompileMeAway[ii] = access.newInstance();

		}
		end("ConstructorAccess");
		start();
		for (int i = 0; i < 100; i++) {
			for (int ii = 0; ii < count; ii++)
				dontCompileMeAway[ii] = type.newInstance();

		}
		end("Reflection");
		start();
		for (int i = 0; i < 100; i++) {
			for (int ii = 0; ii < count; ii++)
				dontCompileMeAway[ii] = new SomeClass();
		}
		end("Normal");
		chart("Constructor");
	}

	static public class SomeClass {
		public String name;
	}

	public static void main (String[] args) throws Exception {
		new ConstructorAccessBenchmark();
	}
}
