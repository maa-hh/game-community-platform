package com.game.community.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.model.entity.search.SearchHistory;
import com.game.community.search.config.SearchHistoryProperties;
import com.game.community.search.mapper.SearchHistoryMapper;
import com.game.community.search.service.SearchRecordService;
import com.game.community.model.vo.search.SearchHistoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchRecordServiceImpl implements SearchRecordService {

    private final SearchHistoryMapper searchHistoryMapper;
    private final SearchHistoryProperties searchHistoryProperties;

    @Override
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
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void addRecordAsync(Long userId, String keyword) {
        try {
            addRecord(userId, keyword);
        } catch (Exception e) {
            log.warn("异步保存搜索历史失败: userId={}, keyword={}", userId, keyword, e);
        }
    }

    @Override
    public List<SearchHistoryVO> getRecords(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getUpdateTime)
                .orderByDesc(SearchHistory::getId)
                .last("LIMIT " + searchHistoryProperties.normalizedMaxRecords()))
                .stream()
                .map(this::toHistoryVO)
                .toList();
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
        searchHistoryMapper.deleteExcess(userId, searchHistoryProperties.normalizedMaxRecords());
    }

    /** 将搜索历史实体裁剪成对外只读视图。 */
    private SearchHistoryVO toHistoryVO(SearchHistory history) {
        SearchHistoryVO vo = new SearchHistoryVO();
        vo.setId(history.getId());
        vo.setKeyword(history.getKeyword());
        vo.setUpdateTime(history.getUpdateTime());
        return vo;
    }
}
