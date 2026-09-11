package com.game.community.ai.service.impl;

import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.config.ModerationProperties;
import com.game.community.ai.moderation.AhoCorasickSensitiveWordMatcher;
import com.game.community.ai.moderation.AiModerationScore;
import com.game.community.ai.service.AiModerationService;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.dto.aiagent.ModerationImageRequest;
import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.Semaphore;

/** 文本 AC 预审 + 文本/图片模型评分的统一审核流程。 */
@Slf4j
@Service
public class AiModerationServiceImpl implements AiModerationService {

    private final AgentModelRegistry modelRegistry;
    private final ModerationProperties properties;
    private final AhoCorasickSensitiveWordMatcher sensitiveWordMatcher;
    private final PromptTemplate systemPrompt;
    private final PromptTemplate textPrompt;
    private final PromptTemplate imagePrompt;
    private final PromptTemplate articlePrompt;
    private final Semaphore modelSemaphore;
    private final BeanOutputConverter<AiModerationScore> outputConverter =
            new BeanOutputConverter<>(AiModerationScore.class);

    /** 加载工程化提示词；提示词调整无需修改业务流程。 */
    public AiModerationServiceImpl(AgentModelRegistry modelRegistry,
                                   ModerationProperties properties,
                                   AhoCorasickSensitiveWordMatcher sensitiveWordMatcher,
                                   ResourceLoader resourceLoader) {
        this.modelRegistry = modelRegistry;
        this.properties = properties;
        this.sensitiveWordMatcher = sensitiveWordMatcher;
        this.systemPrompt = new PromptTemplate(resourceLoader.getResource(properties.getSystemPromptPath()));
        this.textPrompt = new PromptTemplate(resourceLoader.getResource(properties.getTextPromptPath()));
        this.imagePrompt = new PromptTemplate(resourceLoader.getResource(properties.getImagePromptPath()));
        this.articlePrompt = new PromptTemplate(resourceLoader.getResource(properties.getArticlePromptPath()));
        this.modelSemaphore = new Semaphore(Math.max(1, properties.getMaxConcurrentModelCalls()));
    }

    /** 执行统一审核，文本必须先经过 AC 自动机。 */
    @Override
    public ModerationResultVO moderate(ModerationRequest request) {
        if (request == null || request.getType() == null) {
            return invalid(null, "审核类型不能为空");
        }
        return switch (request.getType()) {
            case TEXT -> moderateText(request);
            case IMAGE -> moderateImage(request);
            case ARTICLE -> moderateArticle(request);
        };
    }

    /** 文本先本地快速拒绝，未命中时才消耗模型调用。 */
    private ModerationResultVO moderateText(ModerationRequest request) {
        ModerationResultVO result = base(ModerationContentType.TEXT);
        List<String> matches = sensitiveWordMatcher.find(request.getContent());
        result.setMatchedKeywords(matches);
        if (!matches.isEmpty()) {
            result.setKeywordAudit(ModerationCheckResult.REJECT);
            result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
            result.setScore(0);
            result.setReason("命中屏蔽词: " + String.join("、", matches));
            result.setResult(ModerationDecision.REJECT);
            return result;
        }
        result.setKeywordAudit(ModerationCheckResult.PASS);
        if (!StringUtils.hasText(request.getContent())) {
            result.setAiAudit(ModerationCheckResult.PASS);
            result.setScore(10);
            result.setReason("空文本无需审核");
            result.setResult(ModerationDecision.PASS);
            return result;
        }
        if (request.getContent().length() > properties.getMaxTextChars()) {
            return unavailable(result, new IllegalArgumentException("text token length exceeds configured limit"));
        }

        try {
            AgentModelRegistry.ModelClient model = modelRegistry.textModeration(request.getProvider());
            String userPrompt = textPrompt.render(Map.of("content", request.getContent()));
            return callModel(result, model, spec -> spec.user(userPrompt));
        } catch (Exception e) {
            return unavailable(result, e);
        }
    }

    /** 文章正文与全部图片一次提交，避免调用方分拆审核造成竞态和重复计费。 */
    private ModerationResultVO moderateArticle(ModerationRequest request) {
        ModerationResultVO result = base(ModerationContentType.ARTICLE);
        String title = request.getTitle() == null ? "" : request.getTitle();
        String content = request.getContent() == null ? "" : request.getContent();
        String fullText = (title + "\n" + content).trim();
        List<String> matches = sensitiveWordMatcher.find(fullText);
        result.setMatchedKeywords(matches);
        if (!matches.isEmpty()) {
            result.setKeywordAudit(ModerationCheckResult.REJECT);
            result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
            result.setScore(0);
            result.setReason("命中屏蔽词: " + String.join("、", matches));
            result.setResult(ModerationDecision.REJECT);
            return result;
        }
        result.setKeywordAudit(ModerationCheckResult.PASS);
        if (fullText.length() > properties.getMaxTextChars()) {
            return unavailable(result, new IllegalArgumentException("article token length exceeds configured limit"));
        }
        List<ModerationImageRequest> images = request.getImages() == null
                ? Collections.emptyList() : request.getImages();
        try {
            AgentModelRegistry.ModelClient model = images.stream().anyMatch(this::hasImage)
                    ? modelRegistry.imageModeration(request.getProvider())
                    : modelRegistry.textModeration(request.getProvider());
            String userPrompt = articlePrompt.render(Map.of("title", title, "content", content));
            return callModel(result, model, spec -> spec.user(user -> {
                user.text(userPrompt);
                for (ModerationImageRequest image : images) {
                    addMedia(user, image);
                }
            }));
        } catch (Exception e) {
            return unavailable(result, e);
        }
    }

