package com.aitools.mdassistant.cat;

import com.aitools.mdassistant.ai.AiClient;
import com.aitools.mdassistant.ai.AiConfig;
import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.ai.Json;
import com.aitools.mdassistant.model.MdFile;
import com.aitools.mdassistant.util.I18n;
import com.aitools.mdassistant.util.MdText;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 用大模型对 Markdown 文档进行批量分类。 */
public final class AiClassifier {

    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "项目说明", "设计文档", "开发文档", "接口文档", "教程指南",
            "会议记录", "计划任务", "笔记备忘", "翻译内容", "其他"));

    public static final String UNCLASSIFIED = "未分类";

    /**
     * 统一类别词表（目录库）：全局维护的类别列表 + 本项目生效的知识库目录名（去重、保持顺序）。
     * AI 批量分类、对话式分类、映射表下拉框、移动归档共用，保证「配置目录」与「AI 归类类别」一致。
     */
    @NotNull
    public static List<String> allCategories(@NotNull Project project) {
        AiSettings s = AiSettings.getInstance();
        Set<String> set = new LinkedHashSet<>(s.getCategories());
        for (String lib : CategoryService.effectiveKnowledgeDirs(project)) {
            if (!lib.isEmpty()) {
                set.add(lib);
            }
        }
        return new ArrayList<>(set);
    }

    public interface Listener {
        void onProgress(int done, int total);

        /**
         * @param cancelled 用户取消
         * @param error      出错（message 为错误信息）
         * @param message    完成提示；null 表示成功无话要说
         */
        void onFinished(boolean cancelled, boolean error, String message);
    }

    private static final int BATCH_SIZE = 15;

    private AiClassifier() {
    }

    public static void classify(@NotNull Project project,
                                @NotNull List<MdFile> files,
                                @NotNull ProgressIndicator indicator,
                                @NotNull Listener listener) {
        AiSettings settings = AiSettings.getInstance();
        if (!settings.isConfigured()) {
            listener.onFinished(false, false, I18n.t("notify.needConfig"));
            return;
        }
        CategoryService cats = CategoryService.getInstance(project);
        AiClient client = new AiClient(AiConfig.from(settings));
        List<String> allowed = allCategories(project);
        try {
            for (int start = 0; start < files.size(); start += BATCH_SIZE) {
                indicator.checkCanceled();
                List<MdFile> batch = files.subList(start, Math.min(start + BATCH_SIZE, files.size()));
                classifyBatch(client, cats, batch, allowed);
                int done = Math.min(start + BATCH_SIZE, files.size());
                indicator.setFraction(done / (double) files.size());
                indicator.setText(I18n.f("classify.progress", done, files.size()));
                listener.onProgress(done, files.size());
            }
            listener.onFinished(false, false, null);
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            listener.onFinished(true, false, null);
        } catch (Exception e) {
            listener.onFinished(false, true, e.getMessage());
        }
    }

    private static void classifyBatch(AiClient client, CategoryService cats, List<MdFile> batch,
                                      List<String> allowed) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为下列 Markdown 文档分类。类别必须从以下集合中选择：")
                .append(String.join("、", allowed)).append('\n')
                .append("集合末尾为项目知识库类别；文档内容明显属于某个知识库（需求/示例/错误复盘/成果记录）时优先选择对应知识库类别。")
                .append('\n')
                .append("输出严格的 JSON，格式：{\"results\":[{\"i\":<序号>,\"c\":\"<类别>\"}]}，必须覆盖全部文档，不要输出任何其他内容。\n")
                .append("文档列表：\n");
        for (int i = 0; i < batch.size(); i++) {
            MdFile f = batch.get(i);
            String content = MdText.read(f.getFile());
            String title = MdText.title(content);
            if (title == null || title.isEmpty()) {
                title = f.getTitle();
            }
            sb.append(i + 1).append(". 《").append(title).append("》 路径: ")
                    .append(f.getRelativePath())
                    .append(" 摘要: ").append(MdText.excerpt(content, 300))
                    .append('\n');
        }
        String resp = client.chatSync("你是文档分类助手，只输出 JSON，不输出解释。",
                Collections.singletonList(new AiClient.Msg("user", sb.toString())));
        Map<String, Object> parsed = Json.asMap(Json.parse(stripFences(resp)));
        List<Object> results = parsed == null ? null : Json.asList(parsed.get("results"));
        if (results == null) {
            throw new AiClassifierException(I18n.f("classify.badFormat", MdText.excerpt(resp, 120)));
        }
        for (Object ro : results) {
            Map<String, Object> r = Json.asMap(ro);
            if (r == null) {
                continue;
            }
            int i = Json.getInt(r, "i", -1);
            String c = Json.getStr(r, "c");
            if (i < 1 || i > batch.size() || c == null) {
                continue;
            }
            if (!allowed.contains(c)) {
                c = "其他";
            }
            cats.store(batch.get(i - 1).getFile(), c);
        }
    }

    public static String stripFences(String s) {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                t = t.substring(firstNl + 1, lastFence).trim();
            }
        }
        return t;
    }

    public static class AiClassifierException extends RuntimeException {
        public AiClassifierException(String message) {
            super(message);
        }
    }
}
