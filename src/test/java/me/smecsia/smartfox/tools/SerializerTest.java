package me.smecsia.smartfox.tools;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.smartfox.tools.annotations.SFSCustomListItemDeserializer;
import me.smecsia.smartfox.tools.annotations.SFSCustomListItemSerializer;
import me.smecsia.smartfox.tools.annotations.SFSSerialize;
import me.smecsia.smartfox.tools.annotations.SFSSerializeStrategy;
import me.smecsia.smartfox.tools.common.AbstractTransportObject;
import me.smecsia.smartfox.tools.common.TransportObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static junit.framework.Assert.*;
import static junit.framework.Assert.assertEquals;
import static me.smecsia.smartfox.tools.annotations.SFSSerializeStrategy.Strategy.ALL_FIELDS;
import static me.smecsia.smartfox.tools.util.SFSObjectUtil.safePutInt;
import static me.smecsia.smartfox.tools.util.SFSObjectUtil.serialize;

/**
 * @author Ilya Sadykov
 *         Date: 20.10.12
 *         Time: 3:59
 */
public class SerializerTest {
    @Test
    public void testDeserialize() {
        Serializer serializerService = new Serializer();

        ISFSObject subEntityObj = new SFSObject();
        subEntityObj.putLong("longField", 20L);
        ISFSObject entityObj = new SFSObject();
        entityObj.putInt("intField", 10);
        entityObj.putSFSObject("subEntity", subEntityObj);
        entityObj.putUtfString("notDeserializable", "value1");
        entityObj.putUtfString("notSerializable", "value2");
        entityObj.putUtfString("enumField", "white");

        ISFSArray subArray = new SFSArray();
        SFSObject subObj1 = new SFSObject();
        subObj1.putLong("longField", 30L);
        subObj1.putUtfStringArray("stringsList", Arrays.asList("testString"));
        SFSObject subObj2 = new SFSObject();
        subArray.addSFSObject(subObj1);
        subArray.addSFSObject(subObj2);


        entityObj.putSFSArray("subEntities", subArray);

        Entity entity = serializerService.deserialize(Entity.class, entityObj);
        assertEquals(Entity.Color.white, entity.getEnumField());
        assertEquals(entityObj.getInt("intField"), entity.getIntField());
        assertEquals(entityObj.getUtfString("notSerializable"), entity.getNotSerializable());
        assertNull(entity.getNotDeserializable());
        assertEquals(subEntityObj.getLong("longField"), entity.getSubEntity().getLongField());

        assertNotNull(entity.getSubEntities());
        assertFalse(entity.getSubEntities().isEmpty());
        assertEquals(30L, entity.getSubEntities().get(0).getLongField().longValue());
        assertEquals("testString", entity.getSubEntities().get(0).getStringsList().get(0));

    }

    @Test
    public void testSerializeCustom() {
        Serializer serializerService = new Serializer();
        Entity entity = new Entity();
        List<Entity> list = new ArrayList<Entity>();
        Entity subEntity = new Entity();
        subEntity.setIntField(10);
        list.add(subEntity);
        entity.setWildcardList(list);

        ISFSObject sObj = serializerService.serialize(entity);

        assertNotNull(sObj.getSFSArray("wildcardList"));
        Iterator<SFSDataWrapper> iterator = sObj.getSFSArray("wildcardList").iterator();
        assertEquals(20, ((SFSObject) iterator.next().getObject()).getInt("intField").intValue());
    }

    @Test
    public void testDeserializeCustom() {
        Serializer serializerService = new Serializer();
        ISFSObject entityObj = new SFSObject();
        entityObj.putInt("intField", 10);
        ISFSArray subArray = new SFSArray();
        SFSObject subObj1 = new SFSObject();
        subObj1.putLong("longField", 30L);
        subArray.addSFSObject(subObj1);

        entityObj.putSFSArray("wildcardList", subArray);

        Entity entity = new Entity();
        serializerService.deserialize(entity, entityObj);

        assertNotNull("Wildcarded deserialized list must not be null!", entity.getWildcardList());
        assertFalse("Wildcarded deserialized list must not be empty!", entity.getWildcardList().isEmpty());
        assertEquals(30L, ((SubEntity) entity.getWildcardList().get(0)).getLongField().longValue());
    }

