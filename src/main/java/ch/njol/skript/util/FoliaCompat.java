package ch.njol.skript.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for Folia and Paper/Spigot scheduler compatibility.
 * <p>
 * Folia replaces the single-threaded tick model with regionalized
 * multithreading.
 * This class provides methods that work correctly on both Folia and non-Folia
 * servers
 * by delegating to the appropriate scheduler at runtime.
 * </p>
 * <p>
 * <b>Scheduler types on Folia:</b>
 * <ul>
 * <li><b>Global Scheduler</b> — for tasks not tied to any specific
 * region/entity (e.g. plugin init)</li>
 * <li><b>Region Scheduler</b> — for tasks operating on blocks at a specific
 * location</li>
 * <li><b>Entity Scheduler</b> — for tasks operating on a specific entity</li>
 * <li><b>Async Scheduler</b> — for asynchronous operations</li>
 * </ul>
 * </p>
 */
public final class FoliaCompat {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private FoliaCompat() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @return Whether the server is running Folia.
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    // ================ GLOBAL REGION TASKS (no location/entity context)
    // ================

    /**
     * Runs a task on the next tick of the global region (Folia) or the main thread
     * (Paper/Spigot).
     * Use this for tasks that don't require a specific world location or entity
     * context.
     *
     * @param plugin The plugin owning the task.
     * @param task   The task to run.
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a task after a delay on the global region (Folia) or the main thread
     * (Paper/Spigot).
     *
     * @param plugin     The plugin owning the task.
     * @param task       The task to run.
     * @param delayTicks Delay in ticks.
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            runTask(plugin, task);
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Schedules a repeating task on the global region (Folia) or the main thread
     * (Paper/Spigot).
     *
     * @param plugin      The plugin owning the task.
     * @param task        The task to run.
     * @param delayTicks  Initial delay in ticks.
     * @param periodTicks Period between executions in ticks.
     * @return A {@link FoliaTaskHandle} that can be used to cancel the task.
     */
    public static FoliaTaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, st -> task.run(),
                            Math.max(delayTicks, 1), Math.max(periodTicks, 1));
            return new FoliaTaskHandle(scheduledTask);
        } else {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
            return new FoliaTaskHandle(id);
        }
    }

    /**
     * Schedules a sync delayed task on the global region (Folia) or the main thread
     * (Paper/Spigot).
     * This is a drop-in replacement for
     * {@code Bukkit.getScheduler().scheduleSyncDelayedTask(...)}.
     *
     * @param plugin     The plugin owning the task.
     * @param task       The task to run.
     * @param delayTicks Delay in ticks (0 means next tick).
     */
    public static void scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            runTask(plugin, task);
        } else {
            runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Schedules a sync delayed task on the global region (Folia) or the main thread
     * (Paper/Spigot).
     * Runs on the next tick.
     *
     * @param plugin The plugin owning the task.
     * @param task   The task to run.
     */
    public static void scheduleSyncDelayedTask(Plugin plugin, Runnable task) {
        runTask(plugin, task);
    }

    // ================ LOCATION-BASED REGION TASKS ================

    /**
     * Runs a task on the region that owns the given location.
     * On non-Folia servers, this runs on the main thread.
     *
     * @param plugin   The plugin owning the task.
     * @param location The location whose region should run the task.
     * @param task     The task to run.
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a task after a delay on the region that owns the given location.
     *
     * @param plugin     The plugin owning the task.
     * @param location   The location whose region should run the task.
     * @param task       The task to run.
     * @param delayTicks Delay in ticks.
     */
    public static void runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            runAtLocation(plugin, location, task);
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // ================ ENTITY-BASED TASKS ================

    /**
     * Runs a task on the thread that owns the given entity.
     * On non-Folia servers, this runs on the main thread.
     *
     * @param plugin  The plugin owning the task.
     * @param entity  The entity whose thread should run the task.
     * @param task    The task to run.
     * @param retired The task to run if the entity is removed before the task is
     *                executed. May be null.
     */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task, @Nullable Runnable retired) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), retired);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a task after a delay on the thread that owns the given entity.
     *
     * @param plugin     The plugin owning the task.
     * @param entity     The entity whose thread should run the task.
     * @param task       The task to run.
     * @param retired    The task to run if the entity is removed before the task is
     *                   executed. May be null.
     * @param delayTicks Delay in ticks.
     */
    public static void runOnEntityLater(Plugin plugin, Entity entity, Runnable task,
            @Nullable Runnable retired, long delayTicks) {
        if (delayTicks <= 0) {
            runOnEntity(plugin, entity, task, retired);
            return;
        }
        if (IS_FOLIA) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), retired, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // ================ ASYNC TASKS ================

    /**
     * Runs a task asynchronously.
     *
     * @param plugin The plugin owning the task.
     * @param task   The task to run.
     */
    public static void runTaskAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs a task asynchronously after a delay.
     *
     * @param plugin     The plugin owning the task.
     * @param task       The task to run.
     * @param delayTicks Delay in ticks — converted to ms for Folia's async
     *                   scheduler (1 tick ≈ 50ms).
     */
    public static void runTaskAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            runTaskAsync(plugin, task);
            return;
        }
        if (IS_FOLIA) {
            long delayMs = delayTicks * 50;
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(),
                    delayMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    /**
     * Schedules a repeating asynchronous task.
     *
     * @param plugin      The plugin owning the task.
     * @param task        The task to run.
     * @param delayTicks  Initial delay in ticks.
     * @param periodTicks Period between executions in ticks.
     * @return A {@link FoliaTaskHandle} that can be used to cancel the task.
     */
    public static FoliaTaskHandle runTaskTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            long delayMs = Math.max(delayTicks, 1) * 50;
            long periodMs = Math.max(periodTicks, 1) * 50;
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = Bukkit.getAsyncScheduler()
                    .runAtFixedRate(plugin, st -> task.run(),
                            delayMs, periodMs, TimeUnit.MILLISECONDS);
            return new FoliaTaskHandle(scheduledTask);
        } else {
            int id = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
                    .getTaskId();
            return new FoliaTaskHandle(id);
        }
    }

    // ================ LOCATION-BASED REPEATING TASKS ================

    /**
     * Schedules a repeating task on the region that owns the given location.
     * On non-Folia servers, this runs on the main thread.
     *
     * @param plugin      The plugin owning the task.
     * @param location    The location whose region should run the task.
     * @param task        The task to run.
     * @param delayTicks  Initial delay in ticks.
     * @param periodTicks Period between executions in ticks.
     * @return A {@link FoliaTaskHandle} that can be used to cancel the task.
     */
    public static FoliaTaskHandle runAtLocationTimer(Plugin plugin, Location location, Runnable task,
            long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = Bukkit.getRegionScheduler()
                    .runAtFixedRate(plugin, location, st -> task.run(),
                            Math.max(delayTicks, 1), Math.max(periodTicks, 1));
            return new FoliaTaskHandle(scheduledTask);
        } else {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
            return new FoliaTaskHandle(id);
        }
    }

    // ================ ENTITY-BASED REPEATING TASKS ================

    /**
     * Schedules a repeating task on the thread that owns the given entity.
     * On non-Folia servers, this runs on the main thread.
     *
     * @param plugin      The plugin owning the task.
     * @param entity      The entity whose thread should run the task.
     * @param task        The task to run.
     * @param retired     The task to run if the entity is removed. May be null.
     * @param delayTicks  Initial delay in ticks.
     * @param periodTicks Period between executions in ticks.
     * @return A {@link FoliaTaskHandle} that can be used to cancel the task.
     */
    public static FoliaTaskHandle runOnEntityTimer(Plugin plugin, Entity entity, Runnable task,
            @Nullable Runnable retired, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = entity.getScheduler()
                    .runAtFixedRate(plugin, st -> task.run(), retired,
                            Math.max(delayTicks, 1), Math.max(periodTicks, 1));
            return new FoliaTaskHandle(scheduledTask);
        } else {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
            return new FoliaTaskHandle(id);
        }
    }

    // ================ TASK CANCELLATION ================

    /**
     * Cancels all tasks owned by the given plugin.
     * On Folia, this cancels tasks on the global region scheduler and the async
     * scheduler.
     * On Paper/Spigot, this delegates to
     * {@code Bukkit.getScheduler().cancelTasks(plugin)}.
     *
     * @param plugin The plugin whose tasks to cancel.
     */
    public static void cancelTasks(Plugin plugin) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    // ================ SYNC METHOD CALLING ================

    /**
     * Calls a method synchronously and returns a Future for the result.
     * <p>
     * On Folia, {@code Bukkit.getScheduler().callSyncMethod()} is not available.
     * Instead, this uses the global region scheduler to execute the callable
     * and returns a {@link java.util.concurrent.CompletableFuture}.
     * </p>
     * <p>
     * On Paper/Spigot, this delegates to
     * {@code Bukkit.getScheduler().callSyncMethod(...)}.
     * </p>
     *
     * @param plugin   The plugin owning the task.
     * @param callable The callable to run.
     * @param <T>      The return type of the callable.
     * @return A Future representing the result.
     */
    public static <T> java.util.concurrent.Future<T> callSyncMethod(Plugin plugin,
            java.util.concurrent.Callable<T> callable) {
        if (IS_FOLIA) {
            java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        } else {
            return Bukkit.getScheduler().callSyncMethod(plugin, callable);
        }
    }

    // ================ THREAD CHECKS ================

    /**
     * Checks if the current thread is a "tick thread" — the primary thread on
     * Paper/Spigot,
     * or any region tick thread on Folia.
     * <p>
     * On non-Folia servers, this is equivalent to {@link Bukkit#isPrimaryThread()}.
     * On Folia, this checks if the current thread is any tick thread (region or
     * global).
     * </p>
     *
     * @return Whether the current thread is a tick thread.
     */
    public static boolean isTickThread() {
        if (IS_FOLIA) {
            // On Folia, Bukkit.isPrimaryThread() is often false, so we use the Folia API
            try {
                return Bukkit.isPrimaryThread(); // Paper 1.20+ has this method return true for any tick thread
            } catch (Exception e) {
                return false;
            }
        } else {
            return Bukkit.isPrimaryThread();
        }
    }

    // ================ TASK HANDLE ================

    /**
     * A wrapper that can cancel both Folia ScheduledTask and legacy Bukkit task
     * IDs.
     */
    public static final class FoliaTaskHandle {
        @Nullable
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask;
        private final int bukkitTaskId;

        FoliaTaskHandle(io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask) {
            this.foliaTask = foliaTask;
            this.bukkitTaskId = -1;
        }

        FoliaTaskHandle(int bukkitTaskId) {
            this.foliaTask = null;
            this.bukkitTaskId = bukkitTaskId;
        }

        /**
         * Cancels this task.
         */
        public void cancel() {
            if (foliaTask != null) {
                foliaTask.cancel();
            } else if (bukkitTaskId != -1) {
                Bukkit.getScheduler().cancelTask(bukkitTaskId);
            }
        }

        /**
         * @return Whether this task is currently running or queued.
         */
        public boolean isActive() {
            if (foliaTask != null) {
                io.papermc.paper.threadedregions.scheduler.ScheduledTask.ExecutionState state = foliaTask
                        .getExecutionState();
                return state == io.papermc.paper.threadedregions.scheduler.ScheduledTask.ExecutionState.IDLE
                        || state == io.papermc.paper.threadedregions.scheduler.ScheduledTask.ExecutionState.RUNNING;
            } else if (bukkitTaskId != -1) {
                return Bukkit.getScheduler().isQueued(bukkitTaskId)
                        || Bukkit.getScheduler().isCurrentlyRunning(bukkitTaskId);
            }
            return false;
        }

        /**
         * @return The Bukkit task ID, or -1 if this is a Folia task.
         */
        public int getBukkitTaskId() {
            return bukkitTaskId;
        }
    }
}
