package com.aitools.mdassistant.ai;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 应用级配置：AI 服务 + AI 工具目录过滤 + 文件夹分类映射。
 * API Key 优先存放在 IDE 凭据库（PasswordSafe）：
 *  - 2021.2+ 使用 com.intellij.credentialStore.PasswordSafe；
 *  - 2021.0/2021.1 使用旧版 com.intellij.ide.passwordSafe.PasswordSafe；
 * 两者均通过反射访问以兼容所有平台版本；都不可用时退回本地状态明文存储。
 */
@State(name = "MdAssistantAiSettings", storages = @Storage("md-assistant.xml"))
public class AiSettings implements PersistentStateComponent<AiSettings.State> {

    private static final String CRED_SERVICE = "com.aitools.mdassistant";
    private static final String CRED_USER = "api_key";
    private static final String CRED_KEY = "MD Assistant API Key";

    /** 预置的常见 AI 编码工具目录（用户可在设置中增删）。 */
    public static final String DEFAULT_AI_TOOL_DIRS = String.join("\n", Arrays.asList(
            ".codex", ".workbuddy", ".claude", ".claude-code", ".cursor", ".aider", ".continue",
            ".copilot", ".github-copilot", ".windsurf", ".codeium", ".tabnine", ".cody",
            ".gemini", ".qwen", ".kiro", ".trae", ".augment", ".augment-guidelines", ".cline",
            ".roo", ".goose", ".openai", ".anthropic", ".zcode", ".openhands", ".devin",
            ".avante", ".spec-story", "specstory", "memory-bank"));

    public static class State {
        public String provider = AiConfig.PROVIDER_OPENAI;
        public String baseUrl = "";
        public String model = "";
        public int maxTokens = 2048;
        public int timeoutSeconds = 120;
        public String fallbackApiKey = "";
        public String aiToolDirsText = DEFAULT_AI_TOOL_DIRS;
        public boolean showAiToolDocs = false;
        public Map<String, String> dirMapping = new LinkedHashMap<>();
        public String uiLanguage = "auto";
        /** 目录库（全局类别词表，可编辑维护）。 */
        public java.util.List<String> categories =
                new java.util.ArrayList<>(com.aitools.mdassistant.cat.AiClassifier.CATEGORIES);
        /** 四个知识库目录的全局默认（项目根目录下的相对目录名；项目级可覆盖）。 */
        public String libRequirement = "需求库";
        public String libResult = "成果库";
        public String libError = "错误库";
        public String libExample = "示例库";
        public String aiRulesTarget = "AGENTS.md";
        public boolean ruleAvoidError = true;
        public boolean rulePreferExample = true;
        public boolean ruleArchiveResult = true;
        public boolean ruleReadRequirement = true;
        public String ruleExtra = "";
        public boolean autoCreateLibDirs = true;
        /** 打开工具窗口时对话区（上下文条 + 对话记录）是否默认折叠，仅保留提问输入区。 */
        public boolean chatAreaCollapsed = false;
        /** 默认分组方式：0=按目录，1=智能分类，2=不分组，3=按日期。 */
        public int defaultGroupMode = 0;
    }

    /** 目录库（类别词表）；为空时回退到内置 10 类。 */
    @NotNull
    public java.util.List<String> getCategories() {
        if (state.categories == null || state.categories.isEmpty()) {
            return com.aitools.mdassistant.cat.AiClassifier.CATEGORIES;
        }
        return state.categories;
    }

    public void setCategories(@Nullable java.util.List<String> categories) {
        state.categories = categories == null
                ? new java.util.ArrayList<>(com.aitools.mdassistant.cat.AiClassifier.CATEGORIES)
                : new java.util.ArrayList<>(categories);
    }

    private State state = new State();

    public static AiSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getProvider() {
        return state.provider == null || state.provider.isEmpty()
                ? AiConfig.PROVIDER_OPENAI : state.provider;
    }

    public String getBaseUrl() {
        return state.baseUrl == null ? "" : state.baseUrl.trim();
    }

    public String getModel() {
        return state.model == null ? "" : state.model.trim();
    }

    public int getMaxTokens() {
        return state.maxTokens <= 0 ? 2048 : state.maxTokens;
    }

