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
* Supports methods/constructors with variable parameters
* Removes the harcodes of the package name
* Supports accessing non-public class/method/field

To support Java 7, just change the imports in class `ClassAccess` to add back the dependency of `asm-xxx.jar`

## Overview

ReflectASM is a very small Java library that provides high performance reflection by using code generation. An access class is generated to set/get fields, call methods, or create a new instance. The access class uses bytecode rather than Java's reflection, so it is much faster. It can also access primitive fields via bytecode to avoid boxing.

#### Summary of the cost(Smaller value means better performance)
VM: Java 8u112 x86<br/>
OS: Win10 x64<br/> 

| VM | Item | Direct | ReflectASM | Reflection |
| --- | --- |  ---------  |  ---------  |  ---------  |
| Server VM | Field Set+Get | 1.33 ns | 6.55 ns | 12.37 ns |
| Server VM | Method Call | 0.79 ns | 3.26 ns | 4.26 ns |
| Server VM | Constructor | 4.76 ns | 7.49 ns | 9.61 ns |
| Client VM | Field Set+Get | 2.60 ns | 12.43 ns | 203.03 ns |
| Client VM | Method Call | 2.15 ns | 7.79 ns | 47.56 ns |
| Client VM | Constructor | 66.10 ns | 69.23 ns | 147.64 ns |

#### Server VM
![](http://chart.apis.google.com/chart?chtt=&Java 1.8.0_112 x86(Server VM)&chs=700x183&chd=t:39798066,196364847,371049615,23751727,97825112,127880351,142828145,224693206,288327386&chds=0,371049615&chxl=0:|Constructor - Reflection|Constructor - ReflectASM|Constructor - Direct|Method Call - Reflection|Method Call - ReflectASM|Method Call - Direct|Field Set+Get - Reflection|Field Set+Get - ReflectASM|Field Set+Get - Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)

#### Client VM
![](http://chart.apis.google.com/chart?chtt=&Java 1.8.0_112 x86(Client VM)&chs=700x183&chd=t:78127351,373011028,6090870837,64646437,233816059,1426909603,1983121202,2076906030,4429104410&chds=0,6090870837&chxl=0:|Constructor - Reflection|Constructor - ReflectASM|Constructor - Direct|Method Call - Reflection|Method Call - ReflectASM|Method Call - Direct|Field Set+Get - Reflection|Field Set+Get - ReflectASM|Field Set+Get - Direct&cht=bhg&chbh=10&chxt=y&chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|663366|663399|6633CC|6633FF|666600|666633|666666)


## Usage

Method reflection with ReflectASM:

```java
SomeClass someObject = ...
MethodAccess access = MethodAccess.access(SomeClass.class);
access.invoke(someObject, "setName", "Awesome McLovin");
String name = (String)access.invoke(someObject, "getName");
```

Field reflection with ReflectASM:

```java
SomeClass someObject = ...
FieldAccess access = FieldAccess.access(SomeClass.class);
access.set(someObject, "name", "Awesome McLovin");
String name = (String)access.access(someObject, "name");
```

Constructor reflection with ReflectASM:

```java
ConstructorAccess<SomeClass> access = ConstructorAccess.access(SomeClass.class);
SomeClass someObject = access.newInstance();
```

Class reflection with ReflectASM(F=FieldAccess,M=MethodAccess,C=ConstructorAccess, {}=Array):

| ClassAccess.*method* | Equivalent | Discription |
| -------------------- | ---------- | ----------- | 
| *static* access(Class,[dump dir]) | (F/M/C).access | returns a wrapper object of the underlying class, in case of the 2nd parameter is specified, dumps the dynamic classes into the target folder |
| IndexesOf(name,type)| | returns an index array that matches the given name and type(`field`/`method`/`<new>`) |
| IndexOf(name,type)| | returns the first element of `IndexesOf`|
| IndexOfField(String/Field) | F.getIndex | returns the field index that matches the given input|
| IndexOfMethod(String/Method) | M.getIndex | returns the first method index that matches the given input|
| indexOfMethod(name,argCount/{argTypes}) | (M/C).getIndex | returns the first index that matches the given input|
| indexOfConstructor(constructor) | C.getIndex | returns the index that matches the given constructor|
| set(instance,Name/index,value) | F.set | Assign value to the specific field |
| set*Primative*(instance,index,value) | F.set*Primative* | Assign primitive value to the specific field, *primitive* here can be `Integer/Long/Double/etc`  |
| get(instance,name/index) | F.get | Get field value |
| get*Primative*(instance,index) | F.get*Primative* | Get field value and cast to target primitive type|
| get(instance,name/index, Class) | F.get | Get field value and cast to target class type|
| invoke(instance,name,{args}) | M.invoke | Executes  method |
| invokeWithIndex(instance,index,{args}) | M.invokeWithIndex | Executes method by specifying the exact index|
| invokeWithWithTypes(instance,name/index,{argTypes},{args}) | M.invokeWithWithTypes | Invokes method by specifying parameter types in order to position the accurate method|
| newInstance() | C.newInstance | Create underlying Object |
| newInstance({args}) | C.newInstance | Create underlying Object |
| newInstanceWithIndex(index,{args}) | C.newInstanceWithIndex | Create underlying Object with the specific constructor index|
| newInstanceWithTypes({argTypes},{args}) | C.newInstanceWithTypes | Create underlying Object by specifying parameter types in order to position the accurate constructor|
| getInfo || Get the underlying `ClassInfo`|
| *accessor.*newInstanceWithIndex| | Directly initializes the instance without validating/auto-casting the input arguments|
| *accessor.*invokeWithIndex| | Directly invokes the method without validating/auto-casting the input arguments|
| *accessor.*set/get| | Directly set/get the field without validating/auto-casting the input arguments|

Other static fields:
* `ClassAccess.ACCESS_CLASS_PREFIX`: the prefix of the dynamic class name(format is `<prefix>.<underlying_class_full_name>`), the default value is `asm.`
* `ClassAccess.IS_CACHED`: The option to cache the method/constructor indexes and the dynamic classes, default is `true`. VM parameter `reflectasm.is_cache` can also control this option
* `ClassAccess.IS_STRICT_CONVERT`: controls the auto-conversion of the input arguments for the invoke/set/constructor functions, i.e., auto-cast `String` into `int` when a method only accepts the `int` parameter. Default is `false`(enabled conversion), and VM parameter `reflectasm.is_strict_convert` can also control this option

## Avoiding Name Lookup

For maximum performance when methods or fields are accessed repeatedly, the method or field index should be used instead of the name:

```java
SomeClass someObject = ...
MethodAccess access = MethodAccess.access(SomeClass.class);
int addNameIndex = access.getIndex("addName");
for (String name : names)
    access.invokeWithIndex(someObject, addNameIndex, "Awesome McLovin");
```

Iterate all fields:

```java
FieldAccess access = FieldAccess.access(SomeClass.class);
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