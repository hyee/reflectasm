package com.esotericsoftware.reflectasm.util;

/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Miscellaneous utility methods for number conversion and parsing.
 * <p>Mainly for internal use within the framework; consider Apache's
 * Commons Lang for a more comprehensive suite of number utilities.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 1.1.2
 */
public abstract class NumberUtils {
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    /**
     * Standard number types (all immutable):
     * Byte, Short, Integer, Long, BigInteger, Float, Double, BigDecimal.
     */
    public static final Set<Class<?>> STANDARD_NUMBER_TYPES;
    public static final Map<String, Class<?>> namePrimitiveMap = new HashMap<>();

    static {
        namePrimitiveMap.put("boolean", Boolean.class);
        namePrimitiveMap.put("byte", Byte.class);
        namePrimitiveMap.put("char", Character.class);
        namePrimitiveMap.put("short", Short.class);
        namePrimitiveMap.put("int", Integer.class);
        namePrimitiveMap.put("long", Long.class);
        namePrimitiveMap.put("double", Double.class);
        namePrimitiveMap.put("float", Float.class);
        namePrimitiveMap.put("void", Void.class);

        Set<Class<?>> numberTypes = new HashSet<>(8);
        numberTypes.add(Byte.class);
        numberTypes.add(Byte.TYPE);
        numberTypes.add(Short.class);
        numberTypes.add(Short.TYPE);
        numberTypes.add(Integer.class);
        numberTypes.add(Integer.TYPE);
        numberTypes.add(Long.class);
        numberTypes.add(Long.TYPE);
        numberTypes.add(BigInteger.class);
        numberTypes.add(Float.class);
        numberTypes.add(Float.TYPE);
        numberTypes.add(Double.class);
        numberTypes.add(Double.TYPE);
        numberTypes.add(BigDecimal.class);
        STANDARD_NUMBER_TYPES = Collections.unmodifiableSet(numberTypes);
    }

    private static <T> T convertOrGetDistance(Object from, Class toClass, boolean isGetDistance) {
        if (from == null || toClass == null) return (T) (isGetDistance ? Integer.valueOf(5) : null);
        Class clz;
        if (!(from instanceof Class)) clz = from.getClass();
        else clz = (Class) from;
        if (clz == toClass || toClass.isAssignableFrom(clz)) return (T) (isGetDistance ? Integer.valueOf(5) : from);
        if (toClass.isArray() && clz.isArray()) {
            int distance = 5;
            Class<?> baseClass = toClass.getComponentType();
            Object objects=null;
            if(!isGetDistance) objects=Array.newInstance(baseClass,Array.getLength(from));
            for (int i = 0; i < Array.getLength(from); i++) {
                if (isGetDistance) distance = Math.min(distance, convertOrGetDistance(Array.get(from,i), baseClass, true));
                else Array.set(objects,i,convertOrGetDistance(Array.get(from,i), baseClass, false));
            }
            return (T) (isGetDistance ? distance : objects);
        }
        if (toClass == String.class) return (T) (isGetDistance ? Integer.valueOf(2) : String.valueOf(from));
        if (STANDARD_NUMBER_TYPES.contains(toClass)) {
            if (STANDARD_NUMBER_TYPES.contains(clz))
                return (T) (isGetDistance ? Integer.valueOf(4) : convertNumberToTargetClass((Number) from, toClass));
            if (clz == String.class)
                return (T) (isGetDistance ? Integer.valueOf(1) : parseNumber((String) from, toClass));
        }
        if(namePrimitiveMap.containsKey(toClass.getName())&&namePrimitiveMap.get(toClass.getName())==clz) {
            return (T) (isGetDistance ? Integer.valueOf(5) : from);
        }
        if(namePrimitiveMap.containsKey(clz.getName())&&namePrimitiveMap.get(clz.getName())==toClass) {
            return (T) (isGetDistance ? Integer.valueOf(5) : from);
        }
        return (T) (isGetDistance ? Integer.valueOf(0) : toClass.cast(from));
    }

