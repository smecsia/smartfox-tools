package me.smecsia.smartfox.tools.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Ilya Sadykov
 * @version $Date$ $Revision$
 */
public class EnumUtil {

    private static final Map<Class<? extends Enum>, Set<String>> cache = new HashMap<Class<? extends Enum>, Set<String>>();

    /**
     * Checks that an String element is contained by the enumclass
     *
     * @param enumClass
     * @param value
     * @return true if enumClass contains value
     */
    public static boolean enumContains(Class<? extends Enum> enumClass, String value) {
        if (!cache.containsKey(enumClass)) {
            Set<String> options = new HashSet<String>();
            for (Enum opt : enumClass.getEnumConstants()) {
                options.add(String.valueOf(opt));
            }
            cache.put(enumClass, options);
        }
        return cache.get(enumClass).contains(value);
    }

    /**
     * Get the enum value from its strng value
     * @param enumClass
     * @param value
     * @param <T>
     * @return
     */
    public static <T extends Enum<T>> T fromString(Class<T> enumClass, String value){
        if(!enumContains(enumClass, value)){
            throw new IllegalArgumentException("Wrong value provided to the enum: " + enumClass + " : " + value + "!");
        }
        return Enum.valueOf(enumClass, value);
    }


    /**
     * Get the enum value from its ordinal value
     * @param enumClass
     * @param value
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> T fromOrdinal(Class<T> enumClass, Integer value) {
        Enum<T>[] values = (enumClass).getEnumConstants();
        if (value > values.length - 1) {
            throw new IllegalArgumentException("Wrong value provided for the enum " + enumClass + " : " + value + "!");
        }
        return (T) values[value];
    }
}
