package me.smecsia.smartfox.tools.util;

import org.apache.commons.lang.ArrayUtils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Util class allowing to scan all the classes inside the specified package and other class operations
 * User: isadykov
 * Date: 16.03.12
 * Time: 15:55
 */
public class ClassUtil {

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    public static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
    }

    /**
     * Searches for a certain annotation in the class hierarchy
     *
     * @param annotatedClass
     * @param annotationClass
     * @param <T>
     * @return
     */
    public static <T extends Annotation> T findAnnotationInClassHierarchy(Class<?> annotatedClass, Class<T> annotationClass) {
        T result = null;
        while (annotatedClass != null && result == null) {
            result = annotatedClass.getAnnotation(annotationClass);
            annotatedClass = annotatedClass.getSuperclass();
        }
        return result;
    }


    /**
     * Searches for all fields within class hierarchy
     *
     * @return
     */
    public static Field[] getFieldsInClassHierarchy(Class<?> clazz) {
        Field[] fields = {};
        while (clazz != null) {
            fields = (Field[]) ArrayUtils.addAll(fields, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * Searches for all methods within class hierarchy
     *
     * @return
     */
    public static Method[] getMethodsInClassHierarchy(Class<?> clazz) {
        Method[] methods = {};
        while (clazz != null) {
            methods = (Method[]) ArrayUtils.addAll(methods, clazz.getDeclaredMethods());
            clazz = clazz.getSuperclass();
        }
        return methods;
    }

    /**
     * Returns the generic arguments from the field
     *
     * @param field - field to be reflect
     * @return Type arguments array
     */
    public static Type[] getFieldTypeArguments(Field field) {
        Type genericFieldType = field.getGenericType();
        if (genericFieldType instanceof ParameterizedType) {
            ParameterizedType aType = (ParameterizedType) genericFieldType;
            return aType.getActualTypeArguments();
        }
        return new Type[]{};
    }
}
