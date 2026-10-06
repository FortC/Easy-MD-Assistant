package com.aitools.mdassistant.scan;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 全项目 Markdown 文档扫描（BFS 遍历，跳过构建/依赖目录；支持 AI 工具目录按需过滤）。 */
public final class MdScanner {

    private static final Set<String> EXCLUDED_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".svn", ".hg", ".idea", ".gradle", ".mvn", "build", "out", "target",
            "node_modules", "bower_components", "vendor", "dist", ".next", ".nuxt",
            "__pycache__", ".pytest_cache", "venv", ".venv", ".terraform", ".cache",
            "tmp", "temp", "logs", "coverage", "cmake-build-debug", "cmake-build-release"));

    public static final int MAX_FILES = 5000;

    public static final class ScanResult {
        public final List<VirtualFile> files = new ArrayList<>();
        /** files 的子集：来自 AI 工具目录（.codex / .claude 等，含其子目录）的文档。 */
        public final Set<VirtualFile> aiToolFiles = new HashSet<>();
    }

    private static final class DirEntry {
        final VirtualFile dir;
        final boolean inAiDir;

        DirEntry(VirtualFile dir, boolean inAiDir) {
            this.dir = dir;
            this.inAiDir = inAiDir;
        }
    }

    /**
     * @param aiToolDirs     AI 工具目录名单（按目录名匹配，如 .codex、.claude）
     * @param includeAiToolDirs true 时进入 AI 工具目录扫描并打标记；false 时整棵子树跳过
     */
    @NotNull
    public static ScanResult scan(@NotNull Project project,
                                  @Nullable ProgressIndicator indicator,
                                  @NotNull Set<String> aiToolDirs,
                                  boolean includeAiToolDirs) {
        ScanResult result = new ScanResult();
        VirtualFile base = project.getBaseDir();
        if (base == null || !base.isValid()) {
            return result;
        }
        return ApplicationManager.getApplication().runReadAction((Computable<ScanResult>) () -> {
            Deque<DirEntry> queue = new ArrayDeque<>();
            queue.add(new DirEntry(base, false));
            while (!queue.isEmpty()) {
                if (indicator != null) {
                    indicator.checkCanceled();
                }
                DirEntry cur = queue.poll();
                if (!cur.dir.isValid()) {
                    continue;
                }
                for (VirtualFile child : cur.dir.getChildren()) {
                    if (result.files.size() >= MAX_FILES) {
                        return result;
                    }
                    if (child.isDirectory()) {
                        if (EXCLUDED_DIRS.contains(child.getName())) {
                            continue;
                        }
                        boolean childAi = cur.inAiDir || aiToolDirs.contains(child.getName());
                        if (!includeAiToolDirs && childAi) {
                            continue;
                        }
                        queue.add(new DirEntry(child, childAi));
                    } else {
                        String ext = child.getExtension();
                        if (ext != null && ("md".equalsIgnoreCase(ext) || "markdown".equalsIgnoreCase(ext))) {
                            result.files.add(child);
                            if (cur.inAiDir) {
                                result.aiToolFiles.add(child);
                            }
                        }
                    }
                }
            }
            return result;
        });
    }

    private MdScanner() {
    }
}
