package me.smecsia.smartfox.tools.serialize;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.common.serialize.*;
import me.smecsia.common.serialize.annotations.*;
import me.smecsia.smartfox.tools.common.BasicService;
import me.smecsia.smartfox.tools.error.MetadataException;
import me.smecsia.smartfox.tools.util.EnumUtil;
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
public class SFSSerializer extends BasicService implements TransportSerializer<ISFSObject> {

    private enum FieldType {
        LONG, INT, BOOL, FLOAT, DOUBLE, STRING, STRING_ARRAY, LONG_ARRAY, ENUM_ARRAY, ENTITY, ENTITY_ARRAY, ENUM, UNKNOWN
    }

    private static final Map<Class<? extends TransportObject>, Metadata> metaCache =
            new ConcurrentHashMap<Class<? extends TransportObject>, Metadata>();
    private final List<SerializePreProcessor> preProcessors = Collections.synchronizedList(new
            ArrayList<SerializePreProcessor>());
    private final List<SerializePostProcessor> postProcessors = Collections.synchronizedList(new
            ArrayList<SerializePostProcessor>());

    private class FieldMeta {
        Method getter;
        Method setter;
        FieldType fieldType;
        Class<?> type;
        Class<?> genericType;
        Method customFieldSerializer;
        Method customFieldDeserializer;
        Method customListItemSerializer;
        Method customListItemDeserializer;
        Method customListItemInitializer;
        Field field;
        final String name;
        Serialize config;

        private FieldMeta(String name) {
            this.name = name;
        }
    }

    /**
     * Registers serialize processor
     *
     * @param processor processor to be registered
     */
    @Override
    public void registerProcessor(SerializeProcessor processor) {
        registerPreProcessor(processor);
        registerPostProcessor(processor);
    }

    /**
     * Registers the post-serialize processor for the transport object class
     *
     * @param processor processor to be registered
     */
    @Override
    public void registerPreProcessor(SerializePreProcessor processor) {
        preProcessors.add(processor);
    }

    /**
     * Registers the pre-serialize processor for the transport object class
     *
     * @param processor processor to be registered
     */
    @Override
    public void registerPostProcessor(SerializePostProcessor processor) {
        postProcessors.add(processor);
    }


    /**
     * Read fields options for the class
     *
     * @param clazz
     * @return options for each field
     */
    @Override
    public <DT extends TransportObject> Map<String, String[]> getFieldsOptions(Class<DT> clazz) {
        return getMetadata(clazz).fieldsOptions;
    }

