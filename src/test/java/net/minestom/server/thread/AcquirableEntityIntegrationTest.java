package net.minestom.server.thread;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public class AcquirableEntityIntegrationTest {

    @Test
    public void instanceSet(Env env) throws InterruptedException {
        var zombie = new Entity(EntityType.ZOMBIE);
        CountDownLatch latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            assertTrue(zombie.acquirable().isOwned());
            assertTrue(zombie.acquirable().isLocal());
            latch.countDown();
        });
        latch.await();
    }
}
