package com.aitools.mdassistant.ai;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型 HTTP 客户端，同时支持：
 *  - OpenAI 兼容协议：POST {base}/v1/chat/completions（OpenAI / DeepSeek / GLM / OneAPI 等）
 *  - Anthropic 协议：POST {base}/v1/messages（Claude）
 * 支持流式（SSE）与非流式两种调用；方法均为同步阻塞，必须在后台线程调用。
 */
public final class AiClient {

    private static final java.util.regex.Pattern VERSION_SUFFIX =
            java.util.regex.Pattern.compile("(?i)/v\\d+[a-z]*$");

    public static final class Msg {
        public final String role;
        public final String content;

        public Msg(@NotNull String role, @NotNull String content) {
            this.role = role;
            this.content = content;
        }
    }

    public interface Callback {
        void onDelta(String text);

        void onSuccess(String fullText);

        void onError(String message);
    }

    private final AiConfig config;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    public AiClient(@NotNull AiConfig config) {
        this.config = config;
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = connection;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Throwable ignore) {
            }
        }
    }

    /** 流式对话。阻塞当前线程，结果通过回调返回（回调在调用线程上触发）。 */
    public void chat(@Nullable String system, @NotNull List<Msg> messages, @NotNull Callback cb) {
        perform(system, messages, true, cb);
    }

    /** 非流式对话，直接返回完整文本。 */
    @NotNull
    public String chatSync(@Nullable String system, @NotNull List<Msg> messages) {
        return perform(system, messages, false, null);
    }

    /** 连接测试：发送一个极小请求验证配置（30s 超时——部分模型如 MiniMax 首响应需 7~20s）。 */
    @NotNull
    public static String test(@NotNull AiConfig cfg) {
        AiConfig small = new AiConfig(cfg.provider, cfg.baseUrl, cfg.model, cfg.apiKey,
                Math.min(cfg.maxTokens <= 0 ? 16 : cfg.maxTokens, 16), 30);
        AiClient client = new AiClient(small);
        String reply = client.chatSync("You are a connectivity probe. Reply with exactly: OK",
                java.util.Collections.singletonList(new Msg("user", "ping")));
        return "连接成功，模型已响应：" + (reply.isEmpty() ? "OK" : reply.trim());
    }

    /** 测试/诊断用：返回实际请求的完整 URL 与协议类型、代理状态。 */
    @NotNull
    public static String describeTarget(@NotNull AiConfig cfg) {
        String url = cfg.isAnthropic() ? anthropicUrl(cfg.effectiveBaseUrl())
                : openAiUrl(cfg.effectiveBaseUrl());
        return "POST " + url + " [" + (cfg.isAnthropic() ? "anthropic" : "openai") + ", "
                + ProxyResolver.describe(url) + "]";
    }

    /** 生成与插件请求等价的 cURL 命令（用于终端运行，快速分辨插件问题与网络问题）。 */
    @NotNull
    public static String toCurl(@NotNull AiConfig cfg) {
        String url = cfg.isAnthropic() ? anthropicUrl(cfg.effectiveBaseUrl())
                : openAiUrl(cfg.effectiveBaseUrl());
        StringBuilder sb = new StringBuilder();
        sb.append("# EasyMDAssistant 探测命令（Git Bash / PowerShell 运行；超时可加 --proxy http://127.0.0.1:端口）\n");
        sb.append("curl -sS -X POST \"").append(url).append('"')
                .append(" -H \"Content-Type: application/json\"");
        if (cfg.isAnthropic()) {
            sb.append(" -H \"x-api-key: ").append(cfg.apiKey).append('"')
                    .append(" -H \"anthropic-version: 2023-06-01\"");
        } else {
            sb.append(" -H \"Authorization: Bearer ").append(cfg.apiKey).append('"');
        }
        sb.append(" -d \"{\\\"model\\\":\\\"").append(cfg.model)
                .append("\\\",\\\"max_tokens\\\":16,\\\"messages\\\":[{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"ping\\\"}]}\"");
        return sb.toString();
    }

    @NotNull
    private String perform(@Nullable String system, @NotNull List<Msg> messages,
                           boolean stream, @Nullable Callback cb) {
        if (!config.isComplete()) {
            String err = "AI 未配置完整（需要 API Key 和模型 ID）";
            if (cb != null) {
                cb.onError(err);
            }
            throw new AiException(err);
        }
        try {
            String url = config.isAnthropic() ? anthropicUrl(config.effectiveBaseUrl())
                    : openAiUrl(config.effectiveBaseUrl());
            String body = buildBody(system, messages, stream);
            connection = open(url, body, config);
            HttpURLConnection conn = connection;
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String errText = readAll(conn.getErrorStream());
                throw new AiException("HTTP " + code + " " + shorten(errText, 600));
            }
            StringBuilder full = new StringBuilder();
            if (stream) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled) {
                            break;
                        }
                        line = line.trim();
                        if (line.isEmpty() || !line.startsWith("data:")) {
                            continue;
                        }
                        String payload = line.substring(5).trim(); // "data:" 占 5 个字符，substring(4) 会残留冒号导致 JSON 解析全部静默失败
                        if ("[DONE]".equals(payload)) {
                            break;
                        }
                        Object chunk;
                        try {
                            chunk = Json.parse(payload);
                        } catch (RuntimeException ignore) {
                            continue; // 跳过无法解析的心跳/事件行
                        }
                        if (config.isAnthropic()) {
                            Map<String, Object> m = Json.asMap(chunk);
                            String type = Json.getStr(m, "type");
                            if ("error".equals(type)) {
                                Map<String, Object> errMap = m == null ? null : Json.asMap(m.get("error"));
                                throw new AiException("API 错误：" + (errMap == null ? payload : Json.getStr(errMap, "message")));
                            }
                            if ("message_stop".equals(type)) {
                                break;
                            }
                        }
                        String delta = extractDelta(chunk, config.isAnthropic());
                        if (delta != null && !delta.isEmpty()) {
                            full.append(delta);
                            if (cb != null) {
                                cb.onDelta(delta);
                            }
                        }
                    }
                }
            } else {
                String respText = readAll(conn.getInputStream());
                Object resp = Json.parse(respText);
                String text = extractDelta(resp, config.isAnthropic());
                if (text != null) {
                    full.append(text);
                }
            }
            if (!cancelled) {
                if (cb != null) {
                    cb.onSuccess(full.toString());
                }
                return full.toString();
            }
            return full.toString();
        } catch (AiException e) {
            if (!cancelled) {
                if (cb != null) {
                    cb.onError(e.getMessage());
                } else {
                    throw e;
                }
            }
            return "";
        } catch (Exception e) {
            if (!cancelled) {
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (cb != null) {
                    cb.onError(msg);
                } else {
                    throw new AiException(msg, e);
                }
            }
            return "";
        } finally {
            connection = null;
        }
    }

    private HttpURLConnection open(String url, String body, AiConfig cfg) throws IOException {
        java.net.Proxy proxy = ProxyResolver.resolve(url);
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) (proxy == null ? u.openConnection() : u.openConnection(proxy));
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(cfg.timeoutSeconds * 1000);
        conn.setReadTimeout(cfg.timeoutSeconds * 1000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", cfg.isAnthropic() ? "text/event-stream" : "application/json");
        conn.setRequestProperty("User-Agent", "MDAssistant-IDEA/1.0");
        if (cfg.isAnthropic()) {
            conn.setRequestProperty("x-api-key", cfg.apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else {
            conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
        }
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }
        return conn;
    }

    private String buildBody(String system, List<Msg> messages, boolean stream) {
        List<Object> msgs = new ArrayList<>();
        if (!config.isAnthropic() && system != null && !system.isEmpty()) {
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", system);
            msgs.add(sys);
        }
        for (Msg m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.role);
            mm.put("content", m.content);
            msgs.add(mm);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model);
        body.put("stream", stream);
        if (config.isAnthropic()) {
            body.put("max_tokens", Math.max(config.maxTokens, 16));
            if (system != null && !system.isEmpty()) {
                body.put("system", system);
            }
            body.put("messages", msgs);
        } else {
            // o1 / gpt-5 系列不接受 max_tokens 参数
            String model = config.model.toLowerCase();
            if (!model.startsWith("o1") && !model.startsWith("gpt-5") && !model.startsWith("o3")) {
                body.put("max_tokens", Math.max(config.maxTokens, 16));
            }
            body.put("messages", msgs);
        }
        return Json.write(body);
    }

    /** 从流式 chunk 或完整响应中提取文本增量。 */
    @Nullable
    private static String extractDelta(Object chunk, boolean anthropic) {
        if (chunk == null) {
            return null;
        }
        if (!anthropic) {
            Map<String, Object> root = Json.asMap(chunk);
            List<Object> choices = root == null ? null : Json.asList(root.get("choices"));
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> c0 = Json.asMap(choices.get(0));
            if (c0 == null) {
                return null;
            }
            Map<String, Object> delta = Json.asMap(c0.get("delta"));
            if (delta != null) {
                return Json.str(delta.get("content"));
            }
            Map<String, Object> message = Json.asMap(c0.get("message"));
            if (message != null) {
                return Json.str(message.get("content"));
            }
            return null;
        }
        // Anthropic
        Map<String, Object> root = Json.asMap(chunk);
        if (root == null) {
            return null;
        }
        String type = Json.getStr(root, "type");
        if ("content_block_delta".equals(type)) {
            Map<String, Object> d = Json.asMap(root.get("delta"));
            return d == null ? null : Json.str(d.get("text"));
        }
        // 非流式响应：content 为 [{type:text,text:...}] 数组
        List<Object> content = Json.asList(root.get("content"));
        if (content != null) {
            StringBuilder sb = new StringBuilder();
            for (Object o : content) {
                Map<String, Object> block = Json.asMap(o);
                String t = block == null ? null : Json.str(block.get("text"));
                if (t != null) {
                    sb.append(t);
                }
            }
            return sb.toString();
        }
        return null;
    }

    static String openAiUrl(String base) {
        String b = stripSlash(base);
        if (b.endsWith("/chat/completions")) {
            return b;
        }
        // 以版本号结尾（/v1、/v2、/v4…）只补接口路径，避免拼出 /v4/v1/...
        if (VERSION_SUFFIX.matcher(b).find()) {
            return b + "/chat/completions";
        }
        return b + "/v1/chat/completions";
    }

    static String anthropicUrl(String base) {
        String b = stripSlash(base);
        if (b.endsWith("/messages")) {
            return b;
        }
        if (VERSION_SUFFIX.matcher(b).find()) {
            return b + "/messages";
        }
        return b + "/v1/messages";
    }

    private static String stripSlash(String s) {
        String b = s.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }
}
