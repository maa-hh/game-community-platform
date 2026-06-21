package com.game.community.search.service;

import com.game.community.model.entity.search.SearchHistory;

import java.util.List;

public interface SearchRecordService {

    void addRecord(Long userId, String keyword);

    List<SearchHistory> getRecords(Long userId);

    boolean deleteRecord(Long userId, Long id);

    long clearRecords(Long userId);
}
