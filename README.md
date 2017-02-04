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
* Accuratly position the closest method for overloading  

## Overview

ReflectASM is a very small Java library that provides high performance reflection by using code generation. An access class is generated to set/get fields, call methods, or create a new instance. The access class uses bytecode rather than Java's reflection, so it is much faster. It can also access primitive fields via bytecode to avoid boxing.

## Performance
Test OS : Win10 x64<br/>
Test JVM: Java 8u112 x86
#### Server VM

![](http://chart.apis.google.com/chart?chtt=Method Call&chs=700x75&chd=t:25212544,139758792,181607250&chds=0,181607250&chxl=0:|Reflection|MethodAccess|Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)

![](http://chart.apis.google.com/chart?chtt=Field Set/Get&chs=700x75&chd=t:34999471,404592751,426401708&chds=0,426401708&chxl=0:|FieldAccess|Reflection|Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)

![](http://chart.apis.google.com/chart?chtt=Constructor&chs=700x75&chd=t:155610141,262694270,282406869&chds=0,282406869&chxl=0:|Reflection|ConstructorAccess|Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)

#### Client VM

![](http://chart.apis.google.com/chart?chtt=Method Call&chs=700x75&chd=t:67050358,191822572,1501951152&chds=0,1501951152&chxl=0:|Reflection|MethodAccess|Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)

![](http://chart.apis.google.com/chart?chtt=Field Set/Get&chs=700x75&chd=t:81317752,321833969,6215017823&chds=0,6215017823&chxl=0:|Reflection|FieldAccess|Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)

![](http://chart.apis.google.com/chart?chtt=Constructor&chs=700x75&chd=t:4716682366,2100766928,2131200029&chds=0,4716682366&chxl=0:|ConstructorAccess|Direct|Reflection&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)


The source code for these benchmarks is included in the project. 

## Usage

Method reflection with ReflectASM:

```java
SomeClass someObject = ...
MethodAccess access = MethodAccess.get(SomeClass.class);
access.invoke(someObject, "setName", "Awesome McLovin");
String name = (String)access.invoke(someObject, "getName");
```

Field reflection with ReflectASM:

```java
SomeClass someObject = ...
FieldAccess access = FieldAccess.get(SomeClass.class);
access.set(someObject, "name", "Awesome McLovin");
String name = (String)access.get(someObject, "name");
```

Constructor reflection with ReflectASM:

```java
ConstructorAccess<SomeClass> access = ConstructorAccess.get(SomeClass.class);
SomeClass someObject = access.newInstance();
```

## Avoiding Name Lookup

For maximum performance when methods or fields are accessed repeatedly, the method or field index should be used instead of the name:

```java
SomeClass someObject = ...
MethodAccess access = MethodAccess.get(SomeClass.class);
int addNameIndex = access.getIndex("addName");
for (String name : names)
    access.invoke(someObject, addNameIndex, "Awesome McLovin");
```

Iterate all fields:

```java
FieldAccess access = FieldAccess.get(SomeClass.class);
for(int i = 0, n = access.getFieldCount(); i < n; i++) {
    access.set(instanceObject, i, valueToPut);              
}
```

## Visibility

ReflectASM can always access public members. An attempt is made to define access classes in the same classloader (using setAccessible) and package as the accessed class. If the security manager allows setAccessible to succeed, then protected and default access (package private) members can be accessed. If setAccessible fails, no exception is thrown, but only public members can be accessed. Private members can never be accessed.

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
