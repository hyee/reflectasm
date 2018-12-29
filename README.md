![](https://raw.github.com/wiki/EsotericSoftware/reflectasm/images/logo.png)

Please use the [ReflectASM discussion group](http://groups.google.com/group/reflectasm-users) for support.

## Note

This is the revised version, some differences from `EsotericSoftware/reflectasm`:
* Supports `Java 8` only in order to remove the dependency of `asm-xxx.jar`
* New class `ClassAccess` as the base of `MethodAccess`/`FieldAccess`/`ConstructorAccess`
* Provides the interface to dump the dynamic class for investigation purpose
* Provides the caching options for performance purpose(default as enabled)
* Optimize some logics for performance purpose
* Auto data type conversion for invoking or construction
* Accurately position the closest method for overloading
* Supports methods/constructors with variable parameters
* Removes the harcodes of the package name
* Supports accessing non-public class/method/field
* Uses of generic types to reduce unnecessary explicit/implicit casting
* Reduces the generation of proxy classes
* Supports [MethodHandle](https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/MethodHandle.html#asVarargsCollector-java.lang.Class-)

To support <b>Java 7</b>, just change the imports in class `ClassAccess` to add back the dependency of `asm-xxx.jar`

## Overview

ReflectASM is a very small Java library that provides high performance reflection by using code generation. An access class is generated to set/get fields, call methods, or create a new instance. The access class uses bytecode rather than Java's reflection, so it is much faster. It can also access primitive fields via bytecode to avoid boxing.

#### Summary of the cost(Smaller value means better performance)
The benchmark code can be found in the `benchmark` directory, test environment:
* CPU: Intel I7-3820QM
* VM : Java 8u112 x86
* OS : Win10 x64, and as a laptop, the result is not very stable<br/> 

| VM | Item | Direct | DirectMethodHandle | ReflectASM | Reflection |
| --- | --- |  ---------  |  ---------  |  ---------  |  ---------  |
| Server VM | Field Set+Get | 1.17 ns | 7.83 ns | 6.88 ns | 12.51 ns |
| Server VM | Method Call | 0.86 ns | 4.14 ns | 3.30 ns | 4.51 ns |
| Server VM | Constructor | 5.10 ns | 7.51 ns | 6.86 ns | 8.62 ns |
| Client VM | Field Set+Get | 2.49 ns | 16.30 ns | 13.75 ns | 209.21 ns |
| Client VM | Method Call | 2.13 ns | 10.54 ns | 8.32 ns | 48.85 ns |
| Client VM | Constructor | 71.00 ns | 192.87 ns | 74.80 ns | 154.43 ns |

#### Server VM

<img src="http://chart.apis.google.com/chart?chtt=&Java%201.8.0_112%20x86(Server%20VM)&chs=700x237&chd=t:104886099,704401675,619611373,1125590088,77170418,372776726,296855382,405673821,458851833,675729992,616966498,775650205&chds=0,1125590088&chxl=0:|Constructor%20-%20Reflection|Constructor%20-%20ReflectASM|Constructor%20-%20DirectMethodHandle|Constructor%20-%20Direct|Method%20Call%20-%20Reflection|Method%20Call%20-%20ReflectASM|Method%20Call%20-%20DirectMethodHandle|Method%20Call%20-%20Direct|Field%20Set+Get%20-%20Reflection|Field%20Set+Get%20-%20ReflectASM|Field%20Set+Get%20-%20DirectMethodHandle|Field%20Set+Get%20-%20Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666" />

#### Client VM
<img src="http://chart.apis.google.com/chart?chtt=&Java%201.8.0_112%20x86(Client%20VM)&chs=700x237&chd=t:74650984,489015485,412433873,6276273031,63781455,316114537,249572650,1465394076,2130048135,5786071571,2244086000,4632780627&chds=0,6276273031&chxl=0:|Constructor%20-%20Reflection|Constructor%20-%20ReflectASM|Constructor%20-%20DirectMethodHandle|Constructor%20-%20Direct|Method%20Call%20-%20Reflection|Method%20Call%20-%20ReflectASM|Method%20Call%20-%20DirectMethodHandle|Method%20Call%20-%20Direct|Field%20Set+Get%20-%20Reflection|Field%20Set+Get%20-%20ReflectASM|Field%20Set+Get%20-%20DirectMethodHandle|Field%20Set+Get%20-%20Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666" />

## Usage

Considering this class:

```java
public class TestObject {
    static String fs;
    public Double fd;
    public int fi;
    private long fl;
    public TestObject() {fs = "TestObject0";}
    public TestObject(int fi1, Double fd1, String fs1, long l) {}
    static String func1(String str) {fs = str;return str;}
    public String func2(int fi1, Double fd1, String fs1, long l) {return fs;}
}

```

Reflection with ReflectASM:
```java
ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
TestObject obj;
// Construction
obj = access.newInstance();
obj = access.newInstance(1, 2, 3, 4);

// Set+Get field
access.set(null, "fs", 1); // static field
System.out.println((String)access.get(null, "fs"));

access.set(obj, "fd", 2);
System.out.println((String)access.get(obj, "fd"));

// Method invoke
access.invoke(null,"func1","a"); //static call
System.out.println((String)access.invoke(obj, "func2",1,2,3,4));
```

<br/>
If same field/method/constructor is referenced frequently, for performance purpose, consider following code:
```java
ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
TestObject obj;
//Identify the indexes for further use
int newIndex=access.indexOfConstructor(int.class, Double.class, String.class, long.class);
int fieldIndex=access.indexOfField("fd");
int methodIndex=access.indexOfMethod("func1",String.class);
//Now use the index to access object in loop or other part
for(int i=0;i<100;i++) {
    obj=access.newInstanceWithIndex(newIndex,1,2,3,4);
    access.set(obj,fieldIndex,123);
    String result=access.invokeWithIndex(null,methodIndex,"x");
}
```

<br/>
With above code, input arguments are auto-converted into the corresponding data types before the call. If auto-conversion is unnecessary in your code and all argument types are correct previously, for more performance purpose, replace as:
```java
ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
TestObject obj;
//Identify the indexes for further use
int newIndex=access.indexOfConstructor(int.class, Double.class, String.class, long.class);
int fieldIndex=access.indexOfField("fd");
int methodIndex=access.indexOfMethod("func1",String.class);
//Now use the index to access object in loop or other part
for(int i=0;i<100;i++) {
    obj=access.accessor.newInstanceWithIndex(newIndex,1,Double.valueOf(2),"3",4L);
    access.accessor.set(obj,fieldIndex,Double.valueOf(123));
    String result=access.accessor.invokeWithIndex(null,methodIndex,"x");
}
```
Class reflection with ReflectASM(F=FieldAccess,M=MethodAccess,C=ConstructorAccess, {}=Array):

| ClassAccess.*method* | Equivalence | Description |
| -------------------- | ---------- | ----------- | 
| *static* access(Class,[dump dir]) | (F/M/C).access | returns a wrapper object of the underlying class, in case of the 2nd parameter is specified, dumps the dynamic classes into the target folder |
| indexesOf(name,type)| | returns an index array that matches the given name and type(`field`/`method`/`<new>`) |
| indexesOf(Class,name,type)| | returns an index array that matches the given class,name and type(`field`/`method`/`<new>`) |
| indexOf(name,type)| | returns the first element of `indexesOf`|
| getHandleWithIndex(index,type) | | returns a MethodHandle regards to the index(from `indexOf`), type here can be `set/get/method/<new>` |
| getHandle(Class,name,type,{argtypes}) | | returns a MethodHandle regards to input parameter types |
| getHandleWithArgs(Class,name,type,{args}) | | returns a MethodHandle regards to input parameter values |
| invokeWithMethodHandle(instance,index,type,{args}|| if option `invokeWithMethodHandle` is `true`(default as `false`, then all below methods will use MethodHandle to process, instead of `accessor`|
| indexOfField(name/Field) | F.getIndex | returns the field index that matches the given input|
| indexOfField(Class,name) | | returns the field index that defined in the target class|
| indexOfMethod(name/Method) | M.getIndex | returns the first method index that matches the given input|
| indexOfMethod(Class,name,{argTypes}) | | returns the first method index that defined in the target class|
| indexOfMethod(name,argCount/{argTypes}) | (M/C).getIndex | returns the first index that matches the given input|
| indexOfConstructor(constructor) | C.getIndex | returns the index that matches the given constructor|
| set(instance,Name/index,value) | F.set | Assign value to the specific field |
| set*Primitive*(instance,index,value) | F.set*Primitive* | Assign primitive value to the specific field, *primitive* here can be `Integer/Long/Double/etc`  |
| get(instance,name/index) | F.get | Get field value |
| get*Primitive*(instance,index) | F.get*Primitive* | Get field value and convert to target primitive type|
| get(instance,name/index, Class) | F.get | Get field value and convert to target class type|
| invoke(instance,name,{args}) | M.invoke | Executes  method |
| invokeWithIndex(instance,index,{args}) | M.invokeWithIndex | Executes method by specifying the exact index|
| invokeWithWithTypes(instance,name/index,{argTypes},{args}) | M.invokeWithWithTypes | Invokes method by specifying parameter types in order to position the accurate method|
| newInstance() | C.newInstance | Create underlying Object |
| newInstance({args}) | C.newInstance | Create underlying Object |
| newInstanceWithIndex(index,{args}) | C.newInstanceWithIndex | Create underlying Object with the specific constructor index|
| newInstanceWithTypes({argTypes},{args}) | C.newInstanceWithTypes | Create underlying Object by specifying parameter types in order to position the accurate constructor|
| getInfo || Get the underlying `ClassInfo`|
| *accessor.*newInstanceWithIndex| | Directly initializes the instance without validating/auto-conversion the input arguments|
| *accessor.*invokeWithIndex| | Directly invokes the method without validating/auto-conversion the input arguments|
| *accessor.*set/get| | Directly set/get the field without validating/auto-conversion the input arguments|

Other static fields:
* `ClassAccess.ACCESS_CLASS_PREFIX`: the prefix of the dynamic class name(format is `<prefix>.<underlying_class_full_name>`), the default value is `asm.`
* `ClassAccess.IS_CACHED`: The option to cache the method/constructor indexes and the dynamic classes, default is `true`. VM parameter `reflectasm.is_cache` can also control this option
* `ClassAccess.IS_STRICT_CONVERT`: controls the auto-conversion of the input arguments for the invoke/set/constructor functions, i.e., auto-conversion `String` into `int` when a method only accepts the `int` parameter. Default is `false`(enabled conversion), and VM parameter `reflectasm.is_strict_convert` can also control this option



## Visibility

ReflectASM can always access all members(public + non-public) that defined inside it + supper-classes + interfaces. 

To access a field/method defined in the class's super-class which is also samely defined in the class itself with the same parameters, then position the index firstly and invoke/get/set with it afterwards. For example:
```java
int fieldIndex=access.indexOfField(<superClass>,<fieldName>);
access.get(instance,fieldIndex);
access.set(instance,fieldIndex,value);

int methodIndex=access.indexOfMethod(<superClass>,<methodName>,{<parameterTypes>});
access.invokeWithIndex(instance,methodIndex,{arguments});
```

Refer to test case `com.hyee.reflectmeta.testOverload()/testOverloadWithLambda()` for more examples`


## Exceptions

Stack traces when using ReflectASM are a bit cleaner. Here is Java's reflection calling a method that throws a RuntimeException:

```
Exception in thread "main" java.lang.reflect.InvocationTargetException
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)
	at java.lang.reflect.Method.invoke(Method.java:597)
	at com.example.SomeCallingCode.doit(SomeCallingCode.java:22)
Caused by: java.lang.RuntimeException
	at com.example.SomeClass.someMethod(SomeClass.java:48)
	... 5 more
```

Here is the same but when ReflectASM is used:

```
Exception in thread "main" java.lang.RuntimeException
	at com.example.SomeClass.someMethod(SomeClass.java:48)
	at com.example.SomeClassMethodAccess.invoke(Unknown Source)
	at com.example.SomeCallingCode.doit(SomeCallingCode.java:22)
```

If ReflectASM is used to invoke code that throws a checked exception, the checked exception is thrown. Because it is a compilation error to use try/catch with a checked exception around code that doesn't declare that exception as being thrown, you must catch Exception if you care about catching a checked exception in code you invoke with ReflectASM.

## How it works

Each time to call `ClassAccess.access(<target class>)`, a wrapper class is created and instanced, or if the wrapper object can be found from the cache, then return from cache directly. This wrapper object is assigned as `ClassAccess.accessor`.

So any access(get/set field,invoke method,construction) to the target class, the process flow is:
```
User Calls 
  \/
  Is auto-conversion enabled?---Yes---> ClassAccess.reArgs&Call(invoke/get/set/etc)
                          \                 \/
                            -----No---> ClassAccess.accessor.callIndex(invoke/get/set/etc)
                                            \/
                                        TargetInstance.call(invoke/get/set/etc)
                                            \/
                                        Return result                                                              
```
The outstanding cost by comparing to direct call is only redirect once in minimum so the performance differences can be ignored(< 10 ns), the most time-consuming part is the target method itself.

To see how is the logic of the wrapper class, take below class `TestObject` as example:

```java
package test;
public class TestObject {
    static String fs;
    public Double fd;
    public int fi;
    private long fl;
    public TestObject() {fs = "TestObject0";}
    public TestObject(int fi1, Double fd1, String fs1, long l) {}
    static String func1(String str) {fs = str;return str;}
    public String func2(int fi1, Double fd1, String fs1, long l) {return fs;}
}
```

And then the auto-generated wrapper class is shown below, you can also call `ClassAccess.access(TestObject.class,".")` to dump the wrapper class:
```java
package asm.test;
import com.hyee.reflectmeta.Accessor;
import com.hyee.reflectmeta.ClassAccess;
import com.hyee.reflectmeta.ClassInfo;
import sun.reflect.MagicAccessorImpl;

public class TestObject extends MagicAccessorImpl implements Accessor<test.TestObject> {
    static final ClassInfo<test.TestObject> classInfo = new ClassInfo();

    public TestObject() {}

    static {
        classInfo.methodNames = new String[]{"func2", "func1"};
        classInfo.methodParamTypes = new Class[][]{{Integer.TYPE, Double.class, String.class, Long.TYPE}, {String.class}};
        classInfo.returnTypes = new Class[]{String.class, String.class};
        classInfo.methodModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(8)};
        classInfo.methodDescs = new String[]{"(ILjava/lang/Double;Ljava/lang/String;J)Ljava/lang/String;", "(Ljava/lang/String;)Ljava/lang/String;"};
        classInfo.fieldNames = new String[]{"fs", "fd", "fi", "fl"};
        classInfo.fieldTypes = new Class[]{String.class, Double.class, Integer.TYPE, Long.TYPE};
        classInfo.fieldModifiers = new Integer[]{Integer.valueOf(8), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(2)};
        classInfo.fieldDescs = new String[]{"Ljava/lang/String;", "Ljava/lang/Double;", "I", "J"};
        classInfo.constructorParamTypes = new Class[][]{new Class[0], {Integer.TYPE, Double.class, String.class, Long.TYPE}};
        classInfo.constructorModifiers = new Integer[]{Integer.valueOf(1), Integer.valueOf(1)};
        classInfo.constructorDescs = new String[]{"()V", "(ILjava/lang/Double;Ljava/lang/String;J)V"};
        classInfo.baseClass = test.TestObject.class;
        classInfo.isNonStaticMemberClass = false;
        classInfo.bucket = 13;
        ClassAccess.buildIndex(classInfo);
    }

    public ClassInfo<test.TestObject> getInfo() {
        return classInfo;
    }

    public <T, V> T invokeWithIndex(test.TestObject var1, int var2, V... var3) {
        switch(var2) {
        case 0:
            return var1.func2(((Integer)var3[0]).intValue(), (Double)var3[1], (String)var3[2], ((Long)var3[3]).longValue());
        case 1:
            return test.TestObject.func1((String)var3[0]);
        default:
            throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    public <T> T get(test.TestObject var1, int var2) {
        switch(var2) {
        case 0:
            return test.TestObject.fs;
        case 1:
            return var1.fd;
        case 2:
            return Integer.valueOf(var1.fi);
        case 3:
            return Long.valueOf(var1.fl);
        default:
            throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public <T, V> void set(test.TestObject var1, int var2, V var3) {
        switch(var2) {
        case 0:
            test.TestObject.fs = (String)var3;
            return;
        case 1:
            var1.fd = (Double)var3;
            return;
        case 2:
            var1.fi = ((Integer)var3).intValue();
            return;
        case 3:
            var1.fl = ((Long)var3).longValue();
            return;
        default:
            throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    public <T> test.TestObject newInstanceWithIndex(int var1, T... var2) {
        switch(var1) {
        case 0:
            return new test.TestObject();
        case 1:
            return new test.TestObject(((Integer)var2[0]).intValue(), (Double)var2[1], (String)var2[2], ((Long)var2[3]).longValue());
        default:
            throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    public test.TestObject newInstance() {
        return new test.TestObject();
    }
}
```
