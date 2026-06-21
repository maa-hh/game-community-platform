package com.game.community.search.service;

import com.game.community.model.dto.search.SearchPageDTO;
import com.game.community.model.dto.search.SearchResult;

public interface ArticleSearchService {

    SearchResult search(SearchPageDTO searchDTO);
}
