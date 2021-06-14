package mindustry;

// Jackson JSON
// Had troubles with anonymous inner classes & null pointer exceptions
// Would work well if I have to make custom serializers

import arc.graphics.TextureData;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import mindustry.world.Block;
import mindustry.world.consumers.ConsumeType;
import mindustry.world.consumers.Consumers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;

@SuppressWarnings("ALL")
public class MindustrySerializer {

    public static void serializeBlocks(String filename, Block[] blocksToSerialize) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            // IGNORE null values by only including non-null values:
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            SimpleModule skipTextures = new SimpleModule();
            skipTextures.addSerializer(TextureData.class, new NoopSerializer());
            skipTextures.setSerializerModifier(new NoopSerializerModifier());
            mapper.registerModule(skipTextures);

            SimpleModule module = new SimpleModule();
            module.setSerializerModifier(new SkipExceptionsSerializerModifier());
            mapper.registerModule(module);

            mapper.writeValue(new File(filename), blocksToSerialize);

            System.out.println("Printed blocks to " + filename);
        } catch (Exception exc) {
            System.out.println(exc.getMessage());
        }
    }

    // We use this to skip serializing the textures (images).
    public static class NoopSerializerModifier extends BeanSerializerModifier {
        @Override
        public JsonSerializer<?> modifySerializer(
                SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {

            if (beanDesc.getBeanClass().equals(TextureData.class)) {
                return new SkipExceptionsSerializer((JsonSerializer<Object>) serializer);
            }
            return serializer;
        }
    }

    // https://www.baeldung.com/jackson-call-default-serializer-from-custom-serializer
    static class NoopSerializer extends StdSerializer<Object> {

        public NoopSerializer() {
            super(Object.class);
        }

        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            System.out.println("No-op serializer for " + value.getClass().getName());
            jgen.writeString("MISSING"); // instead of writing out the image as the value
            // put in a string
            jgen.flush();
            System.out.println();
        }
    }

    public static class SkipExceptionsSerializerModifier extends BeanSerializerModifier {

        @Override
        public JsonSerializer<?> modifySerializer(
                SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {
            // Use this serializer for everything that we can:
            return new SkipExceptionsSerializer((JsonSerializer<Object>) serializer);
        }
    }

    static class GlobalVars {
        public static int recursion_level = 0;
    }

    // https://www.baeldung.com/jackson-call-default-serializer-from-custom-serializer
    static class SkipExceptionsSerializer extends StdSerializer<Object> {

        private JsonSerializer<Object> defaultSerializer;

        private HashMap<Object, Boolean> serializedObjects = new HashMap<>(2000);

        public SkipExceptionsSerializer(JsonSerializer<Object> defaultSerializer) {
            super(Object.class);
            this.defaultSerializer = defaultSerializer;
        }

        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            try {
                if (value instanceof Block) {
                    try {
                        Block b = (Block) value;
                        System.out.println(b.name);
                        b.getGeneratedIcons(); // see if this causes an exception
                    } catch (Exception e) {
                        System.out.println("getGeneratedIcons() caused an exception.\n\t" + value + "\n\t" + e.getMessage());
                        System.out.println();
                        serializeX(value, jgen, provider);
                        return;
                    }
                }

                if (value instanceof Consumers) {
                    try {
                        serializeConsumers((Consumers) value, jgen, provider);
                    } catch (Exception e) {
                        System.out.println("serializeConsumers: Skipping object b/c of exception.\n\t" + value + "\n\t" + e.getMessage());
                        System.out.println();
                        jgen.writeEndObject(); // end the problematic object (instead of writing out values, etc)
                        jgen.flush();
                    }
                } else if (defaultSerializer != null) {
                    GlobalVars.recursion_level++;
                    if (GlobalVars.recursion_level > 10)
                        return;

                    defaultSerializer.serialize(value, jgen, provider);

                    GlobalVars.recursion_level--;
                }
            } catch (Exception e) {
                System.out.println("Skipping object b/c of exception.\n\t" + value.toString() + "\n\t" + e.getMessage());
                System.out.println();
                // Wanted to write to file, but frequently the problem is that the file has been mangled/corrupted
                //jgen.writeStringField("EXCEPTION_FROM", value.toString());
            }
        }

        /*
         * AFAICT, Mindustry expects people to check for what types are consumed before calling getItem, etc
         * AND
         * Jackson calls every method that starts with get____, and then chokes on Mindustry's exception
         */
        public void serializeConsumers(Consumers value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            jgen.writeStartObject();

            jgen.writeStringField("NOTE", "Custom Serializer Created This");
            if (!value.has(ConsumeType.item))
                jgen.writeStringField("item", "None");
            else {
                jgen.writeObjectField("item", value.getItem());
                jgen.writeObjectField("itemfilters", value.itemFilters);
            }

            if (!value.has(ConsumeType.power))
                jgen.writeStringField("power", "None");
            else
                jgen.writeObjectField("power", value.getPower());

            if (!value.has(ConsumeType.liquid))
                jgen.writeStringField("liquid", "None");
            else
                // No getLiquid() method
                jgen.writeObjectField("liquidfilters", value.liquidfilters);

            jgen.writeEndObject();
        }

        public void serializeX(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            jgen.writeStartObject();

            // Don't serialize the same object multiple times:
            if(serializedObjects.containsKey(value)  ) {
                jgen.writeStringField(value.toString(), "Duplicate object - already serialized this somewhere else");
                jgen.writeEndObject();
                jgen.flush();
                return;
            }
            serializedObjects.put(value, true);

            Class aClass = value.getClass();
            jgen.writeStringField("NOTE", aClass.getName() + ": Manual Serializer Invoked to avoid an exception");

            System.out.println("========= " + aClass.getName() + ": =========");

//            System.out.println("METHODS: ===========");
//
//            Method[] methods = aClass.getMethods();
//            for (Method method : methods) {
//                try {
//                    if (method.getName() == "getGeneratedIcons") {
//                        System.out.println("\tSKIPPING METHOD: " + method.getName());
//                        continue;
//                    } else if (defaultSerializer != null) {
//                        // provider.defaultSerializeValue(value, jgen);
//                        System.out.println("\tMethod: " + method.getName());
////                        jgen.writeFieldName(f.getName());
////                        fieldStarted = true;
//                        jgen.flush();
//                        //defaultSerializer.serialize(f.get(value), jgen, provider);
//                        jgen.flush();
//                    }
//                } catch (Exception e) {
//                    JsonStreamContext ctx = jgen.getOutputContext();
////                    if( fieldStarted) {
////                        jgen.writeNull();
////                        //jgen.writeString("EXCEPTION WHEN SERIALIZED");
////                    }
//                    System.out.println("Failed to get value for method " + method.getName() + ":\n" + e.getMessage());
//                    jgen.writeNullField(method.getName());
//                    jgen.flush();
//                }
//            }

            System.out.println("FIELDS: ===========");
            Field[] fields = aClass.getFields();
            for (Field f : fields) {
                try {
                    if (f.getName().equals("textureData")
                            || f.getType().getName().equals("arc.graphics.g2d.TextureRegion")) {
                        System.out.println("\tSKIPPING " + f.getName() + "(" + f.getType().getName() + "): " + f.get(value));
                    } else if (defaultSerializer != null) {
                        System.out.println("\tname: " + f.getName() + " type: " + f.getType().getName() + " value:" + f.get(value));
                        jgen.writeObjectField(f.getName(), f.get(value));
                        jgen.flush();
                    }
                } catch (Exception e) {
                    System.out.println("Failed to get value for " + f.getName() + ":\n" + e.getMessage());
                    jgen.writeNullField(f.getName());
                    jgen.flush();
                }
            }

            jgen.writeEndObject();
        }

//        // from http://tutorials.jenkov.com/java-reflection/getters-setters.html:
//        public static boolean isGetter(Method method) {
//            if (!method.getName().startsWith("get")) return false;
//            if (method.getParameterTypes().length != 0) return false;
//            if (void.class.equals(method.getReturnType())) return false;
//            return true;
//        }
//
//        public static boolean isSetter(Method method) {
//            if (!method.getName().startsWith("set")) return false;
//            if (method.getParameterTypes().length != 1) return false;
//            return true;
//        }
    }
}
// Example of a block (silicon smelter):
    /*
    {
  "id" : 92,
  "minfo" : { },
  "name" : "silicon-smelter",
  "stats" : {
    "useCategories" : false,
    "intialized" : false
  },
  "localizedName" : "Silicon Smelter",
  "description" : "Refines silicon from sand and coal.",
  "alwaysUnlocked" : false,
  "inlineDescription" : true,
  "iconId" : 0,
  "hasItems" : true,
  "hasLiquids" : false,
  "hasPower" : true,
  "outputsLiquid" : false,
  "consumesPower" : true,
  "outputsPower" : false,
  "outputsPayload" : false,
  "outputFacing" : true,
  "acceptsItems" : false,
  "itemCapacity" : 10,
  "liquidCapacity" : 10.0,
  "liquidPressure" : 1.0,
  "bars" : { },
  "consumes" : {
    "itemFilters" : {
      "empty" : true
    },
    "liquidfilters" : {
      "empty" : true
    },
    "item" : {
      "optional" : false,
      "update" : true,
      "items" : [ {
        "item" : {
          "id" : 5,
          "minfo" : { },
          "name" : "coal",
          "stats" : {
            "useCategories" : false,
            "intialized" : false
          },
          "localizedName" : "Coal",
          "description" : "Used for fuel and refined material production.",
          "details" : "Appears to be fossilized plant matter, formed long before the seeding event.",
          "alwaysUnlocked" : false,
          "inlineDescription" : true,
          "iconId" : 0,
          "color" : {
            "r" : 0.15294118,
            "g" : 0.15294118,
            "b" : 0.15294118,
            "a" : 1.0
          },
          "explosiveness" : 0.2,
          "flammability" : 1.0,
          "radioactivity" : 0.0,
          "charge" : 0.0,
          "hardness" : 2,
          "cost" : 1.0,
          "lowPriority" : false,
          "contentType" : "item",
          "hidden" : false,
          "disposed" : false
        },
        "amount" : 1
      }, {
        "item" : {
          "id" : 4,
          "minfo" : { },
          "name" : "sand",
          "stats" : {
            "useCategories" : false,
            "intialized" : false
          },
          "localizedName" : "Sand",
          "description" : "Used for production of other refined materials.",
          "alwaysUnlocked" : true,
          "inlineDescription" : true,
          "iconId" : 0,
          "color" : {
            "r" : 0.96862745,
            "g" : 0.79607844,
            "b" : 0.6431373,
            "a" : 1.0
          },
          "explosiveness" : 0.0,
          "flammability" : 0.0,
          "radioactivity" : 0.0,
          "charge" : 0.0,
          "hardness" : 0,
          "cost" : 1.0,
          "lowPriority" : true,
          "contentType" : "item",
          "hidden" : false,
          "disposed" : false
        },
        "amount" : 2
      } ],
      "icon" : "icon-item",
      "boost" : false
    },
    "power" : {
      "optional" : false,
      "update" : true,
      "usage" : 0.5,
      "capacity" : 0.0,
      "buffered" : false,
      "icon" : "icon-power",
      "boost" : false
    }
  },
  "displayFlow" : true,
  "inEditor" : true,
  "saveConfig" : false,
  "copyConfig" : true,
  "update" : true,
  "destructible" : false,
  "unloadable" : true,
  "solid" : true,
  "solidifes" : false,
  "rotate" : false,
  "saveData" : false,
  "breakable" : false,
  "rebuildable" : true,
  "requiresWater" : false,
  "placeableLiquid" : false,
  "placeableOn" : true,
  "insulated" : false,
  "squareSprite" : true,
  "absorbLasers" : false,
  "enableDrawStatus" : true,
  "drawDisabled" : true,
  "autoResetEnabled" : true,
  "noUpdateDisabled" : false,
  "useColor" : true,
  "health" : -1,
  "baseExplosiveness" : 0.0,
  "floating" : false,
  "size" : 2,
  "offset" : 0.0,
  "expanded" : false,
  "timers" : 1,
  "cacheLayer" : "normal",
  "fillsTile" : true,
  "alwaysReplace" : false,
  "replaceable" : true,
  "group" : "none",
  "flags" : [ "factory" ],
  "priority" : "base",
  "unitCapModifier" : 0,
  "configurable" : false,
  "logicConfigurable" : false,
  "consumesTap" : false,
  "drawLiquidLight" : true,
  "sync" : true,
  "conveyorPlacement" : false,
  "swapDiagonalPlacement" : false,
  "schematicPriority" : 0,
  "mapColor" : {
    "r" : 0.0,
    "g" : 0.0,
    "b" : 0.0,
    "a" : 1.0
  },
  "hasColor" : false,
  "targetable" : true,
  "canOverdrive" : true,
  "outlineColor" : {
    "r" : 0.2509804,
    "g" : 0.2509804,
    "b" : 0.28627452,
    "a" : 1.0
  },
  "outlineIcon" : false,
  "outlinedIcon" : -1,
  "hasShadow" : true,
  "breakSound" : {
    "bus" : {
      "id" : 1,
      "disposed" : false
    },
    "disposed" : false
  },
  "albedo" : 0.0,
  "lightColor" : {
    "r" : 1.0,
    "g" : 1.0,
    "b" : 1.0,
    "a" : 1.0
  },
  "emitLight" : false,
  "lightRadius" : 60.0,
  "loopSound" : {
    "bus" : {
      "id" : 1,
      "disposed" : false
    },
    "disposed" : false
  },
  "loopSoundVolume" : 0.5,
  "ambientSound" : {
    "bus" : {
      "id" : 1,
      "disposed" : false
    },
    "disposed" : false
  },
  "ambientSoundVolume" : 0.07,
  "requirements" : [ {
    "item" : {
      "id" : 0,
      "minfo" : { },
      "name" : "copper",
      "stats" : {
        "useCategories" : false,
        "intialized" : false
      },
      "localizedName" : "Copper",
      "description" : "Used in all types of construction and ammunition.",
      "details" : "Copper. Abnormally abundant metal on Serpulo. Structurally weak unless reinforced.",
      "alwaysUnlocked" : true,
      "inlineDescription" : true,
      "iconId" : 0,
      "color" : {
        "r" : 0.8509804,
        "g" : 0.6156863,
        "b" : 0.4509804,
        "a" : 1.0
      },
      "explosiveness" : 0.0,
      "flammability" : 0.0,
      "radioactivity" : 0.0,
      "charge" : 0.0,
      "hardness" : 1,
      "cost" : 0.5,
      "lowPriority" : false,
      "contentType" : "item",
      "hidden" : false,
      "disposed" : false
    },
    "amount" : 30
  }, {
    "item" : {
      "id" : 1,
      "minfo" : { },
      "name" : "lead",
      "stats" : {
        "useCategories" : false,
        "intialized" : false
      },
      "localizedName" : "Lead",
      "description" : "Used in liquid transportation and electrical structures.",
      "details" : "Dense. Inert. Extensively used in batteries.\nNote: Likely toxic to biological life forms. Not that there are many left here.",
      "alwaysUnlocked" : true,
      "inlineDescription" : true,
      "iconId" : 0,
      "color" : {
        "r" : 0.54901963,
        "g" : 0.49803922,
        "b" : 0.6627451,
        "a" : 1.0
      },
      "explosiveness" : 0.0,
      "flammability" : 0.0,
      "radioactivity" : 0.0,
      "charge" : 0.0,
      "hardness" : 1,
      "cost" : 0.7,
      "lowPriority" : false,
      "contentType" : "item",
      "hidden" : false,
      "disposed" : false
    },
    "amount" : 25
  } ],
  "category" : "crafting",
  "buildCost" : 0.0,
  "buildVisibility" : "shown",
  "buildCostMultiplier" : 1.0,
  "deconstructThreshold" : 0.0,
  "researchCostMultiplier" : 1.0,
  "instantTransfer" : false,
  "quickRotate" : true,
  "subclass" : "mindustry.world.blocks.production.GenericSmelter",
  "buildType" : { },
  "configurations" : {
    "size" : 0,
    "empty" : true
  },
  "generatedIcons" : [ null ],
  "outputItem" : {
    "item" : {
      "id" : 9,
      "minfo" : { },
      "name" : "silicon",
      "stats" : {
        "useCategories" : false,
        "intialized" : false
      },
      "localizedName" : "Silicon",
      "description" : "Used in solar panels, complex electronics and homing turret ammunition.",
      "alwaysUnlocked" : false,
      "inlineDescription" : true,
      "iconId" : 0,
      "color" : {
        "r" : 0.3254902,
        "g" : 0.3372549,
        "b" : 0.36078432,
        "a" : 1.0
      },
      "explosiveness" : 0.0,
      "flammability" : 0.0,
      "radioactivity" : 0.0,
      "charge" : 0.0,
      "hardness" : 0,
      "cost" : 0.8,
      "lowPriority" : false,
      "contentType" : "item",
      "hidden" : false,
      "disposed" : false
    },
    "amount" : 1
  },
  "craftTime" : 40.0,
  "craftEffect" : {
    "id" : 139,
    "renderer" : { },
    "lifetime" : 15.0,
    "clip" : 50.0,
    "layer" : 110.0,
    "layerDuration" : 0.0
  },
  "updateEffect" : {
    "id" : 0,
    "renderer" : { },
    "lifetime" : 0.0,
    "clip" : 0.0,
    "layer" : 110.0,
    "layerDuration" : 0.0
  },
  "updateEffectChance" : 0.04,
  "drawer" : { },
  "flameColor" : {
    "r" : 1.0,
    "g" : 0.9372549,
    "b" : 0.6,
    "a" : 1.0
  },
  "hidden" : false,
  "static" : false,
  "accessible" : true,
  "contentType" : "block",
  "visible" : true,
  "multiblock" : true,
  "air" : false,
  "placeable" : true,
  "floor" : false,
  "overlay" : false,
  "disposed" : false
}
*/

