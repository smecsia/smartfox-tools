package me.smecsia.smartfox.tools.serialize;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.smartfox.tools.annotations.*;
import me.smecsia.smartfox.tools.common.BasicService;
import me.smecsia.smartfox.tools.common.TransportObject;
import me.smecsia.smartfox.tools.error.MetadataException;
import me.smecsia.smartfox.tools.util.EnumUtil;
import me.smecsia.smartfox.tools.util.ExceptionUtil;
import org.apache.commons.lang.WordUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.smecsia.smartfox.tools.util.ClassUtil.*;
import static me.smecsia.smartfox.tools.util.SFSObjectUtil.*;
import static me.smecsia.smartfox.tools.util.TypesUtil.*;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * @author Ilya Sadykov
 */
public class SFSSerializer extends BasicService {

    private enum FieldType {
        LONG, INT, BOOL, FLOAT, DOUBLE, STRING, STRING_ARRAY, LONG_ARRAY, ENUM_ARRAY, ENTITY, ENTITY_ARRAY, ENUM, UNKNOWN
    }

    private static final Map<Class<? extends TransportObject>, Metadata> metaCache =
            new ConcurrentHashMap<Class<? extends TransportObject>, Metadata>();
    private final List<SFSSerializePreProcessor> preProcessors = Collections.synchronizedList(new
            ArrayList<SFSSerializePreProcessor>());
    private final List<SFSSerializePostProcessor> postProcessors = Collections.synchronizedList(new
            ArrayList<SFSSerializePostProcessor>());

    private class FieldMeta {
        public Method getter;
        public Method setter;
        public FieldType fieldType;
        public Class<?> type;
        public Class<?> genericType;
        public Method customFieldSerializer;
        public Method customFieldDeserializer;
        public Method customListItemSerializer;
        public Method customListItemDeserializer;
        public Method customListItemInitializer;
        public Field field;
        public final String name;
        public SFSSerialize config;

        private FieldMeta(String name) {
            this.name = name;
        }
    }

    /**
     * Registers serialize processor
     *
     * @param processor processor to be registered
     */
    public void registerProcessor(SFSSerializeProcessor processor) {
        registerPreProcessor(processor);
        registerPostProcessor(processor);
    }

    /**
     * Registers the post-serialize processor for the transport object class
     *
     * @param processor processor to be registered
     */
    public void registerPreProcessor(SFSSerializePreProcessor processor) {
        preProcessors.add(processor);
    }

    /**
     * Registers the pre-serialize processor for the transport object class
     *
     * @param processor processor to be registered
     */
    public void registerPostProcessor(SFSSerializePostProcessor processor) {
        postProcessors.add(processor);
    }

    /**
     * Read fields options for the class
     *
     * @param clazz
     * @param <T>
     * @return options for each field
     */
    public <T extends TransportObject> Map<String, String[]> getFieldsOptions(Class<T> clazz) {
        return getMetadata(clazz).fieldsOptions;
    }

    /**
     * Class that holds the metadata about the certain
     *
     * @param <T>
     */
    private class Metadata<T extends TransportObject> {
        private Class<T> entityClass;
        private SFSSerializeStrategy.Strategy serializeStrategy;
        private Map<String, FieldMeta> entityFields = new HashMap<String, FieldMeta>();
        public Map<String, String[]> fieldsOptions = new HashMap<String, String[]>();

        public Metadata(Class<T> entityClass) {
            this.entityClass = entityClass;
            readMetadata();
        }

        public Map<String, FieldMeta> getEntityFields() {
            return entityFields;
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
                if (Boolean.TYPE.equals(field.getType())) {
                    try {
                        return entityClass.getMethod("is" + WordUtils.capitalize(field.getName()));
                    } catch (NoSuchMethodException ignoredBoolean) {
                        //ignore
                    }
                }
            }
            return null;
        }

