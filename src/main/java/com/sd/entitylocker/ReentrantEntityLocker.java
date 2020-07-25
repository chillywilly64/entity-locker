package com.sd.entitylocker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation using {@link ReentrantLock} and {@link ConcurrentHashMap}
 * Before locking checking deadlock and after lock can escalate to global if thread has locked too many entities
 * TODO Move maps to special class, maybe create custom exceptions and maps cleaning
 * @param <ID> Entities ID
 */
public class ReentrantEntityLocker<ID> implements EntityLocker<ID> {
    /**
     * There are several maps to different checking
     * {@link #locks} is lock storage for each ID, which were locked
     * {@link #lockedEntities} is map, which allows to understand which thread blocked id
     * {@link #waitingThreads} is map, which allows to understand which ID thread is waiting\
     * {@link #threadEntityCounters} is map, which store count of locked entities for each thread
     */
    private final Map<ID,ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<ID,Thread> lockedEntities = new ConcurrentHashMap<>();
    private final Map<Thread,ID> waitingThreads = new ConcurrentHashMap<>();
    private final Map<Thread,AtomicInteger> threadEntityCounters = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    private final int countForEscalateToGlobal;

    /**
     * Create ReentrantEntityLocked
     * @param countForEscalateToGlobal parameter that means how many entities the thread should lock for escalation to global lock
     */
    public ReentrantEntityLocker(int countForEscalateToGlobal) {
        this.countForEscalateToGlobal = countForEscalateToGlobal;
    }

    @Override
    public void lock(ID id) {
        beforeLock(id);

        ReentrantLock lock = locks.computeIfAbsent(id, i -> new ReentrantLock());
        if (lock.isLocked()) {
            checkDeadlock(id);
        }
        lock.lock();

        afterLock(id);
    }

    @Override
    public void lock(ID id, long timeout) {
        lock(id, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void lock(ID id, long timeout, TimeUnit timeUnit) {
        boolean isLocked;
        try {
            isLocked = globalLock.readLock().tryLock(timeout, timeUnit);
            isLocked = isLocked && locks.computeIfAbsent(id, i -> new ReentrantLock()).tryLock(timeout, timeUnit);
        } catch (InterruptedException e) {
            isLocked = false;
        }
        if (!isLocked) {
            throw new IllegalMonitorStateException("Cannot lock " + id);
        }
    }

    @Override
    public void unlock(ID id) {
        ReentrantLock lock = locks.get(id);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            globalLock.readLock().unlock();
            afterUnlock(id);
        } else {
            throw new IllegalMonitorStateException("Cannot unlock " + id);
        }
    }

    @Override
    public void lockGlobal() {
        globalLock.writeLock().lock();
    }

    @Override
    public void lockGlobal(long timeout) {
        lockGlobal(timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void lockGlobal(long timeout, TimeUnit timeUnit) {
        boolean isLocked;
        try {
            isLocked = globalLock.writeLock().tryLock(timeout, timeUnit);
        } catch (InterruptedException e) {
            isLocked = false;
        }
        if (!isLocked) {
            throw new IllegalMonitorStateException("Cannot lock global");
        }
    }

    @Override
    public void unlockGlobal() {
        if (globalLock.isWriteLockedByCurrentThread()) {
            globalLock.writeLock().unlock();
        } else {
            throw new IllegalMonitorStateException("Cannot unlock global");
        }
    }

    /**
     * Before locking, putting id to the {@link #waitingThreads}
     * and if this is the only locking of the thread, then read lock on global
     * @param id id to be locked
     */
    private void beforeLock(ID id) {
        Thread currentThread = Thread.currentThread();

        waitingThreads.put(currentThread, id);

        AtomicInteger entitiesCounter = threadEntityCounters.get(currentThread);
        if (entitiesCounter == null || entitiesCounter.get() == 0) {
            globalLock.readLock().lock();
        }
    }

    /**
     * After locking, add the id to the {@link #lockedEntities} and remove it from @{@link #waitingThreads},
     * then check the number of locked entities of this thread and if there are enough of them, then write lock on global
     * @param id id to be locked
     */
    private void afterLock(ID id) {
        Thread currentThread = Thread.currentThread();

        lockedEntities.put(id, currentThread);
        waitingThreads.remove(currentThread);

        int counter = threadEntityCounters
                .computeIfAbsent(currentThread, i -> new AtomicInteger())
                .incrementAndGet();
        if (countForEscalateToGlobal == counter) {
            globalLock.readLock().unlock();
            lockGlobal();
            System.out.println(String.format("Thread %s lock %d entities, lock escalated to global",
                    currentThread.getName(), counter));
        }
    }

    /**
     * To check the deadlock, checking the thread that locked the current id for waiting for the id
     * that is locked by the current thread,
     * otherwise calling it recursively, because there may be more than two threads in a possible deadlock
     * @param id id to be locked
     */
    private void checkDeadlock(ID id) {
        Thread lockThread = lockedEntities.get(id);
        ID awaitId = waitingThreads.get(lockThread);

        if (awaitId == null || !lockedEntities.containsKey(awaitId)) {
            return;
        }

        Thread currentThread = Thread.currentThread();
        if (currentThread == lockedEntities.get(awaitId)) {
            throw new IllegalMonitorStateException(
                    String.format("Deadlock for %s and %s", currentThread.getName(), lockThread.getName()));
        } else {
            checkDeadlock(awaitId);
        }
    }

    /**
     * After locking, remove id from @{@link #lockedEntities},
     * check the number of locked entities of this thread and if there are not enough of them, then release write lock on global
     * @param id id to be locked
     */
    private void afterUnlock(ID id) {
        lockedEntities.remove(id);

        int counter = threadEntityCounters
                .computeIfAbsent(Thread.currentThread(), i -> new AtomicInteger())
                .getAndDecrement();
        if (countForEscalateToGlobal == counter) {
            unlockGlobal();
            System.out.println(String.format("Thread %s lock %d entities, lock deescalated to local",
                    Thread.currentThread().getName(), counter));
        }
    }
}
