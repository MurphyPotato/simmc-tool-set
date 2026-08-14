package com.murphypotato.simmctoolset.internal.accessory.client;

public final class ToolSessionGuard {
    private ToolSessionGuard() {
    }

    public static boolean canRestore(
        boolean playerAvailable,
        Object currentHandler,
        Object originalHandler,
        int currentSyncId,
        int originalSyncId
    ) {
        return playerAvailable
            && currentHandler == originalHandler
            && currentSyncId == originalSyncId;
    }
}
