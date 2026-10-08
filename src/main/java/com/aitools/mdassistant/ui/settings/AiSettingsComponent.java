package com.aitools.mdassistant.ui.settings;

import com.aitools.mdassistant.ai.AiClient;
import com.aitools.mdassistant.ai.AiConfig;
import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.ai.CcSwitchImporter;
import com.aitools.mdassistant.cat.AiClassifier;
import com.aitools.mdassistant.cat.CategoryService;
import com.aitools.mdassistant.util.I18n;
import com.aitools.mdassistant.util.Notify;
import com.aitools.mdassistant.util.Ui;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI / 目录库 / 知识库 / 规则 / 映射 / 忽略列表 设置表单（项目级设置页）。 */
public class AiSettingsComponent {

    private static final String OPENAI_ITEM = I18n.t("set.providerOpenAi");
    private static final String ANTHROPIC_ITEM = I18n.t("set.providerAnthropic");

    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(AiSettingsComponent.class);

    private final Project project;
    private final JPanel panel;

    private final ComboBox<String> provider = new ComboBox<>(new javax.swing.DefaultComboBoxModel<>(
            new String[]{OPENAI_ITEM, ANTHROPIC_ITEM}));
    private final JBTextField baseUrl = new JBTextField(36);
    private final JBTextField model = new JBTextField(36);
    private final JBPasswordField apiKey = new JBPasswordField();
    private final JBTextField maxTokens = new JBTextField(8);
    private final JBTextField timeout = new JBTextField(8);
    private final JButton testBtn = new JButton(I18n.t("set.test"));
    private final JButton copyCurlBtn = new JButton(I18n.t("set.copyCurl"));
    private final JButton importCcBtn = new JButton(I18n.t("set.importCc"));
    private final JBLabel status = new JBLabel(" ");

    private final ComboBox<String> language = new ComboBox<>(new javax.swing.DefaultComboBoxModel<>(
            new String[]{I18n.t("set.langAuto"), "简体中文", "English"}));
    private final JBTextArea aiToolDirs = new JBTextArea(6, 36);
    private final JBCheckBox showAiToolDocs = new JBCheckBox();
    private final JBCheckBox chatCollapsedByDefault = new JBCheckBox();
    /** 分组顺序与工具窗口一致：按目录 / 智能分类 / 按日期 / 保持原层级 / 不分组（最后）。 */
    private final ComboBox<String> defaultGroupMode = new ComboBox<>(new javax.swing.DefaultComboBoxModel<>(
            new String[]{I18n.t("group.dir"), I18n.t("group.smart"), I18n.t("group.date"),
                    I18n.t("group.path"), I18n.t("group.none")}));

