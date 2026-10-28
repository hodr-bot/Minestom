package net.minestom.server.instance;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public class ModelViewIntegrationTest {

    @Test
    public void modelIsNotRegistrable(Env env) {
        var model = new ModelInstance(UUID.randomUUID(), DimensionType.OVERWORLD);
        assertThrows(IllegalStateException.class,
                () -> env.process().instance().registerInstance(model));
    }

    @Test
    public void viewsShareChunksAndSplitEntities(Env env) {
        var manager = env.process().instance();

        var model = new ModelInstance(UUID.randomUUID(), DimensionType.OVERWORLD);
        model.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.STONE));
        model.loadChunk(0, 0).join();

        var viewA = model.createView();
        var viewB = model.createView();
        manager.registerInstance(viewA);
        manager.registerInstance(viewB);

        assertEquals(Block.STONE, viewA.getBlock(0, 10, 0));
        assertEquals(Block.STONE, viewB.getBlock(0, 10, 0));
        assertSame(viewA.getChunk(0, 0), viewB.getChunk(0, 0));

        assertThrows(UnsupportedOperationException.class,
                () -> viewA.setBlock(0, 10, 0, Block.DIAMOND_BLOCK));

        var zombie = new Entity(EntityType.ZOMBIE);
        zombie.setInstance(viewA, new Pos(0, 41, 0)).join();

        assertTrue(viewA.getEntities().contains(zombie));
        assertTrue(viewB.getEntities().isEmpty());

        zombie.setInstance(viewB, new Pos(8, 41, 8)).join();

        assertTrue(viewA.getEntities().isEmpty());
        assertTrue(viewB.getEntities().contains(zombie));

        env.tick();

        assertTrue(viewA.getTickCount() > 0);
        assertTrue(viewB.getTickCount() > 0);
    }

    @Test
    public void autoBudgetSplitsTickAcrossInstances(Env env) {
        var manager = env.process().instance();

        var model = new ModelInstance(UUID.randomUUID(), DimensionType.OVERWORLD);
        var viewA = model.createView();
        var viewB = model.createView();
        manager.registerInstance(viewA);
        manager.registerInstance(viewB);

        final int count = manager.getInstances().size();
        assertEquals((double) MinecraftServer.TICK_MS / count, viewA.getTickBudget());
        assertTrue(viewA.isTickBudgetAutomatic());

        viewA.setTickBudget(10);
        assertEquals(10, viewA.getTickBudget());
        assertFalse(viewA.isTickBudgetAutomatic());

        viewA.setTickBudgetAutomatic();
        assertTrue(viewA.isTickBudgetAutomatic());
        assertEquals((double) MinecraftServer.TICK_MS / count, viewA.getTickBudget());
    }
}
