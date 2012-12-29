package me.smecsia.smartfox.tools.serialize;

import com.smartfoxserver.v2.entities.data.*;
import me.smecsia.common.serialize.BasicTransportObject;
import me.smecsia.common.serialize.SerializePostProcessor;
import me.smecsia.common.serialize.SerializePreProcessor;
import me.smecsia.common.serialize.TransportObject;
import me.smecsia.common.serialize.annotations.*;
import me.smecsia.smartfox.tools.common.AbstractTransportObject;
import me.smecsia.smartfox.tools.util.SFSObjectUtil;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.*;

import static junit.framework.Assert.*;
import static me.smecsia.smartfox.tools.serialize.SFSSerializer.DEFAULT_DATE_FORMAT;

/**
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 13:49
 */
public class SFSSerializerTest {

    @Test
    public void testBasicTransportObject() {
        SFSSerializer serializer = new SFSSerializer();

        ISFSObject input = new SFSObject();
        input.putUtfString("name", "John");
        input.putUtfString("surname", "Smith");
        input.putLong("age", 50L);

        BasicTransportObject to = serializer.deserialize(BasicTransportObject.class, input);
        assertEquals("John", to.get("name"));
        assertEquals("Smith", to.get("surname"));
        assertEquals(50L, to.get("age"));
    }

    @Test
    public void testDeserialize() {
        SFSSerializer sfsSerializer = new SFSSerializer();

        ISFSObject subEntityObj = new SFSObject();
        subEntityObj.putLong("longField", 20L);
        ISFSObject entityObj = new SFSObject();
        entityObj.putInt("intField", 10);
        entityObj.putSFSObject("subEntity", subEntityObj);
        entityObj.putUtfString("notDeserializable", "value1");
        entityObj.putUtfString("notSerializable", "value2");
        entityObj.putUtfString("enumField", "white");
        entityObj.putUtfString("changedName", "changedValue");
        entityObj.putUtfString("fieldWithoutGetter", "value");
        entityObj.putInt("fieldCustomSerializable", 10);
        entityObj.putUtfString("totallyIgnoredField", "value");
        entityObj.putUtfString("date", "2012-12-08 12:00:00");

        ISFSObject entityMapObj = new SFSObject();
        entityMapObj.putSFSObject("entityMap", subEntityObj);
        entityObj.putSFSObject("entityMap", entityMapObj);

        ISFSObject stringMapObj = new SFSObject();
        stringMapObj.putUtfString("stringMapField", "stringMapValue");
        entityObj.putSFSObject("stringMap", stringMapObj);

        entityObj.putUtfStringArray("colors", Arrays.asList("black", "black", "white"));

        ISFSArray subArray = new SFSArray();
        SFSObject subObj1 = new SFSObject();
        subObj1.putLong("longField", 30L);
        subObj1.putUtfStringArray("stringsList", Arrays.asList("testString"));
        SFSObject subObj2 = new SFSObject();
        subArray.addSFSObject(subObj1);
        subArray.addSFSObject(subObj2);

        entityObj.putSFSArray("subEntities", subArray);

        Entity entity = sfsSerializer.deserialize(Entity.class, entityObj);

        assertEquals(Entity.Color.white, entity.getEnumField());
        assertEquals(Entity.Color.white, entity.getColors().get(2));
        assertEquals(entityObj.getInt("intField"), entity.getIntField());
        assertEquals((Long) 10L, entity.fieldCustomSerializable);
        assertEquals(entityObj.getUtfString("fieldWithoutGetter"), entity.fieldWithoutGetter);
        assertEquals(entityObj.getUtfString("notSerializable"), entity.getNotSerializable());
        assertEquals(entityObj.getUtfString("changedName"), entity.getNameToBeChanged());
        assertNull(entity.getNotDeserializable());
        assertNull(entity.totallyIgnoredField);
        assertEquals(subEntityObj.getLong("longField"), entity.getSubEntity().getLongField());
        assertEquals("2012-12-08 12:00:00", new SimpleDateFormat(DEFAULT_DATE_FORMAT).format(entity.date));

        assertEquals(20L, entity.entityMap.get("entityMap").getLongField().longValue());
        assertEquals("stringMapValue", entity.stringMap.get("stringMapField"));
        assertNotNull(entity.getSubEntities());
        assertFalse(entity.getSubEntities().isEmpty());
        assertEquals(30L, entity.getSubEntities().get(0).getLongField().longValue());
        assertEquals("testString", entity.getSubEntities().get(0).getStringsList().get(0));

    }