    public static <T> T convert(Object from, Class toClass) {
        return convertOrGetDistance(from, toClass, false);
    }

    public static int getDistance(Object from, Class toClass) {
        return (Integer) convertOrGetDistance(from, toClass, true);
    }

    /**
     * Convert the given number into an instance of the given target class.
     *
     * @param number      the number to convert
     * @param targetClass the target class to convert to
     * @return the converted number
     * @throws IllegalArgumentException if the target class is not supported
     *                                  (i.e. not a standard Number subclass as included in the JDK)
     * @see java.lang.Byte
     * @see java.lang.Short
     * @see java.lang.Integer
     * @see java.lang.Long
     * @see java.math.BigInteger
     * @see java.lang.Float
     * @see java.lang.Double
     * @see java.math.BigDecimal
     */
    @SuppressWarnings("unchecked")
    public static <T extends Number> T convertNumberToTargetClass(Number number, Class<T> targetClass) throws IllegalArgumentException {
        if (number == null) return null;
        Number to = null;
        if (targetClass.isInstance(number)) {
            return (T) number;
        }
        Class clz = targetClass;
        if (clz.isPrimitive() && STANDARD_NUMBER_TYPES.contains(clz)) clz = namePrimitiveMap.get(clz.getName());
        if (Byte.class == clz) {
            long value = checkedLongValue(number, clz);
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                raiseOverflowException(number, clz);
            }
            to = Byte.valueOf(number.byteValue());
        } else if (Short.class == clz) {
            long value = checkedLongValue(number, clz);
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                raiseOverflowException(number, clz);
            }
            to = Short.valueOf(number.shortValue());
        } else if (Integer.class == clz) {
            long value = checkedLongValue(number, clz);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                raiseOverflowException(number, clz);
            }
            to = Integer.valueOf(number.intValue());
        } else if (Long.class == clz) {
            long value = checkedLongValue(number, clz);
            to = Long.valueOf(value);
        } else if (BigInteger.class == clz) {
            if (number instanceof BigDecimal) {
                // do not lose precision - use BigDecimal's own conversion
                to = ((BigDecimal) number).toBigInteger();
            } else {
                // original value is not a Big* number - use standard long conversion
                to = BigInteger.valueOf(number.longValue());
            }
        } else if (Float.class == clz) {
            to = Float.valueOf(number.floatValue());
        } else if (Double.class == clz) {
            to = Double.valueOf(number.doubleValue());
        } else if (BigDecimal.class == clz) {
            // always use BigDecimal(String) here to avoid unpredictability of BigDecimal(double)
            // (see BigDecimal javadoc for details)
            to = new BigDecimal(number.toString());
        }
        if (to == null) {
            throw new IllegalArgumentException("Could not convert number [" + number + "] of type [" + number.getClass().getName() + "] to unsupported target class [" + clz.getName() + "]");
        }
        return (T) to;
    }

    /**
     * Check for a {@code BigInteger}/{@code BigDecimal} long overflow
     * before returning the given number as a long value.
     *
     * @param number      the number to convert
     * @param targetClass the target class to convert to
     * @return the long value, if convertible without overflow
     * @throws IllegalArgumentException if there is an overflow
     * @see #raiseOverflowException
     */
    private static long checkedLongValue(Number number, Class<? extends Number> targetClass) {
        BigInteger bigInt = null;
        if (number instanceof BigInteger) {
            bigInt = (BigInteger) number;
        } else if (number instanceof BigDecimal) {
            bigInt = ((BigDecimal) number).toBigInteger();
        }
        // Effectively analogous to JDK 8's BigInteger.longValueExact()
        if (bigInt != null && (bigInt.compareTo(LONG_MIN) < 0 || bigInt.compareTo(LONG_MAX) > 0)) {
            raiseOverflowException(number, targetClass);
        }
        return number.longValue();
    }

    /**
     * Raise an <em>overflow</em> exception for the given number and target class.
     *
     * @param number      the number we tried to convert
     * @param targetClass the target class we tried to convert to
     * @throws IllegalArgumentException if there is an overflow
     */
    private static void raiseOverflowException(Number number, Class<?> targetClass) {
        throw new IllegalArgumentException("Could not convert number [" + number + "] of type [" + number.getClass().getName() + "] to target class [" + targetClass.getName() + "]: overflow");
    }

    /**
     * Parse the given {@code text} into a {@link Number} instance of the given
     * target class, using the corresponding {@code decode} / {@code valueOf} method.
     * <p>Trims the input {@code String} before attempting to parse the number.
     * <p>Supports numbers in hex format (with leading "0x", "0X", or "#") as well.
     *
     * @param text        the text to convert
     * @param targetClass the target class to parse into
     * @return the parsed number
     * @throws IllegalArgumentException if the target class is not supported
     *                                  (i.e. not a standard Number subclass as included in the JDK)
     * @see Byte#decode
     * @see Short#decode
     * @see Integer#decode
     * @see Long#decode
     * @see #decodeBigInteger(String)
     * @see Float#valueOf
     * @see Double#valueOf
     * @see java.math.BigDecimal#BigDecimal(String)
     */
    @SuppressWarnings("unchecked")
    public static <T extends Number> T parseNumber(String text, Class<T> targetClass) {
        if (text == null) return null;
        String trimmed = text.replace(" ", "");
        if (trimmed.equals("")) return null;
        Class clz = targetClass;
        if (clz.isPrimitive()) clz = namePrimitiveMap.get(clz.getName());
        if (Byte.class == clz) {
            return (T) (isHexNumber(trimmed) ? Byte.decode(trimmed) : Byte.valueOf(trimmed));
        } else if (Short.class == clz) {
            return (T) (isHexNumber(trimmed) ? Short.decode(trimmed) : Short.valueOf(trimmed));
        } else if (Integer.class == clz) {
            return (T) (isHexNumber(trimmed) ? Integer.decode(trimmed) : Integer.valueOf(trimmed));
        } else if (Long.class == clz) {
            return (T) (isHexNumber(trimmed) ? Long.decode(trimmed) : Long.valueOf(trimmed));
        } else if (BigInteger.class == clz) {
            return (T) (isHexNumber(trimmed) ? decodeBigInteger(trimmed) : new BigInteger(trimmed));
        } else if (Float.class == clz) {
            return (T) Float.valueOf(trimmed);
        } else if (Double.class == clz) {
            return (T) Double.valueOf(trimmed);
        } else if (BigDecimal.class == clz || Number.class == clz) {
            return (T) new BigDecimal(trimmed);
        } else {
            throw new IllegalArgumentException("Cannot convert String [" + text + "] to target class [" + clz.getName() + "]");
        }
    }

    /**
     * Determine whether the given {@code value} String indicates a hex number,
     * i.e. needs to be passed into {@code Integer.decode} instead of
     * {@code Integer.valueOf}, etc.
     */
    private static boolean isHexNumber(String value) {
        int index = (value.startsWith("-") ? 1 : 0);
        return (value.startsWith("0x", index) || value.startsWith("0X", index) || value.startsWith("#", index));
    }

    /**
     * Decode a {@link java.math.BigInteger} from the supplied {@link String} value.
     * <p>Supports decimal, hex, and octal notation.
     *
     * @see BigInteger#BigInteger(String, int)
     */
    private static BigInteger decodeBigInteger(String value) {
        int radix = 10;
        int index = 0;
        boolean negative = false;

        // Handle minus sign, if present.
        if (value.startsWith("-")) {
            negative = true;
            index++;
        }

        // Handle radix specifier, if present.
        if (value.startsWith("0x", index) || value.startsWith("0X", index)) {
            index += 2;
            radix = 16;
        } else if (value.startsWith("#", index)) {
            index++;
            radix = 16;
        } else if (value.startsWith("0", index) && value.length() > 1 + index) {
            index++;
            radix = 8;
        }

        BigInteger result = new BigInteger(value.substring(index), radix);
        return (negative ? result.negate() : result);
    }
}