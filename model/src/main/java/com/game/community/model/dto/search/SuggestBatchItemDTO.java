package com.game.community.model.dto.search;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serializable;

/** 管理端导入建议词的请求模型，不暴露 ES 文档字段。 */
@Data
public class SuggestBatchItemDTO implements Serializable {

    private String term;

    /** 兼容旧管理端仍使用 suggest 字段提交的请求。 */
    @JsonAlias("suggest")
    public void setTerm(String term) {
        this.term = term;
    }
}