    public int getTimeoutSeconds() {
        return state.timeoutSeconds <= 0 ? 120 : state.timeoutSeconds;
    }

    public void setProvider(String provider) {
        state.provider = provider;
    }

    public void setBaseUrl(String baseUrl) {
        state.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public void setModel(String model) {
        state.model = model == null ? "" : model.trim();
    }

    public void setMaxTokens(int maxTokens) {
        state.maxTokens = maxTokens;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        state.timeoutSeconds = timeoutSeconds;
    }

    public String getApiKey() {
        String key = readFromPasswordSafe();
        if (key != null && !key.isEmpty()) {
            return key;
        }
        return state.fallbackApiKey == null ? "" : state.fallbackApiKey;
    }

    public void setApiKey(String key) {
        String v = key == null ? "" : key.trim();
        if (writeToPasswordSafe(v)) {
            state.fallbackApiKey = "";
        } else {
            state.fallbackApiKey = v;
        }
    }

    private static String readFromPasswordSafe() {
        // 现代实现（2021.2+）：credentialStore.PasswordSafe
        try {
            Class<?> ps = Class.forName("com.intellij.credentialStore.PasswordSafe");
            Object instance = ps.getMethod("getInstance").invoke(null);
            Class<?> caClass = Class.forName("com.intellij.credentialStore.CredentialAttributes");
            Object attrs = caClass.getConstructor(String.class, String.class)
                    .newInstance(CRED_SERVICE, CRED_USER);
            Object creds = ps.getMethod("get", caClass).invoke(instance, attrs);
            if (creds != null) {
                String pw = (String) creds.getClass().getMethod("getPasswordAsString").invoke(creds);
                if (pw != null) {
                    return pw;
                }
            }
            return "";
        } catch (Throwable ignore) {
            // 回退旧实现
        }
        // 旧实现（2021.0 / 2021.1）：ide.passwordSafe.PasswordSafe
        try {
            Class<?> ps = Class.forName("com.intellij.ide.passwordSafe.PasswordSafe");
            Object instance = ps.getMethod("getInstance").invoke(null);
            Object pw = ps.getMethod("getPassword",
                    Class.forName("com.intellij.openapi.project.Project"), Class.class, String.class)
                    .invoke(instance, null, AiSettings.class, CRED_KEY);
            return pw == null ? "" : pw.toString();
        } catch (Throwable ignore) {
            // 回退本地存储
        }
        return null;
    }

    private static boolean writeToPasswordSafe(String value) {
        // 现代实现
        try {
            Class<?> ps = Class.forName("com.intellij.credentialStore.PasswordSafe");
            Object instance = ps.getMethod("getInstance").invoke(null);
            Class<?> caClass = Class.forName("com.intellij.credentialStore.CredentialAttributes");
            Class<?> credClass = Class.forName("com.intellij.credentialStore.Credentials");
            Object attrs = caClass.getConstructor(String.class, String.class)
                    .newInstance(CRED_SERVICE, CRED_USER);
            Object creds = value.isEmpty() ? null
                    : credClass.getConstructor(String.class, String.class).newInstance(CRED_USER, value);
            ps.getMethod("set", caClass, credClass).invoke(instance, attrs, creds);
            return true;
        } catch (Throwable ignore) {
            // 回退旧实现
        }
        // 旧实现
        try {
            Class<?> ps = Class.forName("com.intellij.ide.passwordSafe.PasswordSafe");
            Object instance = ps.getMethod("getInstance").invoke(null);
            ps.getMethod("storePassword",
                    Class.forName("com.intellij.openapi.project.Project"), Class.class,
                    String.class, String.class)
                    .invoke(instance, null, AiSettings.class, CRED_KEY, value);
            return true;
        } catch (Throwable ignore) {
            // 回退本地存储
        }
        return false;
    }

    public boolean isConfigured() {
        return !getApiKey().isEmpty() && !getModel().isEmpty();
    }

    /** 解析 AI 工具目录名单（换行分隔，忽略空行与首尾空白）。 */
    @NotNull
    public Set<String> getAiToolDirs() {
        Set<String> dirs = new LinkedHashSet<>();
        String text = state.aiToolDirsText == null ? "" : state.aiToolDirsText;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                dirs.add(t);
            }
        }
        return dirs;
    }

    @NotNull
    public String getAiToolDirsText() {
        return state.aiToolDirsText == null ? "" : state.aiToolDirsText;
    }

    public void setAiToolDirsText(@Nullable String text) {
        state.aiToolDirsText = text == null ? "" : text;
    }

    public boolean isShowAiToolDocs() {
        return state.showAiToolDocs;
    }

    public void setShowAiToolDocs(boolean show) {
        state.showAiToolDocs = show;
    }

    @NotNull
    public Map<String, String> getDirMapping() {
        return state.dirMapping == null ? (state.dirMapping = new LinkedHashMap<>()) : state.dirMapping;
    }

    public void setDirMapping(@NotNull Map<String, String> mapping) {
        state.dirMapping = mapping;
    }

    @NotNull
    public String getUiLanguage() {
        return state.uiLanguage == null ? "auto" : state.uiLanguage;
    }

    public void setUiLanguage(@Nullable String lang) {
        state.uiLanguage = lang == null ? "auto" : lang;
    }

    @NotNull
    public String getLibRequirement() {
        return safe(state.libRequirement);
    }

    public void setLibRequirement(String v) {
        state.libRequirement = v == null ? "" : v.trim();
    }

    @NotNull
    public String getLibResult() {
        return safe(state.libResult);
    }

    public void setLibResult(String v) {
        state.libResult = v == null ? "" : v.trim();
    }

    @NotNull
    public String getLibError() {
        return safe(state.libError);
    }

    public void setLibError(String v) {
        state.libError = v == null ? "" : v.trim();
    }

    @NotNull
    public String getLibExample() {
        return safe(state.libExample);
    }

    public void setLibExample(String v) {
        state.libExample = v == null ? "" : v.trim();
    }

    @NotNull
    public String getAiRulesTarget() {
        String v = state.aiRulesTarget == null ? "" : state.aiRulesTarget.trim();
        return v.isEmpty() ? "AGENTS.md" : v;
    }

    public void setAiRulesTarget(String v) {
        state.aiRulesTarget = v == null ? "" : v.trim();
    }

    public boolean isRuleAvoidError() {
        return state.ruleAvoidError;
    }

    public void setRuleAvoidError(boolean v) {
        state.ruleAvoidError = v;
    }

    public boolean isRulePreferExample() {
        return state.rulePreferExample;
    }

    public void setRulePreferExample(boolean v) {
        state.rulePreferExample = v;
    }

    public boolean isRuleArchiveResult() {
        return state.ruleArchiveResult;
    }

    public void setRuleArchiveResult(boolean v) {
        state.ruleArchiveResult = v;
    }

    public boolean isRuleReadRequirement() {
        return state.ruleReadRequirement;
    }

    public void setRuleReadRequirement(boolean v) {
        state.ruleReadRequirement = v;
    }

    @NotNull
    public String getRuleExtra() {
        return state.ruleExtra == null ? "" : state.ruleExtra;
    }

    public void setRuleExtra(String v) {
        state.ruleExtra = v == null ? "" : v;
    }

    public boolean isAutoCreateLibDirs() {
        return state.autoCreateLibDirs;
    }

    public void setAutoCreateLibDirs(boolean v) {
        state.autoCreateLibDirs = v;
    }

    public boolean isChatAreaCollapsed() {
        return state.chatAreaCollapsed;
    }

    public void setChatAreaCollapsed(boolean v) {
        state.chatAreaCollapsed = v;
    }

    public int getDefaultGroupMode() {
        return state.defaultGroupMode >= 0 && state.defaultGroupMode <= 3 ? state.defaultGroupMode : 0;
    }

    public void setDefaultGroupMode(int v) {
        state.defaultGroupMode = v >= 0 && v <= 3 ? v : 0;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    public String effectiveBaseUrl(String provider) {
        if (!getBaseUrl().isEmpty()) {
            return getBaseUrl();
        }
        return AiConfig.PROVIDER_ANTHROPIC.equals(provider)
                ? "https://api.anthropic.com" : "https://api.openai.com";
    }
}