    private boolean hasImage(ModerationImageRequest image) {
        return image != null && (StringUtils.hasText(image.getImageUrl()) || StringUtils.hasText(image.getImageBase64()));
    }

    /** 将单张图片加入同一条多模态消息。 */
    private void addMedia(ChatClient.PromptUserSpec user, ModerationImageRequest image) {
        if (!hasImage(image)) {
            return;
        }
        MimeType mimeType = resolveMimeType(image.getMimeType());
        if (StringUtils.hasText(image.getImageBase64())) {
            byte[] bytes = Base64.getDecoder().decode(stripDataUrlPrefix(image.getImageBase64()));
            user.media(mimeType, new NamedByteArrayResource(bytes));
            return;
        }
        try {
            user.media(mimeType, URI.create(image.getImageUrl()).toURL());
        } catch (Exception e) {
            throw new IllegalArgumentException("图片 URL 无效", e);
        }
    }

    /** 图片使用同一评分协议，支持 URL 或 Base64 字节。 */
    private ModerationResultVO moderateImage(ModerationRequest request) {
        ModerationResultVO result = base(ModerationContentType.IMAGE);
        result.setKeywordAudit(ModerationCheckResult.NOT_APPLICABLE);
        result.setMatchedKeywords(List.of());
        if (!StringUtils.hasText(request.getImageUrl()) && !StringUtils.hasText(request.getImageBase64())) {
            return invalid(ModerationContentType.IMAGE, "图片 URL 和图片内容不能同时为空");
        }

        try {
            AgentModelRegistry.ModelClient model = modelRegistry.imageModeration(request.getProvider());
            String userPrompt = imagePrompt.render();
            MimeType mimeType = resolveMimeType(request.getMimeType());
            if (StringUtils.hasText(request.getImageBase64())) {
                byte[] bytes = Base64.getDecoder().decode(stripDataUrlPrefix(request.getImageBase64()));
                return callModel(result, model, spec -> spec.user(user -> user.text(userPrompt)
                        .media(mimeType, new NamedByteArrayResource(bytes))));
            }
            URL imageUrl = URI.create(request.getImageUrl()).toURL();
            return callModel(result, model, spec -> spec.user(user -> user.text(userPrompt)
                    .media(mimeType, imageUrl)));
        } catch (Exception e) {
            return unavailable(result, e);
        }
    }

