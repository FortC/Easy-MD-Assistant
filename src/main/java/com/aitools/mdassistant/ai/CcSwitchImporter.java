package com.aitools.mdassistant.ai;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 cc-switch 及其实际生效的 CLI 配置中发现可导入的 AI 服务商：
 * <ul>
 *   <li>cc-switch 旧版（&lt;3.7）：{@code ~/.cc-switch/config.json}（递归扫描 JSON 树，兼容各版本 schema）</li>
 *   <li>cc-switch 新版（&ge;3.7，SQLite 存储）：切换时仍会把当前服务商写入
 *       {@code ~/.claude/settings.json}（env: ANTHROPIC_BASE_URL / ANTHROPIC_AUTH_TOKEN / ANTHROPIC_MODEL）
 *       与 {@code ~/.codex/config.toml}（model_providers）+ {@code ~/.codex/auth.json}，从这里读取当前生效配置</li>
 * </ul>
 * 也可让用户手动选择任意 config.json（parseFile）。
 */
public final class CcSwitchImporter {

    /** 一个可导入的服务商配置。 */
    public static final class Candidate {
        public final String source;              // 来源标签（cc-switch / Claude Code / Codex）
        public final String name;                // 展示名
        public final String provider;            // AiConfig.PROVIDER_OPENAI / PROVIDER_ANTHROPIC
        public final String baseUrl;             // 可为空（用服务商默认地址）
        public final String model;               // 可为空
        public final String apiKey;              // 可为空

        public Candidate(String source, String name, String provider,
                         String baseUrl, String model, String apiKey) {
            this.source = source == null ? "" : source;
            this.name = name == null || name.trim().isEmpty() ? this.source : name.trim();
            this.provider = provider;
            this.baseUrl = trimToEmpty(baseUrl);
            this.model = trimToEmpty(model);
            this.apiKey = trimToEmpty(apiKey);
        }

        public boolean isComplete() {
            return !model.isEmpty() && !apiKey.isEmpty();
        }

        /** 掩码显示 API Key。 */
        public String maskedKey() {
            if (apiKey.isEmpty()) {
                return "";
            }
            if (apiKey.length() <= 12) {
                return "****";
            }
            return apiKey.substring(0, 6) + "…" + apiKey.substring(apiKey.length() - 4);
        }
    }

    private static final String[] KEY_FIELDS =
            {"apiKey", "api_key", "key", "token", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_API_KEY", "OPENAI_API_KEY"};
    private static final String[] URL_FIELDS =
            {"baseUrl", "base_url", "url", "endpoint", "apiUrl", "ANTHROPIC_BASE_URL"};
    private static final String[] NAME_FIELDS = {"name", "id", "label", "title"};
    private static final String[] MODEL_FIELDS = {"model", "defaultModel", "ANTHROPIC_MODEL", "models"};

    private CcSwitchImporter() {
    }

    /** 自动发现所有可导入配置（cc-switch config.json + Claude Code / Codex 当前生效配置）。 */
    @NotNull
    public static List<Candidate> discover() {
        List<Candidate> out = new ArrayList<>();
        File home = new File(System.getProperty("user.home", "."));
        File ccConfig = new File(home, ".cc-switch" + File.separator + "config.json");
        if (ccConfig.isFile()) {
            try {
                parseFile(ccConfig, out);
            } catch (Exception ignore) {
                // 单个来源失败不影响其余来源
            }
        }
        Candidate claude = fromClaudeSettings(new File(home, ".claude" + File.separator + "settings.json"));
        if (claude != null) {
            out.add(claude);
        }
        Candidate codex = fromCodex(new File(home, ".codex"));
        if (codex != null) {
            out.add(codex);
        }
        return out;
    }

    /** 解析一个 cc-switch config.json（或结构类似的 JSON），递归提取其中所有服务商。 */
    public static void parseFile(@NotNull File file, @NotNull List<Candidate> out) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Object root = Json.parse(text);
        scanJson(root, "cc-switch", out);
    }

