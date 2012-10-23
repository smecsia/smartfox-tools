package me.smecsia.smartfox.tools;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.smartfox.tools.annotations.SFSCustomListItemDeserializer;
import me.smecsia.smartfox.tools.annotations.SFSCustomListItemSerializer;
import me.smecsia.smartfox.tools.annotations.SFSSerialize;
import me.smecsia.smartfox.tools.annotations.SFSSerializeStrategy;
import me.smecsia.smartfox.tools.common.BasicService;
import me.smecsia.smartfox.tools.common.TransportObject;
import me.smecsia.smartfox.tools.error.MetadataException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.WordUtils;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static me.smecsia.smartfox.tools.annotations.SFSSerializeStrategy.Strategy;
import static me.smecsia.smartfox.tools.util.ExceptionUtil.formatStackTrace;
import static me.smecsia.smartfox.tools.util.SFSObjectUtil.*;
import static me.smecsia.smartfox.tools.util.TypesUtil.*;


/**
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 3:32
 */
public class Serializer extends BasicService {

    private enum FieldType {
        LONG, INT, BOOL, FLOAT, DOUBLE, STRING, STRING_ARRAY, ENTITY, ENTITY_ARRAY, UNKNOWN
    }

    private static final Map<Class<? extends TransportObject>, Metadata> metaCache =
            new ConcurrentHashMap<Class<? extends TransportObject>, Metadata>();

    private class FieldMeta {
        public Method getter;
        public Method setter;
        public FieldType fieldType;
        public Class<?> type;
        public Class<?> genericType;
        public Method customSerializer;
        public Method customDeserializer;
        public final String name;
        public SFSSerialize config;

        private FieldMeta(String name) {
            this.name = name;
        }
    }

    /**
     * Returns the generic arguments from the field
     *
     * @param field - field to be reflect
     * @return Type arguments array
     */
    protected Type[] getFieldTypeArguments(Field field) {
        Type genericFieldType = field.getGenericType();
        if (genericFieldType instanceof ParameterizedType) {
            ParameterizedType aType = (ParameterizedType) genericFieldType;
            return aType.getActualTypeArguments();
        }
        return new Type[]{};
    }

    /**
     * Class that holds the metadata about the certain
     *
     * @param <T>
     */
    private class Metadata<T extends TransportObject> {
        private Class<T> entityClass;
        private Strategy serializeStrategy;
        private Map<String, FieldMeta> entityFields = new HashMap<String, FieldMeta>();

        public Metadata(Class<T> entityClass) {
            this.entityClass = entityClass;
            readMetadata();
        }

        public Map<String, FieldMeta> getEntityFields() {
            return entityFields;
        }


        private Field[] getAllFields() {
            Field[] fields = entityClass.getDeclaredFields();
            Class superClass = entityClass.getSuperclass();
            while (superClass != null) {
                fields = (Field[]) ArrayUtils.addAll(fields, superClass.getDeclaredFields());
                superClass = superClass.getSuperclass();
            }
            return fields;
        }

        private Method[] getAllMethods() {
            Method[] methods = entityClass.getDeclaredMethods();
            Class superClass = entityClass.getSuperclass();
            while (superClass != null) {
                methods = (Method[]) ArrayUtils.addAll(methods, superClass.getDeclaredMethods());
                superClass = superClass.getSuperclass();
            }
            return methods;
        }

        private Method findSetter(Field field) {
            try {
                return entityClass.getMethod("set" + WordUtils.capitalize(field.getName()), field.getType());
            } catch (NoSuchMethodException ignored) {
            }
            return null;
        }

        private Method findGetter(Field field) {
            try {
                return entityClass.getMethod("get" + WordUtils.capitalize(field.getName()));
            } catch (NoSuchMethodException ignored) {
            }
            return null;
        }

        private Method findCustomDeserializer(Method[] methods, Field field) {
            for (Method m : methods) {
                SFSCustomListItemDeserializer annotation = m.getAnnotation(SFSCustomListItemDeserializer.class);
                if (annotation != null && annotation.listName().equals(field.getName())) {
                    Type returnType = m.getReturnType();
                    Type[] paramTypes = m.getParameterTypes();
                    if (returnType.equals(TransportObject.class)
                            && paramTypes.length == 1
                            && paramTypes[0].equals(ISFSObject.class)) {
                        return m;
                    }
                    throw new MetadataException("The annotated method '" + m.getName() + "' cannot be used for " +
                            "custom deserealization! It must return TransportObject and receive ISFSObject!");
                }
            }
            return null;
        }