        private Method findCustomListItemInitializer(Method[] methods, Field field) {
            for (Method m : methods) {
                SFSCustomListItemInitializer annotation = m.getAnnotation(SFSCustomListItemInitializer.class);
                if (annotation != null && annotation.listName().equals(field.getName())) {
                    return validateCustomListItemInitializerMethod(m);
                }
            }
            return null;
        }

        private Method validateCustomListItemInitializerMethod(Method m) {
            Type returnType = m.getReturnType();
            Type[] paramTypes = m.getParameterTypes();
            if (!(returnType.equals(TransportObject.class) && paramTypes.length == 1 && paramTypes[0].equals(ISFSObject.class))) {
                throw new MetadataException("The annotated method '" + m.getName() + "' cannot be used for " +
                        "custom list item deserialization! It must return TransportObject and receive ISFSObject!");
            }
            return m;
        }

        private Method findCustomListItemDeserializer(Method[] methods, Field field) {
            for (Method m : methods) {
                SFSCustomListItemDeserializer annotation = m.getAnnotation(SFSCustomListItemDeserializer.class);
                if (annotation != null && annotation.listName().equals(field.getName())) {
                    return validateCustomListItemInitializerMethod(m);
                }
            }
            return null;
        }

        private Method findCustomListItemSerializer(Method[] methods, Field field) {
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
                            "custom list item serialization! It must return ISFSObject and receive TransportObject!");
                }
            }
            return null;
        }

        private Method findCustomFieldDeserializer(Method[] methods, Field field) {
            for (Method m : methods) {
                SFSCustomFieldDeserializer annotation = m.getAnnotation(SFSCustomFieldDeserializer.class);
                if (annotation != null && annotation.fieldName().equals(field.getName())) {
                    Type returnType = m.getReturnType();
                    Type[] paramTypes = m.getParameterTypes();
                    if (returnType.equals(field.getType())
                            && paramTypes.length == 1
                            && paramTypes[0].equals(SFSDataWrapper.class)) {
                        return m;
                    }
                    throw new MetadataException("The annotated method '" + m.getName() + "' cannot be used for " +
                            "custom field serialization! It must return " + field.getType() + " and receive " +
                            "SFSDataWrapper!");
                }
            }
            return null;
        }

        private Method findCustomFieldSerializer(Method[] methods, Field field) {
            for (Method m : methods) {
                SFSCustomFieldSerializer annotation = m.getAnnotation(SFSCustomFieldSerializer.class);
                if (annotation != null && annotation.fieldName().equals(field.getName())) {
                    Type returnType = m.getReturnType();
                    Type[] paramTypes = m.getParameterTypes();
                    if (returnType.equals(SFSDataWrapper.class)
                            && paramTypes.length == 1
                            && paramTypes[0].equals(field.getType())) {
                        return m;
                    }
                    throw new MetadataException("The annotated method '" + m.getName() + "' cannot be used for " +
                            "custom field serialization! It must return SFSDataWrapper and receive " + field.getType() + "!");
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private void readMetadata() {
            final Field[] fields = getFieldsInClassHierarchy(entityClass);
            final Method[] methods = getMethodsInClassHierarchy(entityClass);

            SFSSerializeStrategy sfsSerializeStrategy = findAnnotationInClassHierarchy(entityClass, SFSSerializeStrategy.class);
            SFSSerializeIgnore ignoreClassFields = entityClass.getAnnotation(SFSSerializeIgnore.class);
            if (sfsSerializeStrategy != null) {
                this.serializeStrategy = sfsSerializeStrategy.type();
            } else {
                this.serializeStrategy = SFSSerializeStrategy.Strategy.DEFAULT;
            }

            for (Field field : fields) {
                // skip fields that are listed in the ignoreClasFields
                if (ignoreClassFields != null && Arrays.asList(ignoreClassFields.fields()).contains(field.getName())) {
                    continue;
                }

                // skip fields that are not mapped as serializable
                SFSSerialize annotation = field.getAnnotation(SFSSerialize.class);
                if ((annotation == null && this.serializeStrategy.equals(SFSSerializeStrategy.Strategy.ANNOTATED_FIELDS)) ||
                        field.getAnnotation(SFSSerializeIgnore.class) != null) {
                    continue;
                }

                // building metadata
                final SFSSerialize config = (annotation != null) ? annotation : SFSSerialize.DEFAULT.get();

                FieldMeta meta = new FieldMeta((!isEmpty(config.name())) ? config.name() : field.getName());
                meta.getter = findGetter(field);
                meta.setter = findSetter(field);

                if (meta.getter == null || meta.setter == null) {
                    field.setAccessible(true);
                }
                meta.config = config;

                meta.customFieldDeserializer = findCustomFieldDeserializer(methods, field);
                meta.customFieldSerializer = findCustomFieldSerializer(methods, field);
                meta.type = field.getType();
                meta.field = field;
                meta.fieldType = FieldType.UNKNOWN;
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
                } else if (Enum.class.isAssignableFrom(field.getType())) {
                    meta.fieldType = FieldType.ENUM;
                    meta.genericType = field.getType();
                } else if (Collection.class.isAssignableFrom(field.getType())) {
                    meta.customListItemDeserializer = findCustomListItemDeserializer(methods, field);
                    meta.customListItemSerializer = findCustomListItemSerializer(methods, field);
                    meta.customListItemInitializer = findCustomListItemInitializer(methods, field);
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
                        } else if (isLong((Class<?>) genericType)) {
                            meta.fieldType = FieldType.LONG_ARRAY;
                            meta.genericType = (Class<String>) genericType;
                        } else if (Enum.class.isAssignableFrom((Class<?>) genericType)) {
                            meta.fieldType = FieldType.ENUM_ARRAY;
                            meta.genericType = (Class<Enum>) genericType;
                        }
                    }
                }
                if (meta.fieldType != FieldType.UNKNOWN) {
                    entityFields.put(meta.name, meta);
                    fieldsOptions.put(meta.name, meta.config.options());
                }
            }
        }

        private void checkField(String fieldName) {
            if (!getEntityFields().containsKey(fieldName)) {
                logAndThrow(new MetadataException("Cannot get/set the field " + fieldName + " to an object of type " +
                        entityClass + ": field is not known! "));
            }
        }

        /**
         * Set the field's value for an instance of an object
         *
         * @param obj       intance of entity
         * @param fieldName field name
         * @param value     field value
         * @param <T>       instance type
         */
        public <T extends TransportObject> void set(T obj, String fieldName, Object value) {
            checkField(fieldName);
            try {
                FieldMeta fieldMeta = getEntityFields().get(fieldName);
                if (fieldMeta.setter != null) {
                    fieldMeta.setter.invoke(obj, value);
                } else {
                    fieldMeta.field.set(obj, value);
                }
            } catch (Exception e) {
                logAndThrow(new MetadataException("Cannot set field " + fieldName + " for object of " +
                        "type " + entityClass + ":\n " + ExceptionUtil.formatStackTrace(e)));
            }
        }

        /**
         * Get the field's value for an instance of an object
         *
         * @param obj       instance of entity
         * @param fieldName name of the field
         */
        public Object get(T obj, String fieldName) {
            try {
                Method getter = entityFields.get(fieldName).getter;
                if (getter != null) {
                    return getter.invoke(obj);
                } else {
                    return getEntityFields().get(fieldName).field.get(obj);
                }
            } catch (Exception e) {
                logAndThrow(new MetadataException("Cannot get field " + fieldName + " for object of " +
                        "type " + entityClass + ":\n " + ExceptionUtil.formatStackTrace(e)));
            }
            return null;
        }

    }

    private <T extends TransportObject> void applyPreProcessors(T object) {
        for (SFSSerializePreProcessor processor : preProcessors) {
            processor.process(object);
        }
    }

    private <T extends TransportObject> void applyPostProcessors(ISFSObject result, T object) {
        for (SFSSerializePostProcessor processor : postProcessors) {
            processor.process(result, object);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends TransportObject> Metadata<T> getMetadata(Class<T> clazz) {
        if (!metaCache.containsKey(clazz)) {
            metaCache.put(clazz, new Metadata(clazz));
        }
        return metaCache.get(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T extends TransportObject> ISFSObject serialize(T instance) {
        if (instance != null) {
            ISFSObject result = new SFSObject();
            Metadata<T> metadata = (Metadata<T>) getMetadata(instance.getClass());
            applyPreProcessors(instance);
            for (String fieldName : metadata.getEntityFields().keySet()) {
                FieldMeta fieldMeta = metadata.getEntityFields().get(fieldName);
                if (!fieldMeta.config.serialize()) {
                    continue;
                }
                Object value = metadata.get(instance, fieldName);
                if (value == null) { // skip null values
                    continue;
                }
                try {
                    if (fieldMeta.customFieldSerializer != null) {
                        safePutValue(result, fieldName, (SFSDataWrapper) fieldMeta.customFieldSerializer.invoke(instance, value));
                    } else switch (fieldMeta.fieldType) {
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
                        case ENUM:
                            safePutString(result, fieldName, ((Enum) value).name());
                            break;
                        case STRING_ARRAY:
                            result.putUtfStringArray(fieldName, (Collection<String>) value);
                            break;
                        case ENUM_ARRAY:
                            result.putUtfStringArray(fieldName, EnumUtil.toStringCollection((Collection<Enum>) value));
                            break;
                        case LONG_ARRAY:
                            result.putLongArray(fieldName, (Collection<Long>) value);
                            break;
                        case ENTITY_ARRAY:
                            final ISFSArray entityArray = new SFSArray();
                            for (Object entity : (Collection) value) {
                                ISFSObject serializedValue;
                                if (fieldMeta.customListItemSerializer != null) {
                                    serializedValue = (ISFSObject) fieldMeta.customListItemSerializer.invoke(instance, entity);
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
            applyPostProcessors(result, instance);
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
            Metadata<T> metadata = (Metadata<T>) getMetadata(instance.getClass());
            for (String fieldName : object.getKeys()) {
                if (metadata.getEntityFields().containsKey(fieldName)) {
                    FieldMeta fieldMeta = metadata.getEntityFields().get(fieldName);
                    if (!fieldMeta.config.deserialize()) {
                        continue;
                    }
                    Object value = null;
                    if (fieldMeta.customFieldDeserializer != null) {
                        value = fieldMeta.customFieldDeserializer.invoke(instance, object.get(fieldName));
                    } else switch (fieldMeta.fieldType) {
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
                        case ENUM:
                            value = EnumUtil.fromString((Class<Enum>) fieldMeta.genericType, object.getUtfString(fieldName));
                            break;
                        case STRING_ARRAY:
                            value = object.getUtfStringArray(fieldName);
                            break;
                        case LONG_ARRAY:
                            value = object.getLongArray(fieldName);
                            break;
                        case ENUM_ARRAY:
                            value = EnumUtil.fromStringCollection((Class<Enum>) fieldMeta.genericType, object.getUtfStringArray(fieldName));
                            break;
                        case ENTITY_ARRAY:
                            ISFSArray arrValue = object.getSFSArray(fieldName);
                            Iterator<SFSDataWrapper> iterator = arrValue.iterator();
                            value = instantiateCollection((Class<? extends Collection>) fieldMeta.type);
                            while (iterator.hasNext()) {
                                SFSDataWrapper wrapper = iterator.next();
                                if (wrapper.getTypeId() == SFSDataType.SFS_OBJECT) {
                                    if (fieldMeta.customListItemDeserializer != null) {
                                        ((Collection) value).add(
                                                fieldMeta.customListItemDeserializer.invoke(instance, wrapper.getObject())
                                        );
                                    } else if (fieldMeta.customListItemInitializer != null) {
                                        ((Collection) value).add(
                                                deserialize(
                                                        (TransportObject) fieldMeta.customListItemInitializer.invoke
                                                                (instance, wrapper.getObject()),
                                                        (ISFSObject) wrapper.getObject()
                                                )
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
