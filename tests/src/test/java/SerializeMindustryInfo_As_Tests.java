import arc.ApplicationCore;
import arc.Core;
import arc.backend.headless.HeadlessApplication;
import arc.func.Cons;
import arc.math.Rand;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue.ValueType;
import arc.util.serialization.Jval;
import mindustry.MindustrySerializer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.UnitTypes;
import mindustry.core.FileTree;
import mindustry.core.GameState.State;
import mindustry.core.Logic;
import mindustry.core.NetServer;
import mindustry.core.World;
import mindustry.ctype.ContentType;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Builderc;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.net.Net;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Edges;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class SerializeMindustryInfo_As_Tests {
    static Map testMap;
    static boolean initialized;

    @BeforeAll
    static void launchApplication() {
        //only gets called once
        if (initialized) return;
        initialized = true;

        try {
            boolean[] begins = {false};
            Throwable[] exceptionThrown = {null};
            Log.useColors = false;

            ApplicationCore core = new ApplicationCore() {
                @Override
                public void setup() {
                    headless = true;
                    net = new Net(null);
                    tree = new FileTree();
                    Vars.init();
                    world = new World() {
                        @Override
                        public float getDarkness(int x, int y) {
                            //for world borders
                            return 0;
                        }
                    };
                    content.createBaseContent();

                    add(logic = new Logic());
                    add(netServer = new NetServer());

                    content.init();

                }

                @Override
                public void init() {
                    super.init();
                    begins[0] = true;
                    testMap = maps.loadInternalMap("groundZero");
                    Thread.currentThread().interrupt();
                }
            };

            new HeadlessApplication(core, throwable -> exceptionThrown[0] = throwable);

            while (!begins[0]) {
                if (exceptionThrown[0] != null) {
                    fail(exceptionThrown[0]);
                }
                Thread.sleep(10);
            }


            Block block = content.getByName(ContentType.block, "build2");
            assertEquals("build2", block == null ? null : block.name, "2x2 construct block doesn't exist?");
        } catch (Throwable r) {
            fail(r);
        }
    }

    @BeforeEach
    void resetWorld() {
        Time.setDeltaProvider(() -> 1f);
        logic.reset();
        state.set(State.menu);
    }

    @Test
    void serializeBlocks() {
        System.out.println("TEST: SERIALIZE BLOCKS\nWe will attempt to serialize these blocks:\n\t");
        for (Block b : content.blocks()) {
            System.out.print(b.name + ", ");
        }
        System.out.println();

        Object[] objArray = content.blocks().toArray();

        Block[] theBlocks = new Block[objArray.length];
        int i = 0;
        for (Object o : objArray) {
            theBlocks[i++] = (Block) o;
        }

        MindustrySerializer.serializeBlocks("c:\\MikesStuff\\MindustryInfo.json", theBlocks);
    }
}