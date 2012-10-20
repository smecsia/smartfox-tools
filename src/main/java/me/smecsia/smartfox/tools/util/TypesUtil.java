package me.smecsia.smartfox.tools.util;

import me.smecsia.smartfox.tools.error.MetadataException;

import java.util.*;

/**
 * User: smecsia
 * Date: 16.02.12
 * Time: 15:51
 */
public class TypesUtil {

    /**
     * Checks if the fieldType is integer
     *
     * @param type - java fieldType
     * @return true if the given fieldType is integer
     */
    public static boolean isInt(Class<?> type) {
        return Integer.class.isAssignableFrom(type) || (type.isPrimitive() && type.toString().equals("int"));

    }

    /**
     * Checks if the fieldType is double
     *
     * @param type - java fieldType
     * @return true if the given fieldType is double
     */
    public static boolean isDouble(Class<?> type) {
        return Double.class.isAssignableFrom(type) || (type.isPrimitive() && type.toString().equals("double"));
    }

    /**
     * Checks if the fieldType is float
     *
     * @param type - java fieldType
     * @return true if the given fieldType is float
     */
    public static boolean isFloat(Class<?> type) {
        return Float.class.isAssignableFrom(type) || (type.isPrimitive() && type.toString().equals("float"));
    }

    /**
     * Checks if the fieldType is Boolean
     *
     * @param type - java fieldType
     * @return true if the given fieldType is boolean
     */
    public static boolean isBoolean(Class<?> type) {
        return Boolean.class.isAssignableFrom(type) || (type.isPrimitive() && type.toString().equals("boolean"));
    }

    /**
     * Checks if the fieldType is long
     *
     * @param type - java fieldType
     * @return true if the given fieldType is long
     */
    public static boolean isString(Class<?> type) {
        return String.class.isAssignableFrom(type);
    }

    /**
     * Checks if the fieldType is long
     *
     * @param type - java fieldType
     * @return true if the given fieldType is long
     */
    public static boolean isLong(Class<?> type) {
        return Long.class.isAssignableFrom(type) || (type.isPrimitive() && type.toString().equals("long"));
    }

    /**
     * Instantiate the collection by its class
     * @param collClass
     * @return
     */
    public static Collection instantiateCollection(Class<? extends Collection> collClass) {
        if (List.class.isAssignableFrom(collClass)) {
            return new ArrayList();
        } else if (Set.class.isAssignableFrom(collClass)) {
            return new HashSet();
        } else {
            throw new MetadataException("Cannot instantiate collection of a class: " + collClass + ": Not " +
                    "supported!");
        }
    }
}
