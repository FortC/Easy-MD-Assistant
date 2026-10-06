package com.aitools.mdassistant.cat;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置规则分类器：根据路径 / 标题 / 摘要中的关键词打分推断文档类别，无需联网。
 * 仅作兜底启发式，结果不持久化。
 */
public final class RuleClassifier {

    private static final Map<String, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put("项目说明", Arrays.asList(
                "readme", "说明", "introduction", "intro", "overview", "homepage", "项目介绍", "关于项目"));
        RULES.put("设计文档", Arrays.asList(
                "design", "设计", "架构", "architecture", "方案", "scheme", "blueprint", "概要设计",
                "详细设计", "domain", "时序", "er图", "数据模型", "原型"));
        RULES.put("开发文档", Arrays.asList(
                "changelog", "变更", "release", "发布", "deploy", "部署", "install", "安装", "build",
                "构建", "规范", "standard", "coding", "convention", "贡献", "contribute", "开发指南",
                "dev", "debug", "调试", "脚本"));
        RULES.put("接口文档", Arrays.asList(
                "api", "接口", "rest", "graphql", "endpoint", "openapi", "swagger", "proto",
                "protobuf", "grpc", "webhook"));
        RULES.put("教程指南", Arrays.asList(
                "tutorial", "教程", "guide", "指南", "howto", "how-to", "demo", "示例", "example",
                "sample", "walkthrough", "入门", "quickstart", "快速开始", "cookbook", "手册"));
        RULES.put("会议记录", Arrays.asList(
                "meeting", "会议", "纪要", "minutes", "周报", "日报", "月报", "standup", "复盘", "retro"));
        RULES.put("计划任务", Arrays.asList(
                "todo", "待办", "task", "任务", "计划", "plan", "roadmap", "路线", "milestone",
                "里程碑", "backlog", "schedule", "排期", "sprint", "okr"));
        RULES.put("笔记备忘", Arrays.asList(
                "note", "笔记", "备忘", "memo", "journal", "手记", "tips", "经验", "draft", "草稿",
                "scratch", "learn", "学习", "知识库", "摘录"));
        RULES.put("翻译内容", Arrays.asList(
                "translate", "翻译", "translation", "译文", "i18n", "本地化", "localization", "双语"));
    }

    private RuleClassifier() {
    }

    /**
     * @param relativePath 项目内相对路径（权重最高）
     * @param title        文档标题（可为 null）
     * @param excerpt      内容摘要（可为 null）
     * @return 推断类别；无法判断时返回 null
     */
    @Nullable
    public static String classify(@Nullable String relativePath, @Nullable String title, @Nullable String excerpt) {
        String path = relativePath == null ? "" : relativePath.toLowerCase();
        String text = ((title == null ? "" : title.toLowerCase()) + " "
                + (excerpt == null ? "" : excerpt.toLowerCase()));
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, List<String>> e : RULES.entrySet()) {
            double score = 0;
            for (String kw : e.getValue()) {
                if (hit(path, kw)) {
                    score += 2;
                }
                if (hit(text, kw)) {
                    score += 0.5;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return bestScore >= 1 ? best : null;
    }

    /** 包含匹配，并对 ASCII 关键词做词边界检查，避免 rapid 命中 api 之类的误伤。 */
    private static boolean hit(String text, String kw) {
        if (text.isEmpty() || kw.isEmpty()) {
            return false;
        }
        int i = text.indexOf(kw);
        if (i < 0) {
            return false;
        }
        if (!isAscii(kw)) {
            return true; // 中文关键词直接包含即可
        }
        while (i >= 0) {
            int end = i + kw.length();
            boolean beforeOk = i == 0 || !isWordChar(text.charAt(i - 1));
            boolean afterOk = end >= text.length() || !isWordChar(text.charAt(end));
            if (beforeOk && afterOk) {
                return true;
            }
            i = text.indexOf(kw, i + 1);
        }
        return false;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
    }
}
