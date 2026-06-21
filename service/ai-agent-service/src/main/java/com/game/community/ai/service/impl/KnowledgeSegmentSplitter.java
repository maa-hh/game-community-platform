package com.game.community.ai.service.impl;

import com.game.community.ai.config.AiAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeSegmentSplitter {

    private final AiAgentProperties properties;

    public List<String> split(String content) {
        List<String> paragraphs = normalizeParagraphs(content);
        List<String> segments = new ArrayList<>();
        int targetLength = properties.getSegmentTargetLength();
        int overlap = properties.getSegmentOverlap();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() == 0) {
                current.append(paragraph);
                continue;
            }
            if (current.length() + paragraph.length() + 1 <= targetLength) {
                current.append('\n').append(paragraph);
                continue;
            }
            segments.add(current.toString());
            String carry = current.length() <= overlap ? current.toString() : current.substring(current.length() - overlap);
            current = new StringBuilder(carry).append('\n').append(paragraph);
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    private List<String> normalizeParagraphs(String content) {
        String[] raw = content.replace("\r", "").split("\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String item : raw) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty() && content != null && !content.isBlank()) {
            paragraphs.add(content.trim());
        }
        return paragraphs;
    }
}
