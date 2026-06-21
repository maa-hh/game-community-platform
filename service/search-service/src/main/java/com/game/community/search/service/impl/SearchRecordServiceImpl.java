package com.game.community.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.model.entity.search.SearchHistory;
import com.game.community.search.mapper.SearchHistoryMapper;
import com.game.community.search.service.SearchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchRecordServiceImpl implements SearchRecordService {

    private static final int MAX_RECORD_COUNT = 10;

    private final SearchHistoryMapper searchHistoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRecord(Long userId, String keyword) {
        if (userId == null || !StringUtils.hasText(keyword)) {
            return;
        }
        String normalized = keyword.trim();
        LocalDateTime now = LocalDateTime.now();
        int updated = searchHistoryMapper.update(null, new LambdaUpdateWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, normalized)
                .set(SearchHistory::getUpdateTime, now));
        if (updated == 0) {
            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(normalized);
            history.setCreateTime(now);
            history.setUpdateTime(now);
            searchHistoryMapper.insert(history);
        }
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
                .last("LIMIT " + MAX_RECORD_COUNT));
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
        List<SearchHistory> oldRecords = searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getUpdateTime)
                .orderByDesc(SearchHistory::getId)
                .last("LIMIT 100 OFFSET " + MAX_RECORD_COUNT));
        for (SearchHistory record : oldRecords) {
            searchHistoryMapper.deleteById(record.getId());
        }
    }
}
