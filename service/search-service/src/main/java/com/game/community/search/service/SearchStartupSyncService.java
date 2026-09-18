package com.game.community.search.service;

public interface SearchStartupSyncService {

    boolean syncOnStartupIfEnabled();

    boolean rebuildAsync();

    void rebuildNow();
}