        private Method findCustomSerializer(Method[] methods, Field field) {
            for (Method m : methods) {
                SFSCustomListItemSerializer annotation = m.getAnnotation(SFSCustomListItemSerializer.class);
                if (annotation != null && annotation.listName().equals(field.getName())) {
                    Type returnType = m.getReturnType();
                    Type[] paramTypes = m.getParameterTypes();
                    if (returnType.equals(ISFSObject.class)
                            && paramTypes.length == 1
                            && paramTypes[0].equals(TransportObject.class)) {
                        return m;
                    }
                    throw new MetadataException("The annotated method '" + m.getName() + "' cannot be used for " +
                            "custom deserealization! It must return ISFSObject and receive TransportObject!");
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private void readMetadata() {
            final Field[] fields = getAllFields();
            final Method[] methods = getAllMethods();

            SFSSerializeStrategy sfsSerializeStrategy = entityClass.getAnnotation(SFSSerializeStrategy.class);
            if (sfsSerializeStrategy != null) {
                this.serializeStrategy = sfsSerializeStrategy.type();
            } else {
                this.serializeStrategy = Strategy.ALL_FIELDS;
            }

            for (Field field : fields) {
                // skip fields that are not mapped as serializable
                SFSSerialize annotation = field.getAnnotation(SFSSerialize.class);
                if (annotation == null && this.serializeStrategy.equals(Strategy.ANNOTATED_FIELDS)) {
                    continue;
                }

                // building metadata
                FieldMeta meta = new FieldMeta(field.getName());
                meta.getter = findGetter(field);
                meta.setter = findSetter(field);
                meta.config = (annotation != null) ? annotation : SFSSerialize.DEFAULT.get();
                meta.customDeserializer = findCustomDeserializer(methods, field);
                meta.customSerializer = findCustomSerializer(methods, field);
                meta.type = field.getType();
                if (TransportObject.class.isAssignableFrom(field.getType())) {
                    meta.fieldType = FieldType.ENTITY;
                } else if (isString(field.getType())) {
                    meta.fieldType = FieldType.STRING;
                } else if (isLong(field.getType())) {
                    meta.fieldType = FieldType.LONG;
                } else if (isDouble(field.getType())) {
                    meta.fieldType = FieldType.DOUBLE;
                } else if (isFloat(field.getType())) {
                    meta.fieldType = FieldType.FLOAT;
                } else if (isBoolean(field.getType())) {
                    meta.fieldType = FieldType.BOOL;
                } else if (isInt(field.getType())) {
                    meta.fieldType = FieldType.INT;
                } else if (Collection.class.isAssignableFrom(field.getType())) {
                    Type[] typeArgs = getFieldTypeArguments(field);
                    if (typeArgs.length == 1) {
                        Type genericType = typeArgs[0];
                        if (typeArgs[0] instanceof WildcardType) {
                            Type[] bounds = ((WildcardType) typeArgs[0]).getUpperBounds();
                            if (bounds.length == 1 && TransportObject.class.isAssignableFrom((Class<?>) bounds[0])) {
                                meta.fieldType = FieldType.ENTITY_ARRAY;
                                genericType = bounds[0];
                            }
                        }
                        if (TransportObject.class.isAssignableFrom((Class<?>) genericType)) {
                            meta.fieldType = FieldType.ENTITY_ARRAY;
                            meta.genericType = (Class<? extends TransportObject>) genericType;
                        } else if (isString((Class<?>) genericType)) {
                            meta.fieldType = FieldType.STRING_ARRAY;
                            meta.genericType = (Class<String>) genericType;
                        }
                    }
                } else {
                    meta.fieldType = FieldType.UNKNOWN;
                }
                if (meta.fieldType != FieldType.UNKNOWN) {
                    entityFields.put(field.getName(), meta);
                }
            }
        }

        private void checkField(String fieldName) {
            if (!getEntityFields().containsKey(fieldName)) {
                logAndThrow(new MetadataException("Cannot get/set the field " + fieldName + " to an object of type " +
                        entityClass + ": field is not known! "));
            }
        }

        public <T extends TransportObject> void set(T obj, String fieldName, Object value) {
            checkField(fieldName);
            try {
                FieldMeta fieldMeta = getEntityFields().get(fieldName);
                if (fieldMeta.setter != null) {
                    fieldMeta.setter.invoke(obj, value);
                }
            } catch (Exception e) {
                logAndThrow(new MetadataException("Cannot invoke setter for field " + fieldName + " for object of " +
                        "type " + entityClass + ":\n " + formatStackTrace(e)));
            }
        }

        public Object get(T obj, String fieldName) {
            try {
                Method getter = entityFields.get(fieldName).getter;
                if (getter != null) {
                    return getter.invoke(obj);
                }
            } catch (Exception e) {
                logAndThrow(e);
            }
            return null;
        }

    }

    @SuppressWarnings("unchecked")
    public <T extends TransportObject> ISFSObject serialize(T object) {
        if (object != null) {
            if (!metaCache.containsKey(object.getClass())) {
                metaCache.put(object.getClass(), new Metadata(object.getClass()));
            }
            ISFSObject result = new SFSObject();
            Metadata<T> metadata = metaCache.get(object.getClass());
            for (String fieldName : metadata.getEntityFields().keySet()) {
                FieldMeta fieldMeta = metadata.getEntityFields().get(fieldName);
                if (!fieldMeta.config.serialize()) {
                    continue;
                }
                Object value = metadata.get(object, fieldName);
                if (value == null) { // skip null values
                    continue;
                }
                try {
                    switch (fieldMeta.fieldType) {
                        case BOOL:
                            safePutBoolean(result, fieldName, (Boolean) value);
                            break;
                        case FLOAT:
                            safePutFloat(result, fieldName, (Float) value);
                            break;
                        case DOUBLE:
                            safePutDouble(result, fieldName, (Double) value);
                            break;
                        case INT:
                            safePutInt(result, fieldName, (Integer) value);
                            break;
                        case LONG:
                            safePutLong(result, fieldName, (Long) value);
                            break;
                        case STRING:
                            safePutString(result, fieldName, (String) value);
                            break;
                        case ENTITY:
                            safePutSFSObject(result, fieldName, serialize((TransportObject) value));
                            break;
                        case STRING_ARRAY:
                            result.putUtfStringArray(fieldName, (Collection<String>) value);
                            break;
                        case ENTITY_ARRAY:
                            final ISFSArray entityArray = new SFSArray();
                            for (Object entity : (Collection) value) {
                                ISFSObject serializedValue;
                                if (fieldMeta.customSerializer != null) {
                                    serializedValue = (ISFSObject) fieldMeta.customSerializer.invoke(object, entity);
                                } else {
                                    serializedValue = serialize((TransportObject) entity);
                                }
                                safeAddSFSObject(entityArray, serializedValue);
                            }
                            safePutSFSArray(result, fieldName, entityArray);
                            break;

                    }
                } catch (Exception e) {
                    logAndThrow(new MetadataException(e));
                }
            }
            return result;
        }
        return null;
    }


    @SuppressWarnings("unchecked")
    public <T extends TransportObject> T deserialize(T instance, ISFSObject object) {
        try {
            if (instance == null) {
                logAndThrow(new MetadataException("Cannot deserialize to a null instance!"));
            }
            if (!metaCache.containsKey(instance.getClass())) {
                metaCache.put(instance.getClass(), new Metadata(instance.getClass()));
            }
            Metadata<T> metadata = metaCache.get(instance.getClass());
            for (String fieldName : object.getKeys()) {
                if (metadata.getEntityFields().containsKey(fieldName)) {
                    FieldMeta fieldMeta = metadata.getEntityFields().get(fieldName);
                    if (!fieldMeta.config.deserialize()) {
                        continue;
                    }
                    Object value = null;
                    switch (fieldMeta.fieldType) {
                        case BOOL:
                            value = object.getBool(fieldName);
                            break;
                        case FLOAT:
                            value = object.getFloat(fieldName);
                            break;
                        case DOUBLE:
                            value = object.getDouble(fieldName);
                            break;
                        case INT:
                            value = object.getInt(fieldName);
                            break;
                        case LONG:
                            value = object.getLong(fieldName);
                            break;
                        case STRING:
                            value = object.getUtfString(fieldName);
                            break;
                        case ENTITY:
                            value = deserialize((Class<T>) fieldMeta.type, object.getSFSObject(fieldName));
                            break;
                        case STRING_ARRAY:
                            value = object.getUtfStringArray(fieldName);
                            break;
                        case ENTITY_ARRAY:
                            ISFSArray arrValue = object.getSFSArray(fieldName);
                            Iterator<SFSDataWrapper> iterator = arrValue.iterator();
                            value = instantiateCollection((Class<? extends Collection>) fieldMeta.type);
                            while (iterator.hasNext()) {
                                SFSDataWrapper wrapper = iterator.next();
                                if (wrapper.getTypeId() == SFSDataType.SFS_OBJECT) {
                                    if (fieldMeta.customDeserializer != null) {
                                        ((Collection) value).add(
                                                fieldMeta.customDeserializer.invoke(instance, wrapper.getObject())
                                        );
                                    } else if (fieldMeta.genericType != null && !fieldMeta.genericType.isInterface()
                                            && !Modifier.isAbstract(fieldMeta.genericType.getModifiers())) {
                                        ((Collection) value).add(
                                                deserialize(
                                                        (Class<? extends TransportObject>) fieldMeta.genericType,
                                                        (ISFSObject) wrapper.getObject()
                                                )
                                        );
                                    }
                                }
                            }
                            break;
                    }
                    metadata.set(instance, fieldName, value);
                }
            }
            return instance;
        } catch (Exception e) {
            logAndThrow(new MetadataException(e));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends TransportObject> T deserialize(Class<T> clazz, ISFSObject object) {
        if (object != null) {
            try {
                return deserialize(clazz.newInstance(), object);
            } catch (Exception e) {
                logAndThrow(e);
            }
        }
        return null;
    }

}
