package me.smecsia.smartfox.tools.util;

import com.smartfoxserver.v2.entities.data.ISFSArray;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSDataType;
import com.smartfoxserver.v2.entities.data.SFSDataWrapper;
import me.smecsia.smartfox.tools.Serializer;
import me.smecsia.smartfox.tools.common.TransportObject;

import java.util.Iterator;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 *
 * @author Ilya Sadykov
 *         Date: 11.10.12
 *         Time: 18:38
 */
public class SFSObjectUtil {

    private static final Serializer serializer = new Serializer();

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

    public static ISFSObject safePutLong(ISFSObject obj, String key, Long value) {
        if (value != null && obj != null && !isEmpty(key)) {
            obj.putLong(key, value);
        }
        return obj;
    }

    public static <T extends TransportObject> TransportObject deserialize(T instance, ISFSObject sfsObj) {
        return serializer.deserialize(instance, sfsObj);
    }

    public static <T extends TransportObject> TransportObject deserialize(Class<T> clazz, ISFSObject sfsObj) {
        return serializer.deserialize(clazz, sfsObj);
    }

    public static ISFSObject serialize(TransportObject transportObject) {
        return serializer.serialize(transportObject);
    }



    private static void sfsDataWrapperToJson(SFSDataWrapper data, StringBuilder sb) {
        List list = null;
        if (data.getObject() instanceof List) {
            list = (List) data.getObject();
        }
        switch (data.getTypeId()) {
            case SFS_OBJECT:
                sb.append(sfsToJson((ISFSObject) data.getObject()));
                break;
            case UTF_STRING:
                sb.append("\"").append(data.getObject()).append("\"");
                break;
            case SFS_ARRAY:
                Iterator<SFSDataWrapper> iterator = iterator = ((ISFSArray) data.getObject()).iterator();
                sb.append("[ ");
                while (iterator != null && iterator.hasNext()) {
                    sfsDataWrapperToJson(iterator.next(), sb);
                    if (iterator.hasNext()) {
                        sb.append(", ");
                    }
                }
                sb.append(" ]");
                break;
            case INT_ARRAY:
            case FLOAT_ARRAY:
            case SHORT_ARRAY:
            case BOOL_ARRAY:
            case BYTE_ARRAY:
            case DOUBLE_ARRAY:
                sb.append("[ ");
                for (int i = 0; i < list.size(); ++i) {
                    sfsDataWrapperToJson(new SFSDataWrapper(SFSDataType.INT, list.get(i)), sb);
                    if (i < list.size() - 1)
                        sb.append(", ");
                }
                sb.append(" ]");
                break;
            case UTF_STRING_ARRAY:
                sb.append("[ ");
                for (int i = 0; i < list.size(); ++i) {
                    sfsDataWrapperToJson(new SFSDataWrapper(SFSDataType.UTF_STRING, list.get(i)), sb);
                    if (i < list.size() - 1)
                        sb.append(", ");
                }
                sb.append(" ]");
                break;
            default:
                sb.append(data.getObject());
                break;
        }
    }

    public static String sfsToJson(ISFSObject object) {
        StringBuilder sb = new StringBuilder();
        sfsToJson(object, sb);
        return sb.toString();
    }

    public static void sfsToJson(ISFSObject object, StringBuilder sb) {
        sb.append("{");
        int keysIdx = 0;
        int keysCount = object.getKeys().size();
        for (String key : object.getKeys()) {
            sb.append(" \"").append(key).append("\":");
            SFSDataWrapper data = object.get(key);
            if (data != null) {
                sfsDataWrapperToJson(data, sb);
            }
            if (++keysIdx < keysCount) {
                sb.append(", ");
            }
        }
        sb.append("}");
    }
}
