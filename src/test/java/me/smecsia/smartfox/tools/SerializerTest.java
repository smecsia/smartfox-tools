package me.smecsia.smartfox.tools;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.smartfox.tools.annotations.SFSSerialize;
import me.smecsia.smartfox.tools.common.AbstractTransportObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static junit.framework.Assert.*;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
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

        ISFSArray subArray = new SFSArray();
        SFSObject subObj1 = new SFSObject();
        subObj1.putLong("longField", 30L);
        subObj1.putUtfStringArray("stringsList", Arrays.asList("testString"));
        SFSObject subObj2 = new SFSObject();
        subArray.addSFSObject(subObj1);
        subArray.addSFSObject(subObj2);


        entityObj.putSFSArray("subEntities", subArray);

        Entity entity = serializerService.deserialize(Entity.class, entityObj);

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

        ISFSObject sObj = serializerService.serialize(entity);

        assertEquals(entity.getIntField(), sObj.getInt("intField"));
        assertEquals(entity.getNotDeserializable(), sObj.getUtfString("notDeserializable"));
        assertNull(sObj.getUtfString("notSerializable"));
        assertEquals(entity.getStringField(), sObj.getUtfString("stringField"));
        assertNotNull(sObj.getSFSObject("subEntity"));
        assertEquals(subEntity.getLongField(), sObj.getSFSObject("subEntity").getLong("longField"));
        assertNotNull(sObj.getSFSArray("subEntities"));
        Iterator<SFSDataWrapper> iterator = sObj.getSFSArray("subEntities").iterator();
        assertEquals(100L, ((SFSObject) iterator.next().getObject()).getLong("longField").longValue());

        assertEquals("testString", sObj.getSFSObject("subEntity").getSFSArray("stringsList").iterator().next()
                .getObject().toString());
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

    public static class Entity extends AbstractTransportObject {
        @SFSSerialize
        private Integer intField;
        @SFSSerialize
        private String stringField;
        @SFSSerialize
        private SubEntity subEntity;
        @SFSSerialize
        private List<SubEntity> subEntities;
        @SFSSerialize(deserialize = false)
        private String notDeserializable;
        @SFSSerialize(serialize = false)
        private String notSerializable;

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
    }
}