    @Test
    public void testSerializeCustom() {
        SFSSerializer sfsSerializer = new SFSSerializer();
        Entity entity = new Entity();
        Entity subEntity = new Entity();
        Entity subEntityCustom = new Entity();
        subEntity.setIntField(10);
        subEntityCustom.setIntField(99);
        entity.setWildcardList(Arrays.asList(subEntity));
        entity.setWildcardedListCustom(Arrays.asList(subEntityCustom));

        sfsSerializer.registerPostProcessor(new SerializePostProcessor() {
            @Override
            public <ST, DT extends TransportObject> void process(ST result, DT sourceObject) {
                if (sourceObject instanceof Entity)
                    ((ISFSObject) result).putBool("postProcessed", true);
            }
        });
        sfsSerializer.registerPreProcessor(new SerializePreProcessor() {
            @Override
            public <DT> void process(DT sourceObject) {
                if (sourceObject instanceof Entity)
                    ((Entity) sourceObject).setPreProcessed(true);
            }
        });

        ISFSObject sObj = sfsSerializer.serialize(entity);

        assertNotNull(sObj.getSFSArray("wildcardList"));
        assertTrue(sObj.getBool("postProcessed"));
        assertTrue(sObj.getBool("preProcessed"));
        Iterator<SFSDataWrapper> iterator = sObj.getSFSArray("wildcardList").iterator();
        assertEquals(subEntity.getIntField() * 2, ((SFSObject) iterator.next().getObject()).getInt("intField").intValue());

        Iterator<SFSDataWrapper> customIterator = sObj.getSFSArray("wildcardedListCustom").iterator();
        assertEquals(subEntityCustom.getIntField(), ((SFSObject) customIterator.next().getObject()).getInt("intField"));
    }

    @Test
    public void testDeserializeCustom() {
        SFSSerializer sfsSerializer = new SFSSerializer();
        ISFSObject entityObj = new SFSObject();
        entityObj.putInt("intField", 10);
        ISFSArray subArray = new SFSArray();
        ISFSArray subCustomArray = new SFSArray();
        SFSObject subObj1 = new SFSObject();
        SFSObject subCustomObj = new SFSObject();
        subObj1.putLong("longField", 30L);
        subCustomObj.putLong("longField", 99L);
        subArray.addSFSObject(subObj1);
        subCustomArray.addSFSObject(subCustomObj);

        entityObj.putSFSArray("wildcardList", subArray);
        entityObj.putSFSArray("wildcardedListCustom", subCustomArray);

        Entity entity = new Entity();
        sfsSerializer.deserialize(entity, entityObj);

        assertNotNull("Wildcarded deserialized list must not be null!", entity.getWildcardList());
        assertFalse("Wildcarded deserialized list must not be empty!", entity.getWildcardList().isEmpty());
        assertEquals(30L, ((SubEntity) entity.getWildcardList().get(0)).getLongField().longValue());
        assertEquals(99L, ((SubEntity) entity.getWildcardedListCustom().get(0)).getLongField().longValue());
    }

    @Test
    public void testSerialize() {
        SFSSerializer sfsSerializer = new SFSSerializer();

        SubEntity subEntity = new SubEntity();
        subEntity.setLongField(10L);
        subEntity.setStringsList(Arrays.asList("testString"));

        Entity entity = new Entity();
        entity.setIntField(100);
        entity.setStringField("string");
        entity.setSubEntity(subEntity);
        entity.setSubEntities(Arrays.asList(new SubEntity(100L), new SubEntity(100L)));
        entity.setNotDeserializable("value1");
        entity.fieldWithoutGetter = "value";
        entity.setNotSerializable("value2");
        entity.setEnumField(Entity.Color.black);
        entity.setColors(Arrays.asList(Entity.Color.black, Entity.Color.white));
        entity.fieldCustomSerializable = 20L;
        entity.totallyIgnoredField = "value";
        entity.date = new Date();

        entity.entityMap.put("entityMapField", new SubEntity(99L));
        entity.stringMap.put("stringMapField", "stringMapValue");

        ISFSObject sObj = sfsSerializer.serialize(entity);

        assertEquals(entity.getIntField(), sObj.getInt("intField"));
        assertEquals(entity.fieldWithoutGetter, sObj.getUtfString("fieldWithoutGetter"));
        assertEquals(entity.getEnumField().name(), sObj.getUtfString("enumField"));
        assertEquals(entity.getNameToBeChanged(), sObj.getUtfString("changedName"));
        assertEquals(entity.getNotDeserializable(), sObj.getUtfString("notDeserializable"));
        assertEquals(new SimpleDateFormat(DEFAULT_DATE_FORMAT).format(entity.date), sObj.getUtfString("date"));
        assertEquals(20, sObj.getInt("fieldCustomSerializable").intValue());
        Collection<String> colors = sObj.getUtfStringArray("colors");
        assertTrue(colors.contains(entity.getColors().get(1).name()));
        assertNull(sObj.getUtfString("totallyIgnoredField"));
        assertNull(sObj.getUtfString("notSerializable"));
        assertNull(sObj.getUtfString("ignoredField"));
        assertEquals(entity.getStringField(), sObj.getUtfString("stringField"));
        assertNotNull(sObj.getSFSObject("subEntity"));
        assertEquals(subEntity.getLongField(), sObj.getSFSObject("subEntity").getLong("longField"));
        assertNotNull(sObj.getSFSArray("subEntities"));
        assertNotNull(sObj.getSFSObject("entityMap"));
        assertEquals(99L, sObj.getSFSObject("entityMap").getSFSObject("entityMapField").getLong("longField").longValue());
        assertNotNull(sObj.getSFSObject("stringMap"));
        assertEquals("stringMapValue", sObj.getSFSObject("stringMap").getUtfString("stringMapField"));
        Iterator<SFSDataWrapper> iterator = sObj.getSFSArray("subEntities").iterator();
        assertEquals(100L, ((SFSObject) iterator.next().getObject()).getLong("longField").longValue());

        assertEquals("testString", sObj.getSFSObject("subEntity").getUtfStringArray("stringsList").iterator().next());
    }


