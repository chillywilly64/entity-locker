package com.sd.entitylocker;

import java.util.concurrent.TimeUnit;

/**
 * This class for locking and unlocking entities, it only does deal with it's ID
 * Support different entity ID type
 * @param <ID> Entities ID
 */
public interface EntityLocker<ID> {

    /**
     * Locking on id
     * @param id entity's ID to be locked
     */
    void lock(ID id);

    /**
     * Same as {@link #lock(ID)}, but can specify timeout for locking
     * @param id entity's ID to be locked
     * @param timeout time to wait for lock
     */
    void lock(ID id, long timeout);

    /**
     * Same as {@link #lock(ID, long)}, but can specify @{@link TimeUnit}
     * @param id entity's ID to be locked
     * @param timeout time to wait for lock
     * @param timeUnit the time unit of the timeout argument
     */
    void lock(ID id, long timeout, TimeUnit timeUnit);

    /**
     * Unlocking entity
     * @param id entity's ID to be locked
     */
    void unlock(ID id);

    /**
     * Global lock
     * No one thread can lock any entity except current
     */
    void lockGlobal();

    /**
     * Same as {@link #lockGlobal()}, but can specify timeout for locking
     * @param timeout time to wait for lock
     */
    void lockGlobal(long timeout);

    /**
     * Same as {@link #lockGlobal(long)}, but can specify @{@link TimeUnit}
     * @param timeout time to wait for lock
     * @param timeUnit the time unit of the timeout argument
     */
    void lockGlobal(long timeout, TimeUnit timeUnit);

    /**
     * Release global lock
     */
    void unlockGlobal();
}
