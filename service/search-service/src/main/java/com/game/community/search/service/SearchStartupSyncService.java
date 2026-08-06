package com.game.community.search.service;

public interface SearchStartupSyncService {

    void syncOnStartupIfEnabled();

    void rebuildNow();
}