    /**
     * Class that holds the metadata about the certain
     *
     * @param <T>
     */
    private class Metadata<T extends TransportObject> {
        private Class<T> entityClass;
        private SerializeStrategy.Strategy serializeStrategy;
        private Map<String, FieldMeta> entityFields = new HashMap<String, FieldMeta>();
        private Map<String, String[]> fieldsOptions = new HashMap<String, String[]>();
        private FieldMeta missingFieldsStorage = null;

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
                CustomListItemInitializer annotation = m.getAnnotation(CustomListItemInitializer.class);
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
                CustomListItemDeserializer annotation = m.getAnnotation(CustomListItemDeserializer.class);
                if (annotation != null && annotation.listName().equals(field.getName())) {
                    return validateCustomListItemInitializerMethod(m);
                }
            }
            return null;
        }

        private Method findCustomListItemSerializer(Method[] methods, Field field) {
            for (Method m : methods) {
                CustomListItemSerializer annotation = m.getAnnotation(CustomListItemSerializer.class);
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

        private void checkAndSetMissingFieldsStorage(Field f) {
            MissingSerializeFieldsStorage annotation = f.getAnnotation(MissingSerializeFieldsStorage.class);
            if (annotation != null) {
                if (missingFieldsStorage != null) {
                    throw new MetadataException("Class '" + entityClass + "' must have only 1 field " +
                            "annotated with @MissingSerializeFieldsStorage!");
                }
                Type fieldType = f.getType();
                Type[] typeArgs = getFieldTypeArguments(f);
                if (Map.class.isAssignableFrom((Class<?>) fieldType) && typeArgs.length == 2 &&
                        isString((Class<?>) typeArgs[0]) && Object.class.equals(typeArgs[1])) {
                    missingFieldsStorage = new FieldMeta(f.getName());
                    missingFieldsStorage.field = f;
                    missingFieldsStorage.setter = findSetter(f);
                    missingFieldsStorage.getter = findGetter(f);
                    if (missingFieldsStorage.setter == null || missingFieldsStorage.getter == null) {
                        f.setAccessible(true);
                    }
                } else {
                    throw new MetadataException("Field '" + f.getName() + "' annotated as " +
                            "@MissingSerializeFieldsStorage must be Map<String, Object>!");
                }
            }
        }

        private Method findCustomFieldDeserializer(Method[] methods, Field field) {
            for (Method m : methods) {
                CustomFieldDeserializer annotation = m.getAnnotation(CustomFieldDeserializer.class);
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
                CustomFieldSerializer annotation = m.getAnnotation(CustomFieldSerializer.class);
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

            SerializeStrategy serializeStrategy = findAnnotationInClassHierarchy(entityClass, SerializeStrategy.class);
            SerializeIgnore ignoreClassFields = entityClass.getAnnotation(SerializeIgnore.class);
            if (serializeStrategy != null) {
                this.serializeStrategy = serializeStrategy.type();
            } else {
                this.serializeStrategy = SerializeStrategy.Strategy.DEFAULT;
            }

            for (Field field : fields) {
                // skip fields that are listed in the ignoreClasFields
                if (ignoreClassFields != null && Arrays.asList(ignoreClassFields.fields()).contains(field.getName())) {
                    continue;
                }

                // skip fields that are not mapped as serializable
                Serialize annotation = field.getAnnotation(Serialize.class);
                if ((annotation == null && this.serializeStrategy.equals(SerializeStrategy.Strategy.ANNOTATED_FIELDS)) ||
                        field.getAnnotation(SerializeIgnore.class) != null) {
                    continue;
                }

                // building metadata
                final Serialize config = (annotation != null) ? annotation : Serialize.DEFAULT.get();

                FieldMeta meta = new FieldMeta((!isEmpty(config.name())) ? config.name() : field.getName());
                meta.getter = findGetter(field);
                meta.setter = findSetter(field);

                if (meta.getter == null || meta.setter == null) {
                    field.setAccessible(true);
                }
                meta.config = config;

                meta.customFieldDeserializer = findCustomFieldDeserializer(methods, field);
                meta.customFieldSerializer = findCustomFieldSerializer(methods, field);
                checkAndSetMissingFieldsStorage(field);

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
        private <T extends TransportObject> void set(T obj, String fieldName, Object value, FieldMeta fieldMeta) {
            try {
                if (fieldMeta.setter != null) {
                    fieldMeta.setter.invoke(obj, value);
                } else {
                    fieldMeta.field.set(obj, value);
                }
            } catch (Exception e) {
                logAndThrow(new MetadataException("Cannot set field " + fieldName + " for object of " +
                        "type " + entityClass));
            }
        }

        private <T extends TransportObject> void set(T obj, String fieldName, Object value) {
            checkField(fieldName);
            set(obj, fieldName, value, entityFields.get(fieldName));
        }

        /**
         * Get the field's value for an instance of an object
         *
         * @param obj       instance of entity
         * @param fieldName name of the field
         */
        private Object get(T obj, String fieldName, FieldMeta fieldMeta) {
            try {
                Method getter = fieldMeta.getter;
                if (getter != null) {
                    return getter.invoke(obj);
                } else {
                    return getEntityFields().get(fieldName).field.get(obj);
                }
            } catch (Exception e) {
                logAndThrow(new MetadataException("Cannot get field " + fieldName + " for object of " +
                        "type " + entityClass + ""));
            }
            return null;
        }

        private Object get(T obj, String fieldName) {
            return get(obj, fieldName, entityFields.get(fieldName));
        }

    }

    private <T extends TransportObject> void applyPreProcessors(T object) {
        for (SerializePreProcessor processor : preProcessors) {
            processor.process(object);
        }
    }

    private <T extends TransportObject> void applyPostProcessors(ISFSObject result, T object) {
        for (SerializePostProcessor processor : postProcessors) {
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
    @Override
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
            if (metadata.missingFieldsStorage != null) {
                Map<String, Object> storage = (Map<String, Object>) metadata.get(instance,
                        metadata.missingFieldsStorage.field.getName(), metadata.missingFieldsStorage);
                for (String mFieldKey : storage.keySet()) {
                    if (!metadata.getEntityFields().keySet().contains(mFieldKey)) {
                        safePutDataWrapper(result, mFieldKey, newSfsDataWrapper(storage.get(mFieldKey)));
                    }
                }
            }
            applyPostProcessors(result, instance);
            return result;
        }
        return null;
    }

    private SFSDataWrapper newSfsDataWrapper(Object value) {
        if (value != null) {
            SFSDataType dataType = null;
            Class<?> valueClass = value.getClass();
            if (isString(valueClass)) {
                dataType = SFSDataType.UTF_STRING;
            } else if (isLong(valueClass)) {
                dataType = SFSDataType.LONG;
            } else if (isInt(valueClass)) {
                dataType = SFSDataType.INT;
            } else if (isDouble(valueClass)) {
                dataType = SFSDataType.DOUBLE;
            } else if (isFloat(valueClass)) {
                dataType = SFSDataType.FLOAT;
            } else if (isBoolean(valueClass)) {
                dataType = SFSDataType.BOOL;
            } else if (value instanceof TransportObject) {
                dataType = SFSDataType.SFS_OBJECT;
                value = serialize((TransportObject) value);
            }

            if (dataType != null) {
                return new SFSDataWrapper(dataType, value);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends TransportObject> T deserialize(T instance, ISFSObject object) {
        try {
            if (instance == null) {
                logAndThrow(new MetadataException("Cannot deserialize to a null instance!"));
            }
            Metadata<T> metadata = (Metadata<T>) getMetadata(instance.getClass());
            Map<String, Object> missedStorage = new HashMap<String, Object>();
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
                } else if (metadata.missingFieldsStorage != null) {
                    missedStorage.put(fieldName, object.get(fieldName).getObject());
                }
            }
            if (metadata.missingFieldsStorage != null) {
                metadata.set(instance, metadata.missingFieldsStorage.name, missedStorage, metadata.missingFieldsStorage);
            }
            return instance;
        } catch (Exception e) {
            logAndThrow(new MetadataException(e));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
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
