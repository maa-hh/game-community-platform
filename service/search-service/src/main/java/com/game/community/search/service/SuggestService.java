package com.game.community.search.service;

import com.game.community.model.dto.search.SearchCorrectVO;
import com.game.community.model.dto.search.SearchResult;
import com.game.community.model.dto.search.SuggestionPageDTO;
import com.game.community.model.elasticsearch.SuggestDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SuggestService {

    List<SuggestDocument> suggest(String prefix);

    SearchCorrectVO correct(String keyword);

    SearchResult getSuggestions(SuggestionPageDTO pageDTO);

    void batchAddSuggestions(List<SuggestDocument> documents);

    void deleteSuggestion(Long id);

    void loadSuggestionsFromXls(MultipartFile file);
}
