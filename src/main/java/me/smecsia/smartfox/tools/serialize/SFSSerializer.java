package me.smecsia.smartfox.tools.serialize;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.smartfox.tools.annotations.*;
import me.smecsia.smartfox.tools.common.BasicService;
import me.smecsia.smartfox.tools.common.TransportObject;
import me.smecsia.smartfox.tools.error.MetadataException;
import me.smecsia.smartfox.tools.util.EnumUtil;
import org.apache.commons.lang.WordUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.smecsia.smartfox.tools.annotations.SFSSerializeStrategy.Strategy;
import static me.smecsia.smartfox.tools.util.ClassUtil.*;
import static me.smecsia.smartfox.tools.util.ExceptionUtil.formatStackTrace;
import static me.smecsia.smartfox.tools.util.SFSObjectUtil.*;
import static me.smecsia.smartfox.tools.util.TypesUtil.*;


/**
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 3:32
 */
public class SFSSerializer extends BasicService {

    private enum FieldType {
        LONG, INT, BOOL, FLOAT, DOUBLE, STRING, STRING_ARRAY, ENTITY, ENTITY_ARRAY, ENUM, UNKNOWN
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
        public Method customSerializer;
        public Method customDeserializer;
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
        private Strategy serializeStrategy;
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
            final Field[] fields = getFieldsInClassHierarchy(entityClass);
            final Method[] methods = getMethodsInClassHierarchy(entityClass);

            SFSSerializeStrategy sfsSerializeStrategy = findAnnotationInClassHierarchy(entityClass, SFSSerializeStrategy.class);
            if (sfsSerializeStrategy != null) {
                this.serializeStrategy = sfsSerializeStrategy.type();
            } else {
                this.serializeStrategy = Strategy.ALL_FIELDS;
            }

            for (Field field : fields) {
                // skip fields that are not mapped as serializable
                SFSSerialize annotation = field.getAnnotation(SFSSerialize.class);
                if ((annotation == null && this.serializeStrategy.equals(Strategy.ANNOTATED_FIELDS)) ||
                        field.getAnnotation(SFSSerializeIgnore.class) != null) {
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
                } else if (Enum.class.isAssignableFrom(field.getType())) {
                    meta.fieldType = FieldType.ENUM;
                    meta.genericType = field.getType();
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
                    fieldsOptions.put(field.getName(), meta.config.options());
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
    public <T extends TransportObject> ISFSObject serialize(T object) {
        if (object != null) {
            ISFSObject result = new SFSObject();
            Metadata<T> metadata = (Metadata<T>) getMetadata(object.getClass());
            applyPreProcessors(object);
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
                        case ENUM:
                            safePutString(result, fieldName, ((Enum) value).name());
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
            applyPostProcessors(result, object);
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
                        case ENUM:
                            value = EnumUtil.fromString((Class<Enum>) fieldMeta.genericType, object.getUtfString(fieldName));
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