    /** 调用 Spring AI 并把结构化评分映射为统一决策。 */
    private ModerationResultVO callModel(ModerationResultVO result,
                                         AgentModelRegistry.ModelClient model,
                                         java.util.function.UnaryOperator<ChatClient.ChatClientRequestSpec> userConfigurer) {
        long start = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = modelSemaphore.tryAcquire();
            if (!acquired) {
                throw new IllegalStateException("moderation model concurrency limit reached");
            }
            ChatClient.ChatClientRequestSpec request = model.client().prompt()
                    .system(systemPrompt.render() + "\n\n" + outputConverter.getFormat());
            ChatResponse response = userConfigurer.apply(request).call().chatResponse();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IllegalStateException("模型返回为空");
            }
            AiModerationScore score = outputConverter.convert(extractJson(response.getResult().getOutput().getText()));
            if (score == null || score.getScore() == null) {
                throw new IllegalStateException("模型结果缺少 score");
            }
            int normalizedScore = Math.max(0, Math.min(10, score.getScore()));
            ModerationDecision decision = decision(normalizedScore);
            result.setAiAudit(checkResult(decision));
            result.setScore(normalizedScore);
            result.setReason(StringUtils.hasText(score.getReason()) ? score.getReason() : defaultReason(decision));
            result.setResult(decision);
            result.setProvider(model.provider());
            result.setModel(model.model());
            result.setDurationMs(durationMs(start));
            copyUsage(result, response);
            log.info("AI审核完成: type={}, provider={}, model={}, score={}, result={}, durationMs={}",
                    result.getType(), result.getProvider(), result.getModel(), result.getScore(),
                    result.getResult(), result.getDurationMs());
            return result;
        } catch (Exception e) {
            result.setDurationMs(durationMs(start));
            return unavailable(result, e);
        } finally {
            if (acquired) {
                modelSemaphore.release();
            }
        }
    }

    /** 模型异常时按配置统一降级，默认转人工而不是误放行。 */
    private ModerationResultVO unavailable(ModerationResultVO result, Exception exception) {
        ModerationDecision fallback;
        try {
            fallback = ModerationDecision.valueOf(properties.getUnavailableDecision().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            fallback = ModerationDecision.HUMAN_REVIEW;
        }
        result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
        result.setResult(fallback);
        result.setScore(switch (fallback) {
            case PASS -> 8;
            case REJECT -> 1;
            case HUMAN_REVIEW -> 5;
        });
        result.setReason(failureReason(exception) + "，已按配置转为" + defaultReason(fallback));
        log.warn("AI审核调用失败: type={}, fallback={}, error={}",
                result.getType(), fallback, exception.getMessage());
        return result;
    }

    /** 将常见链接、超时、token 和返回格式故障转换为不泄漏供应商细节的原因。 */
    private String failureReason(Exception exception) {
        String message = exception == null ? "" : exception.toString().toLowerCase(Locale.ROOT);
        if (message.contains("token") || message.contains("context") || message.contains("too long")
                || message.contains("length exceeds")) {
            return "输入过长或 token 不足，转人工审核";
        }
        if (message.contains("uri") || message.contains("url") || message.contains("图片链接无效")) {
            return "图片链接或内容格式错误，转人工审核";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "模型请求超时，转人工审核";
        }
        if (message.contains("json") || message.contains("格式") || message.contains("返回为空")) {
            return "AI 返回格式异常，转人工审核";
        }
        if (message.contains("concurrency limit")) {
            return "审核并发已达保护上限，转人工审核";
        }
        return "AI审核暂不可用";
    }

    /** 创建各类型共用的响应骨架。 */
    private ModerationResultVO base(ModerationContentType type) {
        ModerationResultVO result = new ModerationResultVO();
        result.setType(type);
        result.setMatchedKeywords(List.of());
        return result;
    }

    /** 构造输入校验失败的直接拒绝结果。 */
    private ModerationResultVO invalid(ModerationContentType type, String reason) {
        ModerationResultVO result = base(type);
        result.setKeywordAudit(type == ModerationContentType.TEXT || type == ModerationContentType.ARTICLE
                ? ModerationCheckResult.PASS : ModerationCheckResult.NOT_APPLICABLE);
        result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
        result.setScore(0);
        result.setReason(reason);
        result.setResult(ModerationDecision.REJECT);
        return result;
    }

    /** 根据可配置阈值生成最终决策。 */
    private ModerationDecision decision(int score) {
        if (score <= properties.getRejectMaxScore()) {
            return ModerationDecision.REJECT;
        }
        if (score <= properties.getHumanReviewMaxScore()) {
            return ModerationDecision.HUMAN_REVIEW;
        }
        return ModerationDecision.PASS;
    }

    /** 把最终决策映射为 AI 阶段结果。 */
    private ModerationCheckResult checkResult(ModerationDecision decision) {
        return switch (decision) {
            case PASS -> ModerationCheckResult.PASS;
            case REJECT -> ModerationCheckResult.REJECT;
            case HUMAN_REVIEW -> ModerationCheckResult.HUMAN_REVIEW;
        };
    }

    /** 复制 Spring AI token 用量。 */
    private void copyUsage(ModerationResultVO result, ChatResponse response) {
        if (response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage != null) {
            result.setPromptTokens(usage.getPromptTokens());
            result.setCompletionTokens(usage.getCompletionTokens());
            result.setTotalTokens(usage.getTotalTokens());
        }
    }

    /** 解析图片 MIME，异常配置回退到 JPEG。 */
    private MimeType resolveMimeType(String mimeType) {
        if (StringUtils.hasText(mimeType)) {
            try {
                return MimeType.valueOf(mimeType);
            } catch (Exception ignored) {
                // 不信任跨服务传入的 MIME，无法解析时使用通用图片类型。
            }
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    /** 兼容纯 Base64 与 data URL 两种传参。 */
    private String stripDataUrlPrefix(String value) {
        int separator = value.indexOf(',');
        return value.startsWith("data:") && separator >= 0 ? value.substring(separator + 1) : value;
    }

    /** 兼容少数模型在 JSON 外包裹 markdown 或思考内容的情况。 */
    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String visible = content.replaceAll("(?s)<think>.*?</think>", "")
                .replace("```json", "")
                .replace("```", "")
                .trim();
        int start = visible.indexOf('{');
        int end = visible.lastIndexOf('}');
        return start >= 0 && end > start ? visible.substring(start, end + 1) : visible;
    }

    /** 计算单次模型调用耗时。 */
    private long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** 生成人工可读的默认结果原因。 */
    private String defaultReason(ModerationDecision decision) {
        return switch (decision) {
            case PASS -> "通过";
            case REJECT -> "不通过";
            case HUMAN_REVIEW -> "人工审核";
        };
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        NamedByteArrayResource(byte[] byteArray) {
            super(byteArray);
        }

        /** Spring AI 需要非空文件名判断媒体类型。 */
        @Override
        public String getFilename() {
            return "moderation-image";
        }
    }
}
