package com.game.community.search.service;

import com.game.community.model.dto.search.SearchCorrectVO;
import com.game.community.model.dto.search.SearchResult;
import com.game.community.model.dto.search.SuggestBatchItemDTO;
import com.game.community.model.dto.search.SuggestionPageDTO;
import com.game.community.model.vo.search.SuggestItemVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SuggestService {

    /** 根据前缀查询公开建议词，可按来源类型过滤游戏候选。 */
    List<SuggestItemVO> suggest(String prefix, String sourceType);

    SearchCorrectVO correct(String keyword);

    SearchResult getSuggestions(SuggestionPageDTO pageDTO);

    void batchAddSuggestions(List<SuggestBatchItemDTO> items);

    void deleteSuggestion(Long id);

    void loadSuggestionsFromXls(MultipartFile file);
}
