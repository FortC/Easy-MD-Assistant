package com.aitools.mdassistant.model;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;

/** 项目中的一个 Markdown 文档。 */
public final class MdFile {

    private final VirtualFile file;
    private final String relativePath;
    private final boolean fromAiToolDir;
    private final long timeStamp;
    private final String dateKey;

    public MdFile(@NotNull VirtualFile file, @NotNull String relativePath, boolean fromAiToolDir) {
        this.file = file;
        this.relativePath = relativePath;
        this.fromAiToolDir = fromAiToolDir;
        this.timeStamp = file.getTimeStamp();
        this.dateKey = new SimpleDateFormat("yyyy-MM-dd").format(new Date(timeStamp));
    }

    @NotNull
    public VirtualFile getFile() {
        return file;
    }

    @NotNull
    public String getRelativePath() {
        return relativePath;
    }

    /** 是否来自 AI 工具目录（如 .codex / .claude / .workbuddy）。 */
    public boolean isFromAiToolDir() {
        return fromAiToolDir;
    }

    /** 文件修改时间戳（毫秒）。 */
    public long getTimeStamp() {
        return timeStamp;
    }

    /** 文件修改日期（yyyy-MM-dd），用于按日期分组。 */
    @NotNull
    public String getDateKey() {
        return dateKey;
    }

    @NotNull
    public String getName() {
        return file.getName();
    }

    @NotNull
    public String getTitle() {
        return file.getNameWithoutExtension();
    }

    /** 相对于分类节点的展示路径（去掉文件名本身）。 */
    @NotNull
    public String getParentDirName() {
        VirtualFile p = file.getParent();
        return p == null ? "" : p.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
