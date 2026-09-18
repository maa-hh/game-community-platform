package com.game.community.search.ai;

import com.game.community.model.elasticsearch.ArticleDocument;
import com.game.community.model.vo.game.GameTagVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleEmbeddingTextBuilderTest {

    @Test
    void shouldKeepHighSignalFieldsAndNormalizeHtmlContent() {
        ArticleDocument document = new ArticleDocument();
        document.setTitle("艾尔登法环新手攻略");
        document.setSummary("讨论前期职业选择");
        document.setCategoryNames(List.of("攻略心得"));
        GameTagVO tag = new GameTagVO();
        tag.setName("Elden Ring");
        document.setGameTags(List.of(tag));
        document.setContent("<p>先升级生命值</p>\n\n选择一把适合自己的武器。");

        String text = ArticleEmbeddingTextBuilder.build(document);

        assertTrue(text.contains("艾尔登法环新手攻略"));
        assertTrue(text.contains("攻略心得"));
        assertTrue(text.contains("Elden Ring"));
        assertTrue(text.contains("先升级生命值"));
        assertFalse(text.contains("<p>"));
    }
}
