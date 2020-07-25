package com.sd.entitylocker;

import com.sd.entitylocker.ReentrantEntityLocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ReentrantEntityLockerTest {
    private ReentrantEntityLocker<Integer> entityLocker;

    @BeforeEach
    public void beforeEach() {
        entityLocker = new ReentrantEntityLocker<>(5);
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    public void lockAndUnlock_withoutTimeout() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executorService.execute(() -> {
            entityLocker.lock(entity.getId());
            for (int i = 0; i < 1000; i++) {
                entity.incrementCounter();
            }
            entityLocker.unlock(entity.getId());
            latch.countDown();
        });

        executorService.execute(() -> {
            entityLocker.lock(entity.getId());
            for (int i = 0; i < 1000; i++) {
                entity.incrementCounter();
            }
            entityLocker.unlock(entity.getId());
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(2000, entity.getCounter());
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    public void lockAndUnlock_withTimeout() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executorService.execute(() -> {
            entityLocker.lock(entity.getId(), 100);
            for (int i = 0; i < 1000; i++) {
                entity.incrementCounter();
            }
            entityLocker.unlock(entity.getId());
            latch.countDown();
        });

        executorService.execute(() -> {
            entityLocker.lock(entity.getId(), 100);
            for (int i = 0; i < 1000; i++) {
                entity.incrementCounter();
            }
            entityLocker.unlock(entity.getId());
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(2000, entity.getCounter());
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    public void lock_withTimeout_timeout() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        assertThrows(IllegalMonitorStateException.class, () -> {
            executorService.execute(() -> {
                entityLocker.lock(entity.getId());
                latch.countDown();
            });

            latch.await();
            entityLocker.lock(entity.getId(), 1);
            fail();
        });
    }

    @Test
    public void unlock_withoutLock() {
        Entity entity = new Entity(0);

        assertThrows(IllegalMonitorStateException.class, () -> entityLocker.unlock(entity.getId()));
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    public void unlock_fromOtherThread() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        assertThrows(IllegalMonitorStateException.class, () -> {
            executorService.execute(() -> {
                entityLocker.lock(entity.getId());
                latch.countDown();
            });

            latch.await();
            entityLocker.unlock(entity.getId());
            fail();
        });
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    public void lockGlobal() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executorService.execute(() -> {
            entityLocker.lockGlobal();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < 1000; i++) {
                entity.incrementCounter();
            }
            entityLocker.unlockGlobal();
            latch.countDown();
        });

        executorService.execute(() -> {
            entityLocker.lock(entity.getId());
            for (int i = 0; i < 1000; i++) {
                entity.incrementCounter();
            }
            entityLocker.unlock(entity.getId());
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(2000, entity.getCounter());
    }

    @Test
    public void lockGlobal_globalBeforeLocal_timeout() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        assertThrows(IllegalMonitorStateException.class, () -> {
            executorService.execute(() -> {
                entityLocker.lockGlobal();
                latch.countDown();
            });

            latch.await();
            entityLocker.lock(entity.getId(), 10);
            fail();
        });
    }

    @Test
    public void lockGlobal_globalAfterLocal_timeout() {
        Entity entity = new Entity(0);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        assertThrows(IllegalMonitorStateException.class, () -> {
            executorService.execute(() -> {
                entityLocker.lock(entity.getId());
                latch.countDown();
            });

            latch.await();
            entityLocker.lockGlobal(10);
            fail();
        });
    }

    @Test
    public void unlockGlobal_withoutLock() {
        assertThrows(IllegalMonitorStateException.class, () -> entityLocker.unlockGlobal());
    }

    @Test
    public void unlockGlobal_fromOtherThread() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        assertThrows(IllegalMonitorStateException.class, () -> {
            executorService.execute(() -> {
                entityLocker.lockGlobal();
                latch.countDown();
            });

            latch.await();
            entityLocker.unlockGlobal();
            fail();
        });
    }

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    public void lock_deadlock() {
        Entity entity1 = new Entity(0);
        Entity entity2 = new Entity(1);

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        IllegalMonitorStateException exception = assertThrows(IllegalMonitorStateException.class, () -> {
            executorService.execute(() -> {
                entityLocker.lock(entity1.getId());
                entityLocker.lock(entity2.getId());
            });

            entityLocker.lock(entity2.getId());
            Thread.sleep(100);
            entityLocker.lock(entity1.getId());
        });

        assertTrue(exception.getMessage().startsWith("Deadlock"));
    }

    @Test
    public void lock_esc() {
        CountDownLatch latch = new CountDownLatch(1);

        assertThrows(IllegalMonitorStateException.class, () -> {
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            executorService.execute(() -> {
                for (int i = 0; i < 5; i++) {
                    entityLocker.lock(i);
                }
                latch.countDown();
            });

            latch.await();
            entityLocker.lock(5, 10);
        });
    }

    private static class Entity {
        private final int id;
        private int counter = 0;

        Entity(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getCounter() {
            return counter;
        }

        public int incrementCounter() {
            return ++counter;
        }
    }
}
