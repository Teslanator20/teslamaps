package com.teslamaps.adm.leap.splits;

// Ported from ADM's splits/BossMoments.kt
public final class BossMoments {

    private static final String WATCHER_HANDLE = "[BOSS] The Watcher: Let's see how you can handle this.";

    private Stamp watcherHandle;
    private Stamp watcherMoved;
    private Stamp allIn;

    public void reset() { watcherHandle = null; watcherMoved = null; allIn = null; }

    public Stamp watcherHandle() { return watcherHandle; }

    public void onChat(String msg, Stamp at) {
        if (msg.equals(WATCHER_HANDLE) && watcherHandle == null) watcherHandle = at;
    }

    public void onWatcherMoved(Stamp at) { if (watcherHandle != null && watcherMoved == null) watcherMoved = at; }

    public boolean waitingForWatcher() { return watcherHandle != null && watcherMoved == null; }

    public void onEveryoneInCore(Stamp at) { if (allIn == null) allIn = at; }

    public boolean waitingForCore() { return allIn == null; }
}
