package com.aitools.mdassistant.cat;

import com.aitools.mdassistant.ai.AiSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目级状态：AI 分类结果、被忽略的文档（含忽略时间）、知识库目录的项目级覆盖。
 * 知识库目录默认取 AiSettings 的全局配置，项目覆盖优先（role: req/exa/res/err）。
 */
@State(name = "MdAssistantCategories", storages = @Storage("md-assistant.xml"))
public class CategoryService implements PersistentStateComponent<CategoryService.State> {

    public static final String ROLE_REQ = "req";
    public static final String ROLE_EXA = "exa";
    public static final String ROLE_RES = "res";
    public static final String ROLE_ERR = "err";

    public static class State {
        public Map<String, String> category = new LinkedHashMap<>();
        public Map<String, String> stamp = new LinkedHashMap<>();
        /** 文件 URL -> 忽略时间（yyyy-MM-dd HH:mm）。 */
        public Map<String, String> ignored = new LinkedHashMap<>();
        /** 知识库角色 -> 目录名（项目级覆盖；值为空串表示本项目停用该库）。 */
        public Map<String, String> knowledgeOverrides = new LinkedHashMap<>();
        /** AI 对话生成的过滤规则（支持 * 通配符，匹配相对路径/文件名，大小写不敏感）。 */
        public List<String> aiFilters = new ArrayList<>();
    }

    private State state = new State();

    public static CategoryService getInstance(@NotNull Project project) {
        return project.getService(CategoryService.class);
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

    @Nullable
    public String categoryOf(@NotNull VirtualFile file) {
        String url = file.getUrl();
        String cat = state.category.get(url);
        if (cat == null) {
            return null;
        }
        String stamp = state.stamp.get(url);
        // 文件在分类之后被修改过，则视为失效
        if (stamp != null && !stamp.equals(String.valueOf(file.getModificationStamp()))) {
            return null;
        }
        return cat;
    }

    public void store(@NotNull VirtualFile file, @NotNull String category) {
        String url = file.getUrl();
        state.category.put(url, category);
        state.stamp.put(url, String.valueOf(file.getModificationStamp()));
    }

    // ==================== 忽略 ====================

    public Map<String, String> getIgnored() {
        return state.ignored == null ? (state.ignored = new LinkedHashMap<>()) : state.ignored;
    }

    public void ignore(@NotNull VirtualFile file) {
        getIgnored().put(file.getUrl(),
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
    }

    public void unignore(@NotNull String url) {
        getIgnored().remove(url);
    }

    public void clearIgnored() {
        getIgnored().clear();
    }

    // ==================== AI 对话过滤规则 ====================

    public List<String> getAiFilters() {
        return state.aiFilters == null ? (state.aiFilters = new ArrayList<>()) : state.aiFilters;
    }

    /** 新增过滤规则（去重）；空规则忽略。 */
    public void addAiFilter(@Nullable String pattern) {
        String p = pattern == null ? "" : pattern.trim();
        if (p.isEmpty()) {
            return;
        }
        List<String> filters = getAiFilters();
        if (!filters.contains(p)) {
            filters.add(p);
        }
    }

    public void clearAiFilters() {
        getAiFilters().clear();
    }

    /** 相对路径是否命中任一过滤规则（支持 * 通配符；无通配符时按包含匹配）。 */
    public static boolean matchesAnyFilter(@NotNull String relativePath, @Nullable List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        String path = relativePath.replace('\\', '/').toLowerCase();
        String name = path.substring(path.lastIndexOf('/') + 1);
        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            String p = pattern.trim().replace('\\', '/').toLowerCase();
            if (p.isEmpty()) {
                continue;
            }
            if (p.indexOf('*') >= 0) {
                if (globMatches(p, path)) {
                    return true;
                }
            } else if (path.contains(p) || name.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** 极简通配符匹配：* 匹配任意字符序列，其余字符按字面比较。 */
    private static boolean globMatches(@NotNull String pattern, @NotNull String path) {
        // 经典双指针回溯算法，无需构造正则
        int pi = 0, ti = 0, star = -1, mark = -1;
        int pn = pattern.length(), tn = path.length();
        while (ti < tn) {
            if (pi < pn && (pattern.charAt(pi) == path.charAt(ti))) {
                pi++;
                ti++;
            } else if (pi < pn && pattern.charAt(pi) == '*') {
                star = pi++;
                mark = ti;
            } else if (star >= 0) {
                pi = star + 1;
                ti = ++mark;
            } else {
                return false;
            }
        }
        while (pi < pn && pattern.charAt(pi) == '*') {
            pi++;
        }
        return pi == pn;
    }

    // ==================== 知识库项目级覆盖 ====================

    public Map<String, String> getKnowledgeOverrides() {
        return state.knowledgeOverrides == null
                ? (state.knowledgeOverrides = new LinkedHashMap<>()) : state.knowledgeOverrides;
    }

    public void setKnowledgeOverrides(Map<String, String> overrides) {
        state.knowledgeOverrides = overrides == null ? new LinkedHashMap<>() : overrides;
    }

    /** 项目覆盖（可为空串=停用）优先，否则取全局默认。 */
    private String effective(String role, String globalDefault) {
        Map<String, String> ov = getKnowledgeOverrides();
        if (ov.containsKey(role)) {
            return ov.get(role) == null ? "" : ov.get(role);
        }
        return globalDefault == null ? "" : globalDefault;
    }

    /** 生效的知识库目录：[需求, 示例, 成果, 错误]。 */
    public static String[] effectiveKnowledgeDirs(@NotNull Project project) {
        AiSettings s = AiSettings.getInstance();
        CategoryService cs = getInstance(project);
        return new String[]{
                cs.effective(ROLE_REQ, s.getLibRequirement()),
                cs.effective(ROLE_EXA, s.getLibExample()),
                cs.effective(ROLE_RES, s.getLibResult()),
                cs.effective(ROLE_ERR, s.getLibError())};
    }

    /**
     * 手动文件夹映射匹配：key 为项目内路径前缀（如 docs/design、notes），
     * 命中相对路径（等于前缀或以 前缀/ 开头）时返回对应类别；最长前缀优先。
     */
    @Nullable
    public static String manualCategory(@NotNull String relativePath, @Nullable Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return null;
        }
        String bestKey = null;
        for (String key : mapping.keySet()) {
            String k = key == null ? "" : key.trim();
            while (k.startsWith("./")) {
                k = k.substring(2);
            }
            while (k.endsWith("/")) {
                k = k.substring(0, k.length() - 1);
            }
            if (k.isEmpty()) {
                continue;
            }
            if (relativePath.equals(k) || relativePath.startsWith(k + "/")) {
                if (bestKey == null || k.length() > bestKey.length()) {
                    bestKey = k;
                }
            }
        }
        return bestKey == null ? null : mapping.get(bestKey);
    }
}
