package com.game.community.ai.service.impl;

import com.game.community.ai.config.AiAgentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSegmentSplitterTest {

    @Test
    void shouldSplitLongParagraphsIntoOverlappedSegments() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setSegmentTargetLength(20);
        properties.setSegmentOverlap(5);
        KnowledgeSegmentSplitter splitter = new KnowledgeSegmentSplitter(properties);

        String content = "第一段第一段第一段第一段第一段\n第二段第二段第二段第二段第二段\n第三段第三段第三段第三段第三段";

        List<String> segments = splitter.split(content);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments.get(0)).contains("第一段");
        assertThat(segments.get(segments.size() - 1)).contains("第三段");
    }
}