    /** 目录库（类别词表，全局）。 */
    private final DefaultTableModel libraryModel = new DefaultTableModel(
            new Object[]{I18n.t("set.catName")}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JBTable libraryTable = new JBTable(libraryModel);
    private final JButton libraryResetBtn = new JButton(I18n.t("set.libReset"));

    /** 知识库四角色（项目级，下拉项来自目录库）。 */
    private final ComboBox<String> libReq = new ComboBox<>();
    private final ComboBox<String> libExa = new ComboBox<>();
    private final ComboBox<String> libRes = new ComboBox<>();
    private final ComboBox<String> libErr = new ComboBox<>();
    private final JBCheckBox autoCreateLibs = new JBCheckBox();
    private final JButton saveGlobalBtn = new JButton(I18n.t("set.saveGlobal"));
    private final JButton useGlobalBtn = new JButton(I18n.t("set.useGlobal"));

    private final JBTextField rulesTarget = new JBTextField(20);
    private final JBCheckBox ruleRead = new JBCheckBox();
    private final JBCheckBox ruleExample = new JBCheckBox();
    private final JBCheckBox ruleAvoid = new JBCheckBox();
    private final JBCheckBox ruleArchive = new JBCheckBox();
    private final JBTextArea ruleExtra = new JBTextArea(3, 36);

    private final DefaultTableModel mappingModel = new DefaultTableModel(
            new Object[]{I18n.t("set.mapDir"), I18n.t("set.mapCat")}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JBTable mappingTable = new JBTable(mappingModel);

    /** 忽略的文档（项目级）：url 与行号平行。 */
    private final DefaultTableModel ignoreModel = new DefaultTableModel(
            new Object[]{I18n.t("set.ignorePath"), I18n.t("set.ignoreTime")}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JBTable ignoreTable = new JBTable(ignoreModel);
    private final List<String> ignoreUrls = new ArrayList<>();
    private final JButton restoreSelBtn = new JButton(I18n.t("set.restoreSel"));
    private final JButton restoreAllBtn = new JButton(I18n.t("set.restoreAll"));

    public AiSettingsComponent(@NotNull Project project) {
        this.project = project;
        provider.addActionListener(e -> updateHints());
        testBtn.addActionListener(e -> runTest());
        importCcBtn.addActionListener(e -> importCcSwitch());
        importCcBtn.setToolTipText(I18n.t("set.importCc.tip"));
        libraryResetBtn.addActionListener(e -> resetLibrary());
        saveGlobalBtn.addActionListener(e -> saveGlobalKnowledge());
        useGlobalBtn.addActionListener(e -> restoreGlobalKnowledge());
        restoreSelBtn.addActionListener(e -> restoreSelectedIgnores());
        restoreAllBtn.addActionListener(e -> restoreAllIgnores());

        mappingTable.getColumnModel().getColumn(1).setCellEditor(categoryCellEditor());
        libraryModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                refreshKnowledgeCombos(false);
            }
        });

        JPanel testRow = new JPanel(new BorderLayout(6, 0));
        JPanel testBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        testBtns.add(testBtn);
        testBtns.add(copyCurlBtn);
        testBtns.add(importCcBtn);
        copyCurlBtn.addActionListener(e -> copyCurl());
        testRow.add(testBtns, BorderLayout.WEST);
        testRow.add(status, BorderLayout.CENTER);

        JPanel knowledgeButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        knowledgeButtons.add(saveGlobalBtn);
        knowledgeButtons.add(useGlobalBtn);

        JPanel ignoreButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        ignoreButtons.add(restoreSelBtn);
        ignoreButtons.add(restoreAllBtn);

        panel = FormBuilder.createFormBuilder()
                .addComponent(sectionHeader(I18n.t("set.sectionAi")))
                .addComponent(hintLabel(I18n.t("set.hint.line1") + "<br>" + I18n.t("set.hint.line2")))
                .addLabeledComponent(I18n.t("set.provider"), provider)
                .addLabeledComponent(I18n.t("set.baseUrl"), baseUrl)
                .addLabeledComponent(I18n.t("set.model"), model)
                .addLabeledComponent(I18n.t("set.apiKey"), apiKey)
                .addLabeledComponent(I18n.t("set.maxTokens"), maxTokens)
                .addLabeledComponent(I18n.t("set.timeout"), timeout)
                .addComponent(testRow)
                .addSeparator()
                .addComponent(sectionHeader(I18n.t("set.sectionUi")))
                .addLabeledComponent(I18n.t("set.lang"), language)
                .addComponent(hintLabel(I18n.t("set.langHint")))
                .addLabeledComponent(I18n.t("set.aiDirs"),
                        com.intellij.ui.ScrollPaneFactory.createScrollPane(aiToolDirs))
                .addComponent(hintLabel(I18n.t("set.aiDirsHint")))
                .addLabeledComponent(I18n.t("set.showAiDocs"), showAiToolDocs)
                .addLabeledComponent(I18n.t("set.chatCollapsed"), chatCollapsedByDefault)
                .addLabeledComponent(I18n.t("set.groupMode"), defaultGroupMode)
                .addSeparator()
                .addComponent(sectionHeader(I18n.t("set.sectionLibs")))
                .addComponent(libraryDecorator())
                .addComponent(leftButtonRow(libraryResetBtn))
                .addSeparator()
                .addComponent(sectionHeader(I18n.t("set.sectionLib")))
                .addComponent(hintLabel(I18n.t("set.libScopeHint")))
                .addLabeledComponent(I18n.t("set.libReq"), libReq)
                .addLabeledComponent(I18n.t("set.libExa"), libExa)
                .addLabeledComponent(I18n.t("set.libRes"), libRes)
                .addLabeledComponent(I18n.t("set.libErr"), libErr)
                .addLabeledComponent(I18n.t("set.autoCreateLibs"), autoCreateLibs)
                .addComponent(knowledgeButtons)
                .addSeparator()
                .addComponent(sectionHeader(I18n.t("set.sectionRules")))
                .addLabeledComponent(I18n.t("set.rulesTarget"), rulesTarget)
                .addLabeledComponent(I18n.t("set.ruleRead"), ruleRead)
                .addLabeledComponent(I18n.t("set.ruleExample"), ruleExample)
                .addLabeledComponent(I18n.t("set.ruleAvoid"), ruleAvoid)
                .addLabeledComponent(I18n.t("set.ruleArchive"), ruleArchive)
                .addLabeledComponent(I18n.t("set.ruleExtra"),
                        com.intellij.ui.ScrollPaneFactory.createScrollPane(ruleExtra))
                .addSeparator()
                .addComponent(sectionHeader(I18n.t("set.sectionMapping")))
                .addComponent(hintLabel(I18n.t("set.mapHint")))
                .addComponent(mappingDecorator())
                .addSeparator()
                .addComponent(sectionHeader(I18n.t("set.sectionIgnore")))
                .addComponent(hintLabel(I18n.t("set.ignoreHint")))
                .addComponent(ignoreDecorator())
                .addComponent(ignoreButtons)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        updateHints();
        loadLibrary(AiSettings.getInstance().getCategories());
        loadKnowledgeCombos();
        loadIgnores();
    }

