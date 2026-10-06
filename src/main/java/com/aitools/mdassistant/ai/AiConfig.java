package com.aitools.mdassistant.ai;

/** 一次 AI 请求所需的完整配置快照。 */
public final class AiConfig {

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";

    public final String provider;
    public final String baseUrl;
    public final String model;
    public final String apiKey;
    public final int maxTokens;
    public final int timeoutSeconds;

    public AiConfig(String provider, String baseUrl, String model, String apiKey,
                    int maxTokens, int timeoutSeconds) {
        this.provider = provider;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isAnthropic() {
        return PROVIDER_ANTHROPIC.equals(provider);
    }

    public boolean isComplete() {
        return !model.isEmpty() && !apiKey.isEmpty();
    }

    public String effectiveBaseUrl() {
        if (!baseUrl.isEmpty()) {
            return baseUrl;
        }
        return isAnthropic() ? "https://api.anthropic.com" : "https://api.openai.com";
    }

    public static AiConfig from(AiSettings settings) {
        return new AiConfig(settings.getProvider(), settings.effectiveBaseUrl(settings.getProvider()),
                settings.getModel(), settings.getApiKey(), settings.getMaxTokens(),
                settings.getTimeoutSeconds());
    }
}