    public static class SubEntity extends AbstractTransportObject {

        @Serialize
        private Long longField;
        @Serialize
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

    @SerializeStrategy(type = SerializeStrategy.Strategy.ALL_FIELDS)
    @SerializeIgnore(fields = {"totallyIgnoredField"})
    public static class Entity extends AbstractTransportObject {
        public static enum Color {white, black}

        private Integer intField;
        private String stringField;
        private SubEntity subEntity;
        private List<SubEntity> subEntities;
        @Serialize(deserialize = false)
        private String notDeserializable;
        @Serialize(serialize = false)
        private String notSerializable;
        private List<? extends TransportObject> wildcardList;
        private List<? extends TransportObject> wildcardedListCustom;
        private Color enumField;
        private Boolean preProcessed = false;
        @SerializeIgnore
        private String ignoredField = "ignoredValue";
        @Serialize(name = "changedName")
        private String nameToBeChanged = "value";
        @Serialize
        private List<Color> colors;
        private String fieldWithoutGetter;
        private Long fieldCustomSerializable;
        private String totallyIgnoredField = null;
        private Date date;
        private Map<String, SubEntity> entityMap = new HashMap<String, SubEntity>();
        private Map<String, String> stringMap = new HashMap<String, String>();

        @CustomListItemInitializer(listName = "wildcardedListCustom")
        public TransportObject initializeWildCardedListItem(ISFSObject object) {
            return new SubEntity();
        }

        @CustomFieldDeserializer(fieldName = "fieldCustomSerializable")
        public Long customDeserializeCustomField(SFSDataWrapper wrapper) {
            return Long.valueOf((Integer) wrapper.getObject());
        }

        @CustomFieldSerializer(fieldName = "fieldCustomSerializable")
        public SFSDataWrapper customSerializeCustomField(Long value) {
            return new SFSDataWrapper(SFSDataType.INT, value.intValue());
        }

        @CustomListItemDeserializer(listName = "wildcardList")
        public TransportObject deserializeWildcardItem(ISFSObject object) {
            SubEntity res = new SubEntity();
            res.setLongField(object.getLong("longField"));
            return res;
        }

        @CustomListItemSerializer(listName = "wildcardList")
        public ISFSObject serializeWildcardItem(TransportObject object) {
            ISFSObject res = SFSObjectUtil.serialize(object);
            if (object instanceof Entity) {
                SFSObjectUtil.safePutInt(res, "intField", ((Entity) object).getIntField() * 2);
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

        public Boolean getPreProcessed() {
            return preProcessed;
        }

        public void setPreProcessed(Boolean preProcessed) {
            this.preProcessed = preProcessed;
        }

        public String getIgnoredField() {
            return ignoredField;
        }

        public void setIgnoredField(String ignoredField) {
            this.ignoredField = ignoredField;
        }

        public String getNameToBeChanged() {
            return nameToBeChanged;
        }

        public void setNameToBeChanged(String nameToBeChanged) {
            this.nameToBeChanged = nameToBeChanged;
        }

        public List<Color> getColors() {
            return colors;
        }

        public void setColors(List<Color> colors) {
            this.colors = colors;
        }

        public List<? extends TransportObject> getWildcardedListCustom() {
            return wildcardedListCustom;
        }

        public void setWildcardedListCustom(List<? extends TransportObject> wildcardedListCustom) {
            this.wildcardedListCustom = wildcardedListCustom;
        }
    }
}