    // ==================== 目录库 ====================

    private JComponent mappingDecorator() {
        return ToolbarDecorator.createDecorator(mappingTable)
                .setAddAction(anActionButton ->
                        mappingModel.addRow(new Object[]{"docs/", ""}))
                .setRemoveAction(anActionButton -> {
                    int row = mappingTable.getSelectedRow();
                    if (row >= 0) {
                        mappingModel.removeRow(row);
                    }
                })
                .disableUpDownActions()
                .createPanel();
    }

    private JComponent libraryDecorator() {
        return ToolbarDecorator.createDecorator(libraryTable)
                .setAddAction(anActionButton -> libraryModel.addRow(new Object[]{"新类别"}))
                .setRemoveAction(anActionButton -> {
                    int row = libraryTable.getSelectedRow();
                    if (row >= 0) {
                        libraryModel.removeRow(row);
                    }
                })
                .disableUpDownActions()
                .createPanel();
    }

    private static JComponent leftButtonRow(JButton button) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 2));
        p.add(button);
        return p;
    }

    private void resetLibrary() {
        loadLibrary(AiClassifier.CATEGORIES);
    }

    private void loadLibrary(List<String> categories) {
        while (libraryModel.getRowCount() > 0) {
            libraryModel.removeRow(0);
        }
        for (String c : categories) {
            if (c != null && !c.trim().isEmpty()) {
                libraryModel.addRow(new Object[]{c.trim()});
            }
        }
    }

    public List<String> getLibrary() {
        List<String> list = new ArrayList<>();
        for (int r = 0; r < libraryModel.getRowCount(); r++) {
            Object v = libraryModel.getValueAt(r, 0);
            String s = v == null ? "" : String.valueOf(v).trim();
            if (!s.isEmpty() && !list.contains(s)) {
                list.add(s);
            }
        }
        return list;
    }

    // ==================== 知识库（项目级 + 全局默认） ====================

    /** 重建四个知识库下拉框的候选集：未启用 + 目录库 + 当前值。 */
    private void refreshKnowledgeCombos(boolean keepSelection) {
        List<String> items = new ArrayList<>();
        items.add(I18n.t("set.disabled"));
        items.addAll(getLibrary());
        for (ComboBox<String> combo : new ComboBox[]{libReq, libExa, libRes, libErr}) {
            String current = comboValue(combo);
            if (current != null && !current.isEmpty() && !items.contains(current)) {
                items.add(current);
            }
        }
        for (ComboBox<String> combo : new ComboBox[]{libReq, libExa, libRes, libErr}) {
            String current = keepSelection ? comboValue(combo) : null;
            combo.removeAllItems();
            for (String item : items) {
                combo.addItem(item);
            }
            if (current != null && items.contains(current)) {
                combo.setSelectedItem(current);
            } else {
                combo.setSelectedIndex(0);
            }
        }
    }

    private void loadKnowledgeCombos() {
        String[] dirs = CategoryService.effectiveKnowledgeDirs(project);
        libReq.removeAllItems();
        libExa.removeAllItems();
        libRes.removeAllItems();
        libErr.removeAllItems();
        // 先填充再选中（避免 TableModelListener 期间空模型）
        refreshKnowledgeCombos(false);
        selectCombo(libReq, dirs[0]);
        selectCombo(libExa, dirs[1]);
        selectCombo(libRes, dirs[2]);
        selectCombo(libErr, dirs[3]);
    }

    private void selectCombo(ComboBox<String> combo, String value) {
        if (value == null || value.isEmpty()) {
            combo.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.addItem(value);
        combo.setSelectedItem(value);
    }

    private static String comboValue(ComboBox<String> combo) {
        int i = combo.getSelectedIndex();
        if (i <= 0) {
            return "";
        }
        Object v = combo.getSelectedItem();
        return v == null ? "" : String.valueOf(v);
    }

    public Map<String, String> getKnowledgeSelection() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(CategoryService.ROLE_REQ, comboValue(libReq));
        map.put(CategoryService.ROLE_EXA, comboValue(libExa));
        map.put(CategoryService.ROLE_RES, comboValue(libRes));
        map.put(CategoryService.ROLE_ERR, comboValue(libErr));
        return map;
    }

    private void saveGlobalKnowledge() {
        AiSettings s = AiSettings.getInstance();
        Map<String, String> sel = getKnowledgeSelection();
        s.setLibRequirement(sel.get(CategoryService.ROLE_REQ));
        s.setLibExample(sel.get(CategoryService.ROLE_EXA));
        s.setLibResult(sel.get(CategoryService.ROLE_RES));
        s.setLibError(sel.get(CategoryService.ROLE_ERR));
        CategoryService.getInstance(project).setKnowledgeOverrides(new LinkedHashMap<String, String>());
        loadKnowledgeCombos();
        Notify.info(project, I18n.t("notify.globalSaved"));
    }

    private void restoreGlobalKnowledge() {
        CategoryService.getInstance(project).setKnowledgeOverrides(new LinkedHashMap<String, String>());
        loadKnowledgeCombos();
        Notify.info(project, I18n.t("notify.globalRestored"));
    }

    // ==================== 忽略列表 ====================

    private JComponent ignoreDecorator() {
        return ToolbarDecorator.createDecorator(ignoreTable)
                .disableAddAction()
                .disableRemoveAction()
                .disableUpDownActions()
                .createPanel();
    }

    private void loadIgnores() {
        while (ignoreModel.getRowCount() > 0) {
            ignoreModel.removeRow(0);
        }
        ignoreUrls.clear();
        String baseUrlStr = project.getBaseDir() == null ? null : project.getBaseDir().getUrl() + "/";
        for (Map.Entry<String, String> e : CategoryService.getInstance(project).getIgnored().entrySet()) {
            String display = e.getKey();
            if (baseUrlStr != null && display.startsWith(baseUrlStr)) {
                display = display.substring(baseUrlStr.length());
            }
            ignoreUrls.add(e.getKey());
            ignoreModel.addRow(new Object[]{display, e.getValue()});
        }
    }

    private void restoreSelectedIgnores() {
        int[] rows = ignoreTable.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        CategoryService cs = CategoryService.getInstance(project);
        for (int i = rows.length - 1; i >= 0; i--) {
            int row = rows[i];
            if (row >= 0 && row < ignoreUrls.size()) {
                cs.unignore(ignoreUrls.get(row));
                ignoreModel.removeRow(row);
                ignoreUrls.remove(row);
            }
        }
    }

    private void restoreAllIgnores() {
        CategoryService.getInstance(project).clearIgnored();
        loadIgnores();
    }

    // ==================== 通用 ====================

    private javax.swing.DefaultCellEditor categoryCellEditor() {
        // 词表与 AI 分类保持一致：目录库 + 本项目生效的知识库目录
        List<String> vocab = AiClassifier.allCategories(project);
        ComboBox<String> combo = new ComboBox<>(vocab.toArray(new String[0]));
        combo.setEditable(true);
        return new javax.swing.DefaultCellEditor(combo);
    }

    private static JLabel sectionHeader(String text) {
        JBLabel l = new JBLabel(text);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD));
        l.setBorder(com.intellij.util.ui.JBUI.Borders.emptyTop(6));
        return l;
    }

    private static JLabel hintLabel(String htmlOrText) {
        JBLabel l = new JBLabel(htmlOrText.startsWith("<html>") ? htmlOrText
                : "<html><body style='width:560px'>" + htmlOrText + "</body></html>");
        l.setForeground(new Color(0x8A8A8A));
        l.setVerticalAlignment(SwingConstants.TOP);
        return l;
    }

    private void updateHints() {
        if (isAnthropic()) {
            baseUrl.getEmptyText().setText("https://api.anthropic.com");
            model.getEmptyText().setText("claude-sonnet-4-5");
        } else {
            baseUrl.getEmptyText().setText("https://api.openai.com");
            model.getEmptyText().setText("gpt-4o-mini / deepseek-chat / glm-4.6");
        }
    }

    private void runTest() {
        final AiConfig cfg = new AiConfig(getProvider(), getBaseUrl(), getModel(), getApiKey(),
                getMaxTokens(), Math.min(getTimeout(), 30));
        if (!cfg.isComplete()) {
            setStatus(I18n.t("set.testFill"), false);
            return;
        }
        // EDT 上只给即时反馈；describeTarget 含代理解析（PAC 模式下会阻塞），
        // 必须放到后台线程，否则 UI 冻结时连 40s 兜底 Timer（也在 EDT）都无法触发。
        setStatus(I18n.t("set.testing"), true);

        // 超时兜底用 Swing Timer（EDT 触发，不依赖线程池/嵌套任务，保证必定反馈）
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        final javax.swing.Timer watchdog = new javax.swing.Timer(40_000, e -> {
            if (finished.compareAndSet(false, true)) {
                setStatus(I18n.f("set.testTimeout", 40, AiClient.describeTarget(cfg))
                        + (isAnthropicOfficial(cfg) ? "  " + I18n.t("set.hintAnthropic") : ""), false);
            }
        });
        watchdog.setRepeats(false);
        watchdog.start();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final String target = AiClient.describeTarget(cfg);
            if (!finished.get()) {
                setStatus(I18n.f("set.testingUrl", target), true);
            }
            try {
                String reply = AiClient.test(cfg);
                LOG.info("AI test ok: " + target);
                if (finished.compareAndSet(false, true)) {
                    watchdog.stop();
                    setStatus(I18n.f("set.testOk", reply), true);
                }
            } catch (Throwable t) {
                LOG.warn("AI test failed: " + target, t);
                if (finished.compareAndSet(false, true)) {
                    watchdog.stop();
                    setStatus(I18n.f("set.testFail",
                            diagnose(t) + (isAnthropicOfficial(cfg) ? "  " + I18n.t("set.hintAnthropic") : "")
                                    + "  " + target), false);
                }
            }
        });
    }

    private static boolean isAnthropicOfficial(AiConfig cfg) {
        return cfg.isAnthropic() && cfg.effectiveBaseUrl().contains("api.anthropic.com");
    }

    /** 把常见失败归类为可操作的提示。 */
    private static String diagnose(Throwable t) {
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        if (t instanceof java.net.UnknownHostException) {
            return msg + " " + I18n.t("set.hintDns");
        }
        if (msg.contains("401") || msg.contains("403")) {
            return msg + " " + I18n.t("set.hint401");
        }
        if (msg.contains("404")) {
            return msg + " " + I18n.t("set.hint404");
        }
        if (msg.contains("407")) {
            return msg + " " + I18n.t("set.hint407");
        }
        if (msg.toLowerCase().contains("timed out") || msg.toLowerCase().contains("timeout")
                || msg.contains("Connect ")
                || t instanceof java.net.SocketTimeoutException
                || t instanceof java.net.ConnectException) {
            return msg + " " + I18n.t("set.hintTimeout");
        }
        return msg;
    }

    /** 复制等价 cURL：在终端运行即可分辨「插件问题」与「网络/配置问题」。 */
    private void copyCurl() {
        AiConfig cfg = new AiConfig(getProvider(), getBaseUrl(), getModel(), getApiKey(),
                getMaxTokens(), Math.min(getTimeout(), 30));
        if (!cfg.isComplete()) {
            setStatus(I18n.t("set.testFill"), false);
            return;
        }
        com.intellij.openapi.ide.CopyPasteManager.getInstance()
                .setContents(new java.awt.datatransfer.StringSelection(AiClient.toCurl(cfg)));
        setStatus(I18n.t("set.curlCopied"), true);
    }

    // ==================== 导入 cc-switch 配置 ====================

    /**
     * 发现 cc-switch / Claude Code / Codex 中的服务商配置，选择后填入表单（不立即保存，仍需 Apply/OK）。
     * 自动发现为空时允许手动选择 cc-switch 的 config.json。
     */
    private void importCcSwitch() {
        setStatus(I18n.t("cc.scanning"), true);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<CcSwitchImporter.Candidate> found = CcSwitchImporter.discover();
            Ui.invokeLater(() -> {
                if (found.isEmpty()) {
                    setStatus(" ", false);
                    importFromPickedFile();
                    return;
                }
                setStatus(" ", false);
                chooseAndApply(found);
            });
        });
    }

    private void chooseAndApply(List<CcSwitchImporter.Candidate> found) {
        CcSwitchChooserDialog dialog = new CcSwitchChooserDialog(found);
        if (!dialog.showAndGet()) {
            return;
        }
        CcSwitchImporter.Candidate c = dialog.getSelected();
        if (c == null) {
            return;
        }
        setProvider(c.provider);
        if (!c.baseUrl.isEmpty()) {
            setBaseUrl(c.baseUrl);
        }
        if (!c.model.isEmpty()) {
            setModel(c.model);
        }
        if (!c.apiKey.isEmpty()) {
            setApiKey(c.apiKey);
        }
        setStatus(I18n.f("cc.imported", c.name), true);
    }

    /** 自动发现为空时，让用户手动挑选一个 cc-switch config.json。 */
    private void importFromPickedFile() {
        int pick = com.intellij.openapi.ui.Messages.showYesNoDialog(panel,
                I18n.t("cc.none"), I18n.t("set.importCc"), com.intellij.openapi.ui.Messages.getQuestionIcon());
        if (pick != com.intellij.openapi.ui.Messages.YES) {
            return;
        }
        com.intellij.openapi.fileChooser.FileChooserDescriptor desc =
                new com.intellij.openapi.fileChooser.FileChooserDescriptor(true, false, false, false, false, false);
        desc.setTitle(I18n.t("cc.pickFile"));
        desc.withFileFilter(vf -> "json".equalsIgnoreCase(vf.getExtension()));
        com.intellij.openapi.vfs.VirtualFile vf =
                com.intellij.openapi.fileChooser.FileChooser.chooseFile(desc, project, null);
        if (vf == null) {
            return;
        }
        List<CcSwitchImporter.Candidate> parsed = new ArrayList<>();
        try {
            CcSwitchImporter.parseFile(new java.io.File(vf.getPath()), parsed);
        } catch (Exception e) {
            setStatus(I18n.f("cc.readFail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), false);
            return;
        }
        if (parsed.isEmpty()) {
            setStatus(I18n.t("cc.noneInFile"), false);
            return;
        }
        chooseAndApply(parsed);
    }

    private void setStatus(final String text, final boolean ok) {
        Ui.invokeLater(() -> {
            status.setText(text);
            status.setToolTipText(text); // 文本过长被截断时悬停可看全文
            status.setForeground(ok ? new Color(0x0E7A46) : new Color(0xC0392B));
        });
    }

    public boolean isAnthropic() {
        return ANTHROPIC_ITEM.equals(provider.getSelectedItem());
    }

    @NotNull
    public JComponent getPanel() {
        return panel;
    }

    public String getProvider() {
        return isAnthropic() ? AiConfig.PROVIDER_ANTHROPIC : AiConfig.PROVIDER_OPENAI;
    }

    public void setProvider(String p) {
        provider.setSelectedItem(AiConfig.PROVIDER_ANTHROPIC.equals(p) ? ANTHROPIC_ITEM : OPENAI_ITEM);
    }

    public String getBaseUrl() {
        return baseUrl.getText().trim();
    }

    public void setBaseUrl(String v) {
        baseUrl.setText(v == null ? "" : v);
    }

    public String getModel() {
        return model.getText().trim();
    }

    public void setModel(String v) {
        model.setText(v == null ? "" : v);
    }

    public String getApiKey() {
        return new String(apiKey.getPassword());
    }

    public void setApiKey(String v) {
        apiKey.setText(v == null ? "" : v);
    }

    public int getMaxTokens() {
        try {
            return Integer.parseInt(maxTokens.getText().trim());
        } catch (NumberFormatException e) {
            return 2048;
        }
    }

    public void setMaxTokens(int v) {
        maxTokens.setText(String.valueOf(v <= 0 ? 2048 : v));
    }

    public int getTimeout() {
        try {
            return Integer.parseInt(timeout.getText().trim());
        } catch (NumberFormatException e) {
            return 120;
        }
    }

    public void setTimeout(int v) {
        timeout.setText(String.valueOf(v <= 0 ? 120 : v));
    }

    public String getUiLanguage() {
        int i = language.getSelectedIndex();
        return i == 1 ? I18n.LANG_ZH : i == 2 ? I18n.LANG_EN : I18n.LANG_AUTO;
    }

    public void setUiLanguage(String v) {
        language.setSelectedIndex(I18n.LANG_ZH.equals(v) ? 1 : I18n.LANG_EN.equals(v) ? 2 : 0);
    }

    public String getAiToolDirsText() {
        return aiToolDirs.getText();
    }

    public void setAiToolDirsText(String v) {
        aiToolDirs.setText(v == null ? "" : v);
    }

    public boolean isShowAiToolDocs() {
        return showAiToolDocs.isSelected();
    }

    public void setShowAiToolDocs(boolean v) {
        showAiToolDocs.setSelected(v);
    }

    public boolean isChatCollapsedByDefault() {
        return chatCollapsedByDefault.isSelected();
    }

    public void setChatCollapsedByDefault(boolean v) {
        chatCollapsedByDefault.setSelected(v);
    }

    public int getDefaultGroupMode() {
        return defaultGroupMode.getSelectedIndex();
    }

    public void setDefaultGroupMode(int v) {
        defaultGroupMode.setSelectedIndex(
                v >= 0 && v < defaultGroupMode.getItemCount() ? v : 0);
    }

    public boolean isAutoCreateLibs() {
        return autoCreateLibs.isSelected();
    }

    public void setAutoCreateLibs(boolean v) {
        autoCreateLibs.setSelected(v);
    }

    public String getRulesTarget() {
        return rulesTarget.getText().trim();
    }

    public void setRulesTarget(String v) {
        rulesTarget.setText(v == null ? "" : v);
    }

    public boolean isRuleRead() {
        return ruleRead.isSelected();
    }

    public void setRuleRead(boolean v) {
        ruleRead.setSelected(v);
    }

    public boolean isRuleExample() {
        return ruleExample.isSelected();
    }

    public void setRuleExample(boolean v) {
        ruleExample.setSelected(v);
    }

    public boolean isRuleAvoid() {
        return ruleAvoid.isSelected();
    }

    public void setRuleAvoid(boolean v) {
        ruleAvoid.setSelected(v);
    }

    public boolean isRuleArchive() {
        return ruleArchive.isSelected();
    }

    public void setRuleArchive(boolean v) {
        ruleArchive.setSelected(v);
    }

    public String getRuleExtra() {
        return ruleExtra.getText();
    }

    public void setRuleExtra(String v) {
        ruleExtra.setText(v == null ? "" : v);
    }

    public Map<String, String> getDirMapping() {
        Map<String, String> map = new LinkedHashMap<>();
        for (int r = 0; r < mappingModel.getRowCount(); r++) {
            Object k = mappingModel.getValueAt(r, 0);
            Object v = mappingModel.getValueAt(r, 1);
            String key = k == null ? "" : String.valueOf(k).trim();
            String val = v == null ? "" : String.valueOf(v).trim();
            if (!key.isEmpty() && !val.isEmpty()) {
                map.put(key, val);
            }
        }
        return map;
    }

    public void setDirMapping(Map<String, String> mapping) {
        while (mappingModel.getRowCount() > 0) {
            mappingModel.removeRow(0);
        }
        if (mapping != null) {
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                mappingModel.addRow(new Object[]{e.getKey(), e.getValue()});
            }
        }
    }
}
