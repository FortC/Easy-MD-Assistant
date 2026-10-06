package com.aitools.mdassistant.ui.settings;

import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.cat.CategoryService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.LinkedHashMap;
import java.util.Map;

/** 项目级设置页：AI 服务与目录库为全局配置，知识库目录/忽略列表为项目级。 */
public class AiConfigurable implements Configurable {

    public static final String DISPLAY_NAME = "EasyMDAssistant AI";

    private final Project project;
    private AiSettingsComponent component;

    public AiConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (component == null) {
            component = new AiSettingsComponent(project);
        }
        // 包一层滚动容器，小窗口尺寸下控件不再被裁切
        return com.intellij.ui.ScrollPaneFactory.createScrollPane(component.getPanel(), true);
    }

    @Override
    public boolean isModified() {
        if (component == null) {
            return false;
        }
        AiSettings s = AiSettings.getInstance();
        boolean globalModified = !component.getProvider().equals(s.getProvider())
                || !component.getBaseUrl().equals(s.getBaseUrl())
                || !component.getModel().equals(s.getModel())
                || !component.getApiKey().equals(s.getApiKey())
                || component.getMaxTokens() != s.getMaxTokens()
                || component.getTimeout() != s.getTimeoutSeconds()
                || !component.getUiLanguage().equals(s.getUiLanguage())
                || !component.getAiToolDirsText().equals(s.getAiToolDirsText())
                || component.isShowAiToolDocs() != s.isShowAiToolDocs()
                || component.isChatCollapsedByDefault() != s.isChatAreaCollapsed()
                || component.getDefaultGroupMode() != s.getDefaultGroupMode()
                || !component.getLibrary().equals(s.getCategories())
                || component.isAutoCreateLibs() != s.isAutoCreateLibDirs()
                || !component.getRulesTarget().equals(s.getAiRulesTarget())
                || component.isRuleRead() != s.isRuleReadRequirement()
                || component.isRuleExample() != s.isRulePreferExample()
                || component.isRuleAvoid() != s.isRuleAvoidError()
                || component.isRuleArchive() != s.isRuleArchiveResult()
                || !component.getRuleExtra().equals(s.getRuleExtra())
                || !component.getDirMapping().equals(s.getDirMapping());
        boolean knowledgeModified = !component.getKnowledgeSelection().equals(effectiveKnowledge());
        return globalModified || knowledgeModified;
    }

    private Map<String, String> effectiveKnowledge() {
        Map<String, String> map = new LinkedHashMap<>();
        String[] dirs = CategoryService.effectiveKnowledgeDirs(project);
        map.put(CategoryService.ROLE_REQ, dirs[0]);
        map.put(CategoryService.ROLE_EXA, dirs[1]);
        map.put(CategoryService.ROLE_RES, dirs[2]);
        map.put(CategoryService.ROLE_ERR, dirs[3]);
        return map;
    }

    @Override
    public void apply() throws ConfigurationException {
        AiSettings s = AiSettings.getInstance();
        s.setProvider(component.getProvider());
        s.setBaseUrl(component.getBaseUrl());
        s.setModel(component.getModel());
        s.setApiKey(component.getApiKey());
        s.setMaxTokens(component.getMaxTokens());
        s.setTimeoutSeconds(component.getTimeout());
        s.setUiLanguage(component.getUiLanguage());
        s.setAiToolDirsText(component.getAiToolDirsText());
        s.setShowAiToolDocs(component.isShowAiToolDocs());
        s.setChatAreaCollapsed(component.isChatCollapsedByDefault());
        s.setDefaultGroupMode(component.getDefaultGroupMode());
        s.setCategories(component.getLibrary());
        s.setAutoCreateLibDirs(component.isAutoCreateLibs());
        s.setAiRulesTarget(component.getRulesTarget());
        s.setRuleReadRequirement(component.isRuleRead());
        s.setRulePreferExample(component.isRuleExample());
        s.setRuleAvoidError(component.isRuleAvoid());
        s.setRuleArchiveResult(component.isRuleArchive());
        s.setRuleExtra(component.getRuleExtra());
        s.setDirMapping(component.getDirMapping());

        // 知识库：与全局默认相同则不建覆盖（保持跟随全局），不同则写入项目覆盖
        Map<String, String> sel = component.getKnowledgeSelection();
        Map<String, String> overrides = new LinkedHashMap<>();
        if (!sel.get(CategoryService.ROLE_REQ).equals(s.getLibRequirement())) {
            overrides.put(CategoryService.ROLE_REQ, sel.get(CategoryService.ROLE_REQ));
        }
        if (!sel.get(CategoryService.ROLE_EXA).equals(s.getLibExample())) {
            overrides.put(CategoryService.ROLE_EXA, sel.get(CategoryService.ROLE_EXA));
        }
        if (!sel.get(CategoryService.ROLE_RES).equals(s.getLibResult())) {
            overrides.put(CategoryService.ROLE_RES, sel.get(CategoryService.ROLE_RES));
        }
        if (!sel.get(CategoryService.ROLE_ERR).equals(s.getLibError())) {
            overrides.put(CategoryService.ROLE_ERR, sel.get(CategoryService.ROLE_ERR));
        }
        CategoryService.getInstance(project).setKnowledgeOverrides(overrides);
    }

    @Override
    public void reset() {
        if (component != null) {
            AiSettings s = AiSettings.getInstance();
            component.setProvider(s.getProvider());
            component.setBaseUrl(s.getBaseUrl());
            component.setModel(s.getModel());
            component.setApiKey(s.getApiKey());
            component.setMaxTokens(s.getMaxTokens());
            component.setTimeout(s.getTimeoutSeconds());
            component.setUiLanguage(s.getUiLanguage());
            component.setAiToolDirsText(s.getAiToolDirsText());
            component.setShowAiToolDocs(s.isShowAiToolDocs());
            component.setChatCollapsedByDefault(s.isChatAreaCollapsed());
            component.setDefaultGroupMode(s.getDefaultGroupMode());
            component.setAutoCreateLibs(s.isAutoCreateLibDirs());
            component.setRulesTarget(s.getAiRulesTarget());
            component.setRuleRead(s.isRuleReadRequirement());
            component.setRuleExample(s.isRulePreferExample());
            component.setRuleAvoid(s.isRuleAvoidError());
            component.setRuleArchive(s.isRuleArchiveResult());
            component.setRuleExtra(s.getRuleExtra());
            component.setDirMapping(s.getDirMapping());
        }
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }
}
