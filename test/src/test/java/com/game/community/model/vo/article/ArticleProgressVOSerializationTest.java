package com.game.community.model.vo.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleProgressVOSerializationTest {

    /** 验证作者侧进度接口只序列化文章 publicId，不暴露内部数据库主键。 */
    @Test
    void serializesPublicArticleIdAsString() throws Exception {
        ArticleProgressVO vo = new ArticleProgressVO();
        vo.setArticleId("article-public-id");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode articleId = objectMapper
                .readTree(objectMapper.writeValueAsString(vo))
                .get("articleId");

        assertTrue(articleId.isTextual());
        assertEquals("article-public-id", articleId.asText());
    }
}
