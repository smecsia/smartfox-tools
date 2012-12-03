package me.smecsia.smartfox.tools.util;

import com.smartfoxserver.v2.entities.data.ISFSArray;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSDataWrapper;
import me.smecsia.smartfox.tools.common.TransportObject;
import me.smecsia.smartfox.tools.serialize.SFSSerializer;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * @author Ilya Sadykov
 *         Date: 11.10.12
 *         Time: 18:38
 */
public class SFSObjectUtil {

    private static final SFSSerializer SFS_SERIALIZER = new SFSSerializer();

    public static ISFSObject safePutFloat(ISFSObject obj, String key, Float value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putFloat(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutSFSArray(ISFSObject obj, String key, ISFSArray value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putSFSArray(key, value);
        }
        return obj;
    }

    public static ISFSArray safeAddString(ISFSArray obj, String value) {
        if (value != null && obj != null) {
            obj.addUtfString(value);
        }
        return obj;
    }

    public static ISFSArray safeAddSFSObject(ISFSArray obj, ISFSObject value) {
        if (value != null && obj != null) {
            obj.addSFSObject(value);
        }
        return obj;
    }

    public static ISFSObject safePutSFSObject(ISFSObject obj, String key, ISFSObject value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putSFSObject(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutInt(ISFSObject obj, String key, Integer value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putInt(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutDouble(ISFSObject obj, String key, Double value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putDouble(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutString(ISFSObject obj, String key, String value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putUtfString(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutBoolean(ISFSObject obj, String key, Boolean value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putBool(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutValue(ISFSObject obj, String key, SFSDataWrapper value){
        if (value != null && obj != null && !isEmpty(key)) {
            obj.put(key, value);
        }
        return obj;
    }

    public static ISFSObject safePutLong(ISFSObject obj, String key, Long value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putLong(key, value);
        }
        return obj;
    }

    public static <T extends TransportObject> TransportObject deserialize(T instance, ISFSObject sfsObj) {
        return SFS_SERIALIZER.deserialize(instance, sfsObj);
    }

    public static <T extends TransportObject> TransportObject deserialize(Class<T> clazz, ISFSObject sfsObj) {
        return SFS_SERIALIZER.deserialize(clazz, sfsObj);
    }

    public static ISFSObject serialize(TransportObject transportObject) {
        return SFS_SERIALIZER.serialize(transportObject);
    }
}
