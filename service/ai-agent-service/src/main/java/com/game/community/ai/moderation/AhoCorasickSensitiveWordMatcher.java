package com.game.community.ai.moderation;

import com.game.community.ai.config.ModerationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 基于 AC 自动机的一次扫描敏感词匹配器。 */
@Component
public class AhoCorasickSensitiveWordMatcher {

    private final Node root = new Node();

    /** 启动时加载 UTF-8 词库并构建 failure 指针。 */
    public AhoCorasickSensitiveWordMatcher(ModerationProperties properties,
                                           ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource(properties.getSensitiveWordsPath());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .filter(line -> !line.startsWith("#"))
                    .map(AhoCorasickSensitiveWordMatcher::normalize)
                    .filter(StringUtils::hasText)
                    .forEach(this::addWord);
        }
        buildFailureLinks();
    }

    /** 以 O(文本长度 + 命中数) 扫描并返回去重命中词。 */
    public List<String> find(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> matches = new LinkedHashSet<>();
        Node state = root;
        for (char value : normalize(text).toCharArray()) {
            while (state != root && !state.children.containsKey(value)) {
                state = state.failure;
            }
            state = state.children.getOrDefault(value, root);
            matches.addAll(state.outputs);
        }
        return List.copyOf(matches);
    }

    /** 把词加入 Trie。 */
    private void addWord(String word) {
        Node node = root;
        for (char value : word.toCharArray()) {
            node = node.children.computeIfAbsent(value, ignored -> new Node());
        }
        node.outputs.add(word);
    }

    /** 广度优先构建 AC 自动机失败跳转。 */
    private void buildFailureLinks() {
        root.failure = root;
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (Node child : root.children.values()) {
            child.failure = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            for (Map.Entry<Character, Node> edge : current.children.entrySet()) {
                char value = edge.getKey();
                Node child = edge.getValue();
                Node fallback = current.failure;
                while (fallback != root && !fallback.children.containsKey(value)) {
                    fallback = fallback.failure;
                }
                if (fallback.children.containsKey(value) && fallback.children.get(value) != child) {
                    fallback = fallback.children.get(value);
                }
                child.failure = fallback;
                child.outputs.addAll(fallback.outputs);
                queue.add(child);
            }
        }
    }

    /** 统一大小写、全半角并移除常见插入符，降低简单规避。 */
    private static String normalize(String source) {
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static class Node {
        private final Map<Character, Node> children = new TreeMap<>();
        private final List<String> outputs = new ArrayList<>();
        private Node failure;
    }
}
