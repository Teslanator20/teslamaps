package com.teslamaps.utils;

import java.util.concurrent.CopyOnWriteArrayList;

/** Runs a task once after N client ticks. */
public class TickScheduler {

    private record Task(int delay, Runnable run, int[] elapsed) {}

    private static final CopyOnWriteArrayList<Task> tasks = new CopyOnWriteArrayList<>();

    public static void schedule(int ticks, Runnable task) {
        tasks.add(new Task(Math.max(ticks, 0), task, new int[]{0}));
    }

    public static void tick() {
        for (Task task : tasks) {
            if (++task.elapsed()[0] < task.delay()) continue;
            tasks.remove(task);
            try {
                task.run().run();
            } catch (Exception ignored) {
            }
        }
    }
}
