package com.aitools.mdassistant.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/** 读取 Markdown 文件文本及提取标题/摘要。 */
public final class MdText {

    private MdText() {
    }

    @Nullable
    public static String read(@Nullable VirtualFile file) {
        if (file == null || !file.isValid()) {
            return null;
        }
        try {
            return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
                try {
                    return VfsUtil.loadText(file);
                } catch (IOException e) {
                    return null;
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** 提取第一个一级/二级标题，找不到则返回 null。 */
    @Nullable
    public static String title(String content) {
        if (content == null) {
            return null;
        }
        for (String line : content.split("\n", 100)) {
            String t = line.trim();
            if (t.startsWith("# ") || t.startsWith("## ")) {
                String v = t.replaceFirst("^#+\\s*", "").trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }

    /** 压缩空白后的前 max 个字符摘要。 */
    public static String excerpt(@Nullable String content, int max) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        // 去掉 markdown 声明/ front matter 的干扰字符
        flat = flat.replace("#", "").replace("*", "").replace("`", "");
        if (flat.length() > max) {
            return flat.substring(0, max);
        }
        return flat;
    }
}
