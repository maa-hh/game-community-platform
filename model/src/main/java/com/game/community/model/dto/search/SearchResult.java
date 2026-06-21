package com.game.community.model.dto.search;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SearchResult implements Serializable {

    private Long total = 0L;

    private Long page = 1L;

    private Long size = 10L;

    private List<?> list = List.of();

    private String errorMsg;

    public boolean isSuccess() {
        return errorMsg == null || errorMsg.isBlank();
    }
}
