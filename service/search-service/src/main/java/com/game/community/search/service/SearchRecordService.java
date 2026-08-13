package com.game.community.search.service;

import com.game.community.model.vo.search.SearchHistoryVO;

import java.util.List;

public interface SearchRecordService {

    void addRecord(Long userId, String keyword);

    List<SearchHistoryVO> getRecords(Long userId);

    boolean deleteRecord(Long userId, Long id);

    long clearRecords(Long userId);
}
