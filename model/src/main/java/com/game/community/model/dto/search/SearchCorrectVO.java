package com.game.community.model.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchCorrectVO implements Serializable {

    private String original;

    private String suggest;

    private String type;

    private Integer distance;

    public static SearchCorrectVO noNeed() {
        return new SearchCorrectVO(null, null, "NONE", null);
    }

    public static SearchCorrectVO of(String original, String suggest, String type, Integer distance) {
        return new SearchCorrectVO(original, suggest, type, distance);
    }
}
