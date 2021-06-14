import arc.ApplicationCore;
import arc.backend.headless.HeadlessApplication;
import arc.util.Log;
import arc.util.Time;
import mindustry.MindustrySerializer;
import mindustry.Vars;
import mindustry.core.FileTree;
import mindustry.core.GameState.State;
import mindustry.core.Logic;
import mindustry.core.NetServer;
import mindustry.core.World;
import mindustry.ctype.ContentType;
import mindustry.maps.Map;
import mindustry.net.Net;
import mindustry.world.Block;
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
                //noinspection BusyWait
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
            Block b = (Block) o;

            if( b.name.equals("air")
                || b.name.equals("spawn"))
                theBlocks[i++] = b;
        }

        MindustrySerializer.serializeBlocks("c:\\MikesStuff\\MindustryInfo.json", theBlocks);
    }
}