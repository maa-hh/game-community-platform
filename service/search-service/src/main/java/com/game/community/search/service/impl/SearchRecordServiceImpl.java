package com.game.community.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.model.entity.search.SearchHistory;
import com.game.community.common.constant.search.SearchConstants;
import com.game.community.search.mapper.SearchHistoryMapper;
import com.game.community.search.service.SearchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchRecordServiceImpl implements SearchRecordService {

    private final SearchHistoryMapper searchHistoryMapper;

    @Override
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void addRecord(Long userId, String keyword) {
        if (userId == null || !StringUtils.hasText(keyword)) {
            return;
        }
        String normalized = keyword.trim();
        LocalDateTime now = LocalDateTime.now();
        searchHistoryMapper.upsert(userId, normalized, now);
        trimOldRecords(userId);
    }

    @Override
    public List<SearchHistory> getRecords(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getUpdateTime)
                .orderByDesc(SearchHistory::getId)
                .last("LIMIT " + SearchConstants.SEARCH_HISTORY_MAX_RECORDS));
    }

    @Override
    public boolean deleteRecord(Long userId, Long id) {
        if (userId == null || id == null) {
            return false;
        }
        return searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getId, id)
                .eq(SearchHistory::getUserId, userId)) > 0;
    }

    @Override
    public long clearRecords(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId));
    }

    private void trimOldRecords(Long userId) {
        searchHistoryMapper.deleteExcess(userId, SearchConstants.SEARCH_HISTORY_MAX_RECORDS);
    }
}