    /** 递归遍历 JSON 树：任何「同时具备 key 类字段 + 地址类字段」的 Map 视为一个服务商（兼容各版本 schema 差异）。 */
    private static void scanJson(@Nullable Object node, @NotNull String source, @NotNull List<Candidate> out) {
        if (node instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) node;
            Candidate c = asCandidate(m, source);
            if (c != null) {
                out.add(c);
            }
            for (Object v : m.values()) {
                scanJson(v, source, out);
            }
        } else if (node instanceof List) {
            for (Object v : (List<?>) node) {
                scanJson(v, source, out);
            }
        }
    }

    @Nullable
    private static Candidate asCandidate(@NotNull Map<String, Object> m, @NotNull String source) {
        String key = firstStr(m, KEY_FIELDS);
        // key 类字段常嵌在 settingsConfig.env 中（cc-switch 的 Claude Code 快照结构）
        Map<String, Object> env = envOf(m);
        if (key == null && env != null) {
            key = firstStr(env, KEY_FIELDS);
        }
        if (key == null) {
            return null;
        }
        String url = firstStr(m, URL_FIELDS);
        String model = firstStr(m, MODEL_FIELDS);
        if (env != null) {
            if (url == null) {
                url = firstStr(env, URL_FIELDS);
            }
            if (model == null) {
                model = firstStr(env, MODEL_FIELDS);
            }
        }
        if (url == null) {
            return null;
        }
        String name = firstStr(m, NAME_FIELDS);
        String provider = guessProvider(url, key, m);
        return new Candidate(source, name, provider, url, model, key);
    }

    /** 取 settingsConfig.env / env / settings.env 三种常见嵌套位置。 */
    @Nullable
    private static Map<String, Object> envOf(@NotNull Map<String, Object> m) {
        for (String wrapper : new String[]{"settingsConfig", "settings", "config"}) {
            Map<String, Object> w = Json.asMap(m.get(wrapper));
            if (w != null) {
                Map<String, Object> env = Json.asMap(w.get("env"));
                if (env != null) {
                    return env;
                }
            }
        }
        return Json.asMap(m.get("env"));
    }

    private static String guessProvider(@NotNull String url, @NotNull String key, @NotNull Map<String, Object> m) {
        String all = (url + " " + key + " " + m.keySet()).toLowerCase();
        if (all.contains("anthropic")) {
            return AiConfig.PROVIDER_ANTHROPIC;
        }
        return AiConfig.PROVIDER_OPENAI;
    }

    /** ~/.claude/settings.json 的 env 块（cc-switch / Claude Code 写入的当前生效配置）。 */
    @Nullable
    private static Candidate fromClaudeSettings(@NotNull File settingsJson) {
        if (!settingsJson.isFile()) {
            return null;
        }
        try {
            Map<String, Object> root = Json.asMap(Json.parse(
                    new String(Files.readAllBytes(settingsJson.toPath()), StandardCharsets.UTF_8)));
            if (root == null) {
                return null;
            }
            Map<String, Object> env = Json.asMap(root.get("env"));
            if (env == null) {
                return null;
            }
            String key = firstStr(env, "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_API_KEY", "apiKey");
            String url = firstStr(env, "ANTHROPIC_BASE_URL", "baseUrl");
            if (key == null && url == null) {
                return null;
            }
            String model = firstStr(env, "ANTHROPIC_MODEL", "model");
            return new Candidate("Claude Code", "Claude Code · " + hostOf(url == null ? "" : url),
                    AiConfig.PROVIDER_ANTHROPIC, url, model, key);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** ~/.codex/config.toml（model_provider / model_providers.*.base_url / model）+ ~/.codex/auth.json（OPENAI_API_KEY）。 */
    @Nullable
    private static Candidate fromCodex(@NotNull File codexDir) {
        File config = new File(codexDir, "config.toml");
        if (!config.isFile()) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
            String model = null;
            String activeProvider = null;
            Map<String, String> providerBaseUrls = new LinkedHashMap<>();
            String section = "";
            for (String raw : text.split("\r?\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[")) {
                    section = line;
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = line.substring(0, eq).trim();
                String v = unquote(line.substring(eq + 1).trim());
                if (section.isEmpty() && "model".equals(k)) {
                    model = v;
                } else if (section.isEmpty() && "model_provider".equals(k)) {
                    activeProvider = v;
                } else if (section.startsWith("[model_providers.")) {
                    String id = section.substring("[model_providers.".length());
                    id = id.replace("]", "").replace("\"", "").trim();
                    if ("base_url".equals(k)) {
                        providerBaseUrls.put(id, v);
                    }
                }
            }
            String url = activeProvider != null ? providerBaseUrls.get(activeProvider) : null;
            if (url == null && providerBaseUrls.size() == 1) {
                url = providerBaseUrls.values().iterator().next();
            }
            if (url == null) {
                return null; // 官方 ChatGPT 登录态（无自定义服务商）无可导入内容
            }
            String key = null;
            File auth = new File(codexDir, "auth.json");
            if (auth.isFile()) {
                try {
                    Map<String, Object> authMap = Json.asMap(Json.parse(
                            new String(Files.readAllBytes(auth.toPath()), StandardCharsets.UTF_8)));
                    key = authMap == null ? null : firstStr(authMap, "OPENAI_API_KEY", "apiKey");
                } catch (Exception ignore) {
                }
            }
            String label = activeProvider == null || activeProvider.isEmpty() ? "default" : activeProvider;
            return new Candidate("Codex", "Codex · " + label,
                    AiConfig.PROVIDER_OPENAI, url, model, key);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Nullable
    private static String firstStr(@NotNull Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            String s = v == null ? null : Json.str(v);
            if (s != null && !s.trim().isEmpty() && !"null".equals(s)) {
                return s.trim();
            }
        }
        return null;
    }

    /** 从 URL 提取主机名用于展示；空串返回空。 */
    private static String hostOf(String url) {
        String s = url == null ? "" : url.trim();
        if (s.isEmpty()) {
            return "api.anthropic.com";
        }
        int scheme = s.indexOf("://");
        String rest = scheme >= 0 ? s.substring(scheme + 3) : s;
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : rest;
    }

    private static String unquote(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
