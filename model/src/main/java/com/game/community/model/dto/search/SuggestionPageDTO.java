package com.game.community.model.dto.search;

import com.game.community.model.base.PageDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
public class SuggestionPageDTO extends PageDto implements Serializable {

    private String keyword;
}
