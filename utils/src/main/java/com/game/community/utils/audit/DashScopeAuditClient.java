package com.game.community.utils.audit;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

/**
 * 基于 Spring AI Alibaba DashScope ChatClient 的审核客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DashScopeAuditProperties.class)
public class DashScopeAuditClient implements AuditClient {

    private static final String AUDIT_SYSTEM_PROMPT = """
            你是游戏社区内容安全审核器。你需要审核文本和图片，判断是否包含违法违规、辱骂、色情、政治敏感、广告引流、人身攻击、未成年人不适宜内容。
            只能返回 JSON，不要输出 markdown、解释、代码块或其他文本。
            通过时返回 {"pass":true,"reason":"通过"}
            不通过时返回 {"pass":false,"reason":"具体原因"}
            """;

    private static final String IMAGE_AUDIT_PROMPT = "请审核这张用户上传图片是否适合作为游戏社区头像或文章配图，只返回 JSON。";

    private final DashScopeChatModel chatModel;

    private final DashScopeAuditProperties properties;

    private final RestClient restClient = RestClient.builder().build();

    @Override
    public AuditResult auditText(String text) {
        if (!StringUtils.hasText(text)) {
            return AuditResult.pass();
        }
        long start = System.nanoTime();
        try {
            ChatResponse response = client().prompt()
                    .options(textOptions())
                    .system(AUDIT_SYSTEM_PROMPT)
                    .user("请审核这段用户资料文本：" + text)
                    .call()
                    .chatResponse();
            AuditResult result = parseAuditResult(response, durationMs(start));
            logAuditMetrics("文字审核", result);
            return result;
        } catch (Exception e) {
            AuditResult result = unavailableFallback("文字审核", "内容审核服务暂不可用", start, e);
            logAuditMetrics("文字审核", result);
            return result;
        }
    }

    @Override
    public AuditResult auditImage(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return AuditResult.pass();
        }
        long start = System.nanoTime();
        try {
            AuditResult result = callCompatibleImageAudit(buildDataUrl(imageBytes, resolveMimeType(mimeType)), durationMs(start));
            logAuditMetrics("图片审核", result);
            return result;
        } catch (Exception e) {
            AuditResult result = unavailableFallback("图片审核", "图片审核服务暂不可用", start, e);
            logAuditMetrics("图片审核", result);
            return result;
        }
    }

    @Override
    public AuditResult auditImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return AuditResult.pass();
        }
        long start = System.nanoTime();
        try {
            AuditResult result = callCompatibleImageAudit(imageUrl, durationMs(start));
            logAuditMetrics("图片URL审核", result);
            return result;
        } catch (Exception e) {
            AuditResult result = unavailableFallback("图片URL审核", "图片审核服务暂不可用", start, e);
            logAuditMetrics("图片URL审核", result);
            return result;
        }
    }

    private ChatClient client() {
        return ChatClient.create(chatModel);
    }

    private DashScopeChatOptions textOptions() {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder()
                .temperature(properties.getTemperature());
        if (StringUtils.hasText(properties.getTextModel())) {
            builder.model(properties.getTextModel());
        }
        return builder.build();
    }

    private AuditResult parseAuditResult(ChatResponse response, long durationMs) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return AuditResult.reject("审核结果为空", durationMs, null, null, null);
        }
        String content = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(content)) {
            return withUsage(AuditResult.reject("审核结果为空"), response, durationMs);
        }
        String json = extractJson(content);
        JSONObject result = JSONObject.parseObject(json);
        boolean pass = Boolean.TRUE.equals(result.getBoolean("pass"));
        String reason = result.getString("reason");
        AuditResult auditResult = pass ? AuditResult.pass() : AuditResult.reject(StringUtils.hasText(reason) ? reason : "审核未通过");
        return withUsage(auditResult, response, durationMs);
    }

    private AuditResult withUsage(AuditResult result, ChatResponse response, long durationMs) {
        result.setDurationMs(durationMs);
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                result.setPromptTokens(usage.getPromptTokens());
                result.setCompletionTokens(usage.getCompletionTokens());
                result.setTotalTokens(usage.getTotalTokens());
            }
        }
        return result;
    }

    private AuditResult unavailableFallback(String scene, String rejectReason, long startNanos, Exception e) {
        long durationMs = durationMs(startNanos);
        if (properties.isFailOpenOnUnavailable()) {
            log.warn("DashScope{}调用失败，按本地规则放行: {}", scene, e.getMessage());
            return AuditResult.pass(durationMs, null, null, null);
        }
        log.warn("DashScope{}调用失败，按失败处理: {}", scene, e.getMessage());
        return AuditResult.reject(rejectReason, durationMs, null, null, null);
    }

    private long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void logAuditMetrics(String scene, AuditResult result) {
        log.info("{}完成: pass={}, reason={}, durationMs={}, promptTokens={}, completionTokens={}, totalTokens={}",
                scene,
                result.isPass(),
                result.getReason(),
                result.getDurationMs(),
                result.getPromptTokens(),
                result.getCompletionTokens(),
                result.getTotalTokens());
    }

    private MimeType resolveMimeType(String mimeType) {
        if (StringUtils.hasText(mimeType)) {
            try {
                return MimeType.valueOf(mimeType);
            } catch (Exception ignored) {
                // 回退到 jpeg。
            }
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    private MimeType guessImageMimeType(String imageUrl) throws MalformedURLException {
        String normalized = imageUrl.toLowerCase();
        if (normalized.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (normalized.endsWith(".gif")) {
            return MimeType.valueOf("image/gif");
        }
        if (normalized.endsWith(".webp")) {
            return MimeType.valueOf("image/webp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    private AuditResult callCompatibleImageAudit(String imageUrl, long durationMs) {
        JSONObject body = new JSONObject();
        body.put("model", resolveImageModel());
        body.put("response_format", JSONObject.of("type", "json_object"));
        body.put("messages", buildImageMessages(imageUrl));

        JSONObject response = restClient.post()
                .uri(properties.getImageCompatibleEndpoint())
                .header("Authorization", "Bearer " + resolveApiKey())
                .header("Content-Type", "application/json")
                .body(body.toJSONString())
                .retrieve()
                .body(JSONObject.class);

        return parseCompatibleImageResult(response, durationMs);
    }

    private JSONArray buildImageMessages(String imageUrl) {
        JSONArray messages = new JSONArray();
        messages.add(JSONObject.of(
                "role", "system",
                "content", AUDIT_SYSTEM_PROMPT
        ));

        JSONArray userContent = new JSONArray();
        userContent.add(JSONObject.of(
                "type", "image_url",
                "image_url", JSONObject.of("url", imageUrl)
        ));
        userContent.add(JSONObject.of(
                "type", "text",
                "text", IMAGE_AUDIT_PROMPT
        ));
        messages.add(JSONObject.of(
                "role", "user",
                "content", userContent
        ));
        return messages;
    }

    private AuditResult parseCompatibleImageResult(JSONObject response, long durationMs) {
        if (response == null) {
            return AuditResult.reject("审核结果为空", durationMs, null, null, null);
        }
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return AuditResult.reject("审核结果为空", durationMs, null, null, null);
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        if (!StringUtils.hasText(content)) {
            return AuditResult.reject("审核结果为空", durationMs, null, null, null);
        }
        String json = extractJson(content);
        JSONObject result = JSONObject.parseObject(json);
        boolean pass = Boolean.TRUE.equals(result.getBoolean("pass"));
        String reason = result.getString("reason");
        AuditResult auditResult = pass ? AuditResult.pass() : AuditResult.reject(StringUtils.hasText(reason) ? reason : "审核未通过");
        auditResult.setDurationMs(durationMs);

        JSONObject usage = response.getJSONObject("usage");
        if (usage != null) {
            auditResult.setPromptTokens(usage.getInteger("prompt_tokens"));
            auditResult.setCompletionTokens(usage.getInteger("completion_tokens"));
            auditResult.setTotalTokens(usage.getInteger("total_tokens"));
        }
        return auditResult;
    }

    private String buildDataUrl(byte[] imageBytes, MimeType mimeType) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    private String resolveImageModel() {
        if (StringUtils.hasText(properties.getImageModel())) {
            return properties.getImageModel();
        }
        return StringUtils.hasText(properties.getTextModel()) ? properties.getTextModel() : "qwen-vl-plus";
    }

    private String resolveApiKey() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        throw new IllegalStateException("DASHSCOPE_API_KEY 未配置");
    }

    private String extractJson(String content) {
        String visibleContent = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        visibleContent = visibleContent.replace("```json", "").replace("```", "").trim();
        int start = visibleContent.indexOf('{');
        int end = visibleContent.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return visibleContent.substring(start, end + 1);
        }
        return visibleContent;
    }
}
