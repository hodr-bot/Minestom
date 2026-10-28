package net.minestom.server.thread;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single owner thread per instance: acquisition never blocks and everything
 * reachable is always local and owned, from any thread.
 */
@EnvTest
public class AcquirableBasicIntegrationTest {

    @Test
    public void localTest(Env env) {
        var instance = env.createFlatInstance();

        var zombie = new Entity(EntityType.ZOMBIE) {
            @Override
            public void tick(long time) {
                super.tick(time);
                assertTrue(this.acquirable().isLocal());
            }
        };
        zombie.setInstance(instance, new Pos(1, 41, 1)).join();
        var acquirable = zombie.acquirable();
        assertTrue(acquirable.isLocal());
        acquirable.sync(_ -> assertTrue(acquirable.isLocal()));
        Thread.startVirtualThread(() -> assertTrue(acquirable.isLocal()));

        env.tick(); // Ensure the entity can access itself

        assertTrue(acquirable.isLocal());
        acquirable.sync(_ -> assertTrue(acquirable.isLocal()));
        Thread.startVirtualThread(() -> assertTrue(acquirable.isLocal()));
    }

    @Test
    public void ownedTest(Env env) {
        var instance = env.createFlatInstance();

        var zombie = new Entity(EntityType.ZOMBIE) {
            @Override
            public void tick(long time) {
                super.tick(time);
                assertTrue(this.acquirable().isOwned());
            }
        };
        zombie.setInstance(instance, new Pos(1, 41, 1)).join();
        var acquirable = zombie.acquirable();
        assertTrue(acquirable.isOwned());
        acquirable.sync(_ -> assertTrue(acquirable.isOwned()));
        Thread.startVirtualThread(() -> assertTrue(acquirable.isOwned()));

        env.tick(); // Ensure the entity can access itself

        assertTrue(acquirable.isOwned());
        acquirable.sync(_ -> assertTrue(acquirable.isOwned()));
        Thread.startVirtualThread(() -> assertTrue(acquirable.isOwned()));
    }

    @Test
    public void acquireSingleThreadInit(Env env) {
        // Ensure that acquisition before and after initialization are properly handled
        var instance = env.createFlatInstance();

        var zombie = new Entity(EntityType.ZOMBIE);
        var acquirable = zombie.acquirable();

        zombie.setInstance(instance, new Pos(1, 41, 1)).join();
        env.tick(); // Init entity

        AtomicInteger counter = new AtomicInteger(0);

        acquirable.sync(_ -> counter.incrementAndGet());
        assertEquals(1, counter.get());

        acquirable.sync(_ -> counter.incrementAndGet());
        assertEquals(2, counter.get());
    }

    @Test
    public void acquireBeforeInit(Env env) throws InterruptedException {
        // Acquisition works from any thread, even before initialization
        var zombie = new Entity(EntityType.ZOMBIE);
        var acquirable = zombie.acquirable();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);
        Thread.startVirtualThread(() -> {
            acquirable.sync(_ -> counter.incrementAndGet());
            latch.countDown();
        });
        latch.await();
        assertEquals(1, counter.get());
    }
}
