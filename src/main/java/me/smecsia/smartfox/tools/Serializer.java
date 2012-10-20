package me.smecsia.smartfox.tools;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.smartfox.tools.annotations.SFSSerialize;
import me.smecsia.smartfox.tools.common.TransportObject;
import me.smecsia.smartfox.tools.error.MetadataException;
import me.smecsia.smartfox.tools.common.BasicService;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.WordUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        private Map<String, FieldMeta> entityFields = new HashMap<String, FieldMeta>();

        public Metadata(Class<T> entityClass) {
            this.entityClass = entityClass;
            readMetadata();
        }

        public Map<String, FieldMeta> getEntityFields() {
            return entityFields;
        }

        @SuppressWarnings("unchecked")
        private void readMetadata() {
            Field[] fields = entityClass.getDeclaredFields();
            Class superClass = entityClass.getSuperclass();
            while (superClass != null) {
                fields = (Field[]) ArrayUtils.addAll(fields, superClass.getDeclaredFields());
                superClass = superClass.getSuperclass();
            }
            for (Field field : fields) {
                // skip fields that are not mapped as serializable
                SFSSerialize annotation = field.getAnnotation(SFSSerialize.class);
                if (annotation == null) {
                    continue;
                }

                // building metadata
                FieldMeta meta = new FieldMeta(field.getName());
                try {
                    meta.setter = entityClass.getMethod("set" + WordUtils.capitalize(field.getName()), field.getType());
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    meta.getter = entityClass.getMethod("get" + WordUtils.capitalize(field.getName()));
                } catch (NoSuchMethodException ignored) {
                }
                meta.config = annotation;
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
                        if (TransportObject.class.isAssignableFrom((Class<?>) typeArgs[0])) {
                            meta.fieldType = FieldType.ENTITY_ARRAY;
                            meta.genericType = (Class<? extends TransportObject>) typeArgs[0];
                        } else if (isString((Class<?>) typeArgs[0])) {
                            meta.fieldType = FieldType.STRING_ARRAY;
                            meta.genericType = (Class<String>) typeArgs[0];
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
                            final ISFSArray stringArray = new SFSArray();
                            for (Object string : (Collection) value) {
                                safeAddString(stringArray, (String) string);
                            }
                            safePutSFSArray(result, fieldName, stringArray);
                            break;
                        case ENTITY_ARRAY:
                            final ISFSArray entityArray = new SFSArray();
                            for (Object entity : (Collection) value) {
                                safeAddSFSObject(entityArray, serialize((TransportObject) entity));
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
            if(instance == null){
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
                                    ((Collection) value).add(deserialize((Class<? extends TransportObject>) fieldMeta.genericType,
                                            (ISFSObject) wrapper.getObject()));
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
