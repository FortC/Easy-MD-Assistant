package com.aitools.mdassistant.cat;

import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.util.I18n;
import com.aitools.mdassistant.util.MdText;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成 / 更新项目 AI 规范文件（默认 AGENTS.md，可配置为 CLAUDE.md 等）。
 * 规则内容来自四个知识库目录：需求库（开发前阅读）、示例库（生成代码优先参考）、
 * 错误库（避免的教训）、成果库（产出归档）。使用标记块合并，不破坏用户手写内容。
 */
public final class AiRulesGenerator {

    public static final String BEGIN_MARKER = "<!-- MD-ASSISTANT:AI-RULES:BEGIN -->";
    public static final String END_MARKER = "<!-- MD-ASSISTANT:AI-RULES:END -->";
    private static final int MAX_ERROR_ITEMS = 30;

    private AiRulesGenerator() {
    }

    /** 生成并写入规则文件，返回目标文件；失败抛出异常。在后台线程调用（含写动作）。 */
    @NotNull
    public static VirtualFile generate(@NotNull Project project, @Nullable ProgressIndicator indicator) throws IOException {
        AiSettings s = AiSettings.getInstance();
        VirtualFile base = project.getBaseDir();
        if (base == null || !base.isValid()) {
            throw new IOException("project base dir unavailable");
        }

        // 先在后台收集错误库要点（读动作）；知识库目录取本项目生效配置（项目级覆盖优先）
        String[] libs = CategoryService.effectiveKnowledgeDirs(project);
        String libReq = libs[0];
        String libExa = libs[1];
        String libRes = libs[2];
        String libErr = libs[3];
        List<String> errorItems = new ArrayList<>();
        if (s.isRuleAvoidError() && !libErr.isEmpty()) {
            VirtualFile dir = base.findChild(libErr);
            if (dir != null && dir.isDirectory()) {
                collectErrorItems(dir, errorItems, indicator);
            }
        }

        String body = buildBody(s, libReq, libExa, libRes, libErr, errorItems);

        // 写入（创建缺失目录 + 合并标记块）
        final IOException[] failure = new IOException[1];
        final VirtualFile[] target = new VirtualFile[1];
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                if (s.isAutoCreateLibDirs()) {
                    for (String lib : libs) {
                        if (!lib.isEmpty()) {
                            VfsUtil.createDirectoryIfMissing(base, lib);
                        }
                    }
                }
                String rel = s.getAiRulesTarget();
                VirtualFile file = base.findFileByRelativePath(rel);
                if (file == null || !file.isValid()) {
                    int slash = Math.max(rel.lastIndexOf('/'), rel.lastIndexOf('\\'));
                    if (slash > 0) {
                        VfsUtil.createDirectoryIfMissing(base, rel.substring(0, slash));
                    }
                    file = base.createChildData(AiRulesGenerator.class, rel.replace('\\', '/'));
                }
                String existing;
                {
                    final VirtualFile vf = file;
                    existing = ApplicationManager.getApplication().runReadAction(
                            (com.intellij.openapi.util.Computable<String>) () -> {
                                try {
                                    return VfsUtil.loadText(vf);
                                } catch (IOException e) {
                                    return "";
                                }
                            });
                }
                String block = BEGIN_MARKER + "\n" + body + "\n" + END_MARKER + "\n";
                String merged = merge(existing, block);
                Document doc = FileDocumentManager.getInstance().getDocument(file);
                if (doc != null) {
                    doc.setText(merged);
                    FileDocumentManager.getInstance().saveDocument(doc);
                } else {
                    VfsUtil.saveText(file, merged);
                }
                target[0] = file;
            } catch (IOException e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        if (target[0] == null) {
            throw new IOException("rules file was not created");
        }
        return target[0];
    }

    private static String merge(String existing, String block) {
        if (existing == null || existing.trim().isEmpty()) {
            return block;
        }
        int b = existing.indexOf(BEGIN_MARKER);
        int e = existing.indexOf(END_MARKER);
        if (b >= 0 && e > b) {
            return existing.substring(0, b) + block + existing.substring(e + END_MARKER.length());
        }
        return existing.endsWith("\n") ? existing + "\n" + block : existing + "\n\n" + block;
    }

    private static void collectErrorItems(VirtualFile dir, List<String> items, ProgressIndicator indicator) {
        for (VirtualFile child : dir.getChildren()) {
            if (indicator != null) {
                indicator.checkCanceled();
            }
            if (items.size() >= MAX_ERROR_ITEMS) {
                return;
            }
            if (child.isDirectory()) {
                collectErrorItems(child, items, indicator);
            } else {
                String ext = child.getExtension();
                if (ext != null && "md".equalsIgnoreCase(ext)) {
                    String content = MdText.read(child);
                    String title = MdText.title(content);
                    if (title == null || title.isEmpty()) {
                        title = child.getNameWithoutExtension();
                    }
                    String excerpt = MdText.excerpt(content, 80);
                    items.add("- " + title + (excerpt.isEmpty() ? "" : "：" + excerpt));
                }
            }
        }
    }

    private static String buildBody(AiSettings s, String libReq, String libExa, String libRes,
                                    String libErr, List<String> errorItems) {
        boolean en = I18n.isEn();
        StringBuilder sb = new StringBuilder();
        sb.append(en ? "# AI Collaboration Rules (managed by EasyMDAssistant; edit outside the markers)\n\n"
                     : "# AI 协作规则（由 EasyMDAssistant 维护，标记块之外的内容可自由编辑）\n\n");

        if (s.isRuleReadRequirement() && !libReq.isEmpty()) {
            sb.append(en ? "## Requirements\n- Read the latest documents in `" + libReq + "/` before starting work.\n\n"
                         : "## 需求\n- 开始工作前，先阅读 `" + libReq + "/` 目录下的最新需求文档。\n\n");
        }
        if (s.isRulePreferExample() && !libExa.isEmpty()) {
            sb.append(en ? "## Prefer\n- When generating code, first look for reference implementations in `"
                            + libExa + "/` and follow their style and conventions.\n\n"
                         : "## 优先参考\n- 生成代码时，优先在 `" + libExa
                            + "/` 目录中查找参考实现，遵循其中的风格与约定。\n\n");
        }
        if (s.isRuleAvoidError() && !libErr.isEmpty()) {
            sb.append(en ? "## Avoid (lessons from `" + libErr + "/`)\n"
                            + "- Do not repeat the mistakes recorded below:\n"
                         : "## 避免（来自 `" + libErr + "/` 错误库）\n"
                            + "- 请勿重复以下记录过的错误：\n");
            if (errorItems.isEmpty()) {
                sb.append(en ? "  - (empty; add documents to the error library)\n"
                             : "  - （暂无记录；向错误库目录添加文档即可自动汇总）\n");
            } else {
                for (String item : errorItems) {
                    sb.append(item).append('\n');
                }
            }
            sb.append('\n');
        }
        if (s.isRuleArchiveResult() && !libRes.isEmpty()) {
            sb.append(en ? "## Archive\n- After completing a task, write a summary (what was built, key decisions, "
                            + "code snippets) into `" + libRes + "/`.\n\n"
                         : "## 归档\n- 任务完成后，将产出说明（做了什么、关键决策、核心代码片段）写入 `"
                            + libRes + "/` 目录。\n\n");
        }
        String extra = s.getRuleExtra();
        if (!extra.trim().isEmpty()) {
            sb.append(en ? "## Extra rules\n" : "## 附加规则\n").append(extra.trim()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
