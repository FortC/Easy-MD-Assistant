package com.aitools.mdassistant.ui;

import com.aitools.mdassistant.util.I18n;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 面向单个文档的 AI 快捷操作；名称经 I18n 双语化。 */
public final class QuickOp {

    /** I18n 文案 key */
    public final String nameKey;
    /** 发给模型的提示词 */
    public final String prompt;

    public QuickOp(String nameKey, String prompt) {
        this.nameKey = nameKey;
        this.prompt = prompt;
    }

    public String getName() {
        return I18n.t(nameKey);
    }

    public static final List<QuickOp> ALL = Collections.unmodifiableList(Arrays.asList(
            new QuickOp("op.summarize", "请全面总结这篇文档的核心内容，按要点分条输出。"),
            new QuickOp("op.translate", "请将这篇文档完整翻译成英文，保持原有的 Markdown 结构与格式。"),
            new QuickOp("op.polish", "请润色优化这篇文档的措辞与排版，保持原意不变，输出完整优化后的 Markdown。"),
            new QuickOp("op.outline", "请为这篇文档生成一个层级大纲，每个标题附一句话说明。"),
            new QuickOp("op.todos", "请提取这篇文档中所有待办事项或任务项，输出为任务清单。")
    ));
}