    @Test
    public void testSerialize() {
        Serializer serializerService = new Serializer();

        SubEntity subEntity = new SubEntity();
        subEntity.setLongField(10L);
        subEntity.setStringsList(Arrays.asList("testString"));

        Entity entity = new Entity();
        entity.setIntField(100);
        entity.setStringField("string");
        entity.setSubEntity(subEntity);
        entity.setSubEntities(Arrays.asList(new SubEntity(100L), new SubEntity(100L)));
        entity.setNotDeserializable("value1");
        entity.setNotSerializable("value2");
        entity.setEnumField(Entity.Color.black);

        ISFSObject sObj = serializerService.serialize(entity);

        assertEquals(entity.getIntField(), sObj.getInt("intField"));
        assertEquals(entity.getEnumField().name(), sObj.getUtfString("enumField"));
        assertEquals(entity.getNotDeserializable(), sObj.getUtfString("notDeserializable"));
        assertNull(sObj.getUtfString("notSerializable"));
        assertEquals(entity.getStringField(), sObj.getUtfString("stringField"));
        assertNotNull(sObj.getSFSObject("subEntity"));
        assertEquals(subEntity.getLongField(), sObj.getSFSObject("subEntity").getLong("longField"));
        assertNotNull(sObj.getSFSArray("subEntities"));
        Iterator<SFSDataWrapper> iterator = sObj.getSFSArray("subEntities").iterator();
        assertEquals(100L, ((SFSObject) iterator.next().getObject()).getLong("longField").longValue());

        assertEquals("testString", sObj.getSFSObject("subEntity").getUtfStringArray("stringsList").iterator().next());
    }


    public static class SubEntity extends AbstractTransportObject {

        @SFSSerialize
        private Long longField;
        @SFSSerialize
        private List<String> stringsList;

        public List<String> getStringsList() {
            return stringsList;
        }

        public void setStringsList(List<String> stringsList) {
            this.stringsList = stringsList;
        }

        public Long getLongField() {
            return longField;
        }

        public SubEntity() {
        }

        public SubEntity(Long longField) {
            this.longField = longField;
        }

        public void setLongField(Long longField) {
            this.longField = longField;
        }

    }

    @SFSSerializeStrategy(type = ALL_FIELDS)
    public static class Entity extends AbstractTransportObject {
        public static enum Color {white, black}

        private Integer intField;
        private String stringField;
        private SubEntity subEntity;
        private List<SubEntity> subEntities;
        @SFSSerialize(deserialize = false)
        private String notDeserializable;
        @SFSSerialize(serialize = false)
        private String notSerializable;
        private List<? extends TransportObject> wildcardList;
        private Color enumField;

        @SFSCustomListItemDeserializer(listName = "wildcardList")
        public TransportObject deserializeWildcardItem(ISFSObject object) {
            SubEntity res = new SubEntity();
            res.setLongField(object.getLong("longField"));
            return res;
        }

        @SFSCustomListItemSerializer(listName = "wildcardList")
        public ISFSObject serializeWildcardItem(TransportObject object) {
            ISFSObject res = serialize(object);
            if (object instanceof Entity) {
                safePutInt(res, "intField", ((Entity) object).getIntField() * 2);
            }
            return res;
        }

        public List<SubEntity> getSubEntities() {
            return subEntities;
        }

        public void setSubEntities(List<SubEntity> subEntities) {
            this.subEntities = subEntities;
        }

        public Integer getIntField() {
            return intField;
        }

        public void setIntField(Integer intField) {
            this.intField = intField;
        }

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }

        public SubEntity getSubEntity() {
            return subEntity;
        }

        public void setSubEntity(SubEntity subEntity) {
            this.subEntity = subEntity;
        }

        public String getNotSerializable() {
            return notSerializable;
        }

        public void setNotSerializable(String notSerializable) {
            this.notSerializable = notSerializable;
        }

        public String getNotDeserializable() {
            return notDeserializable;
        }

        public void setNotDeserializable(String notDeserializable) {
            this.notDeserializable = notDeserializable;
        }

        public List<? extends TransportObject> getWildcardList() {
            return wildcardList;
        }

        public void setWildcardList(List<? extends TransportObject> wildcardList) {
            this.wildcardList = wildcardList;
        }

        public Color getEnumField() {
            return enumField;
        }

        public void setEnumField(Color enumField) {
            this.enumField = enumField;
        }
    }
}
