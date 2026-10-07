package com.aitools.mdassistant.ui;

import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.cat.AiClassifier;
import com.aitools.mdassistant.cat.AiRulesGenerator;
import com.aitools.mdassistant.cat.CategoryService;
import com.aitools.mdassistant.cat.RuleClassifier;
import com.aitools.mdassistant.model.MdFile;
import com.aitools.mdassistant.scan.MdScanner;
import com.aitools.mdassistant.ui.settings.AiConfigurable;
import com.aitools.mdassistant.util.I18n;
import com.aitools.mdassistant.util.MdText;
import com.aitools.mdassistant.util.Notify;
import com.aitools.mdassistant.util.Ui;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 左侧工具窗口主面板：上半部分为文档树，下半部分为 AI 对话。 */
public class MdToolWindowPanel extends SimpleToolWindowPanel {

    private static final String GROUP_DIR = "group.dir";
    private static final String GROUP_SMART = "group.smart";
    private static final String GROUP_NONE = "group.none";
    private static final String GROUP_DATE = "group.date";
    public static final String AI_TOOL_GROUP_KEY = "group.aiTool";

    private final Project project;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final ComboBox<String> groupModeCombo;
    private final List<MdFile> files = new ArrayList<>();
    private final ChatPanel chatPanel;
    private OnePixelSplitter splitter;

    public MdToolWindowPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;

        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new MdTreeCellRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        new TreeSpeedSearch(tree);
        tree.addMouseListener(new TreeMouseListener());

        groupModeCombo = new ComboBox<>(new javax.swing.DefaultComboBoxModel<>(
                new String[]{I18n.t(GROUP_DIR), I18n.t(GROUP_SMART), I18n.t(GROUP_NONE), I18n.t(GROUP_DATE)}));
        int defaultMode = AiSettings.getInstance().getDefaultGroupMode();
        groupModeCombo.setSelectedIndex(defaultMode >= 0 && defaultMode <= 3 ? defaultMode : 0);
        groupModeCombo.addActionListener(e -> rebuildTree());

        JPanel treePanel = new JPanel(new BorderLayout());
        JPanel comboBar = new JPanel(new BorderLayout(6, 0));
        comboBar.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        JLabel modeLabel = new JLabel(I18n.t("group.label"));
        modeLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        comboBar.add(modeLabel, BorderLayout.WEST);
        comboBar.add(groupModeCombo, BorderLayout.CENTER);
        treePanel.add(comboBar, BorderLayout.NORTH);
        treePanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);

        chatPanel = new ChatPanel(project, this::getFiles, this::rebuildTree, this::onChatAreaToggle);

        splitter = new OnePixelSplitter(true, "MdAssistant.Split.Proportion", 0.6f);
        splitter.setFirstComponent(treePanel);
        splitter.setSecondComponent(chatPanel);
        chatPanel.setMinimumSize(new java.awt.Dimension(0, 150));
        setContent(splitter);
        setToolbar(createToolbar().getComponent());
        if (AiSettings.getInstance().isChatAreaCollapsed()) {
            Ui.invokeLater(this::applyCollapsedProportion);
        }
        // 窗口尺寸变化时，折叠态需重新计算比例，保证提问输入区完整可见
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (chatPanel.isChatAreaCollapsed()) {
                    applyCollapsedProportion();
                }
            }
        });
    }

    /** 对话区折叠时按实际高度动态分配，保证提问区约 178px 完整显示；展开恢复默认比例。 */
    private void onChatAreaToggle(boolean collapsed) {
        if (splitter == null) {
            return;
        }
        if (collapsed) {
            applyCollapsedProportion();
        } else {
            splitter.setProportion(0.6f);
        }
    }

    private void applyCollapsedProportion() {
        if (splitter == null) {
            return;
        }
        int total = getHeight();
        if (total <= 0) {
            splitter.setProportion(0.85f);
            return;
        }
        float proportion = (total - 178) / (float) total;
        proportion = Math.min(0.95f, Math.max(0.5f, proportion));
        splitter.setProportion(proportion);
    }

    @NotNull
    public List<MdFile> getFiles() {
        return files;
    }

    public void refresh() {
        new Task.Backgroundable(project, I18n.t("task.scan"), true) {
            private List<MdFile> scanned;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AiSettings settings = AiSettings.getInstance();
                MdScanner.ScanResult r = MdScanner.scan(project, indicator,
                        settings.getAiToolDirs(), settings.isShowAiToolDocs());
                Set<String> ignoredUrls = CategoryService.getInstance(project).getIgnored().keySet();
                r.files.removeIf(vf -> ignoredUrls.contains(vf.getUrl()));
                VirtualFile base = project.getBaseDir();
                List<MdFile> result = new ArrayList<>(r.files.size());
                for (VirtualFile vf : r.files) {
                    String rel = VfsUtilCore.getRelativePath(vf, base, '/');
                    result.add(new MdFile(vf, rel == null ? vf.getPath() : rel, r.aiToolFiles.contains(vf)));
                }
                result.sort(Comparator.comparing(MdFile::getRelativePath, String.CASE_INSENSITIVE_ORDER));
                scanned = result;
            }

            @Override
            public void onSuccess() {
                files.clear();
                files.addAll(scanned);
                rebuildTree();
                chatPanel.refreshContexts();
                if (files.size() >= MdScanner.MAX_FILES) {
                    Notify.warn(project, I18n.f("notify.cap", MdScanner.MAX_FILES));
                } else if (files.isEmpty()) {
                    Notify.info(project, I18n.t("notify.noMd"));
                }
            }
        }.queue();
    }

    private ActionToolbar createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        AnAction refreshAction = new AnAction() {
            {
                getTemplatePresentation().setText(I18n.t("tb.refresh"));
                getTemplatePresentation().setDescription(I18n.t("tb.refresh.desc"));
                getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refresh();
            }
        };

        DefaultActionGroup classifyGroup = new DefaultActionGroup(I18n.t("tb.classify"), true);
        classifyGroup.getTemplatePresentation().setDescription(I18n.t("tb.classify.desc"));
        classifyGroup.getTemplatePresentation().setIcon(AllIcons.Actions.IntentionBulb);
        classifyGroup.addAction(action(I18n.t("classify.rule"), null, this::classifyByRuleAll));
        classifyGroup.addAction(action(I18n.t("classify.ai"), null, this::classifyAll));

        AnAction aiToolToggle = new ToggleAction() {
            {
                getTemplatePresentation().setText(I18n.t("tb.showAiDocs"));
                getTemplatePresentation().setDescription(I18n.t("tb.showAiDocs.desc"));
                getTemplatePresentation().setIcon(AllIcons.General.Filter);
            }

            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return AiSettings.getInstance().isShowAiToolDocs();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                AiSettings.getInstance().setShowAiToolDocs(state);
                refresh();
            }
        };

        AnAction rulesAction = new AnAction() {
            {
                getTemplatePresentation().setText(I18n.t("tb.syncRules"));
                getTemplatePresentation().setDescription(I18n.t("tb.syncRules.desc"));
                getTemplatePresentation().setIcon(AllIcons.Actions.Commit);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                syncAiRules();
            }
        };

        AnAction settingsAction = new AnAction() {
            {
                getTemplatePresentation().setText(I18n.t("tb.settings"));
                getTemplatePresentation().setDescription(I18n.t("tb.settings.desc"));
                getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AiConfigurable.class);
            }
        };

        group.add(refreshAction);
        group.add(classifyGroup);
        group.add(aiToolToggle);
        group.add(rulesAction);
        group.add(settingsAction);
        group.add(new com.aitools.mdassistant.AboutAction());

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("EasyMDAssistant Toolbar", group, true);
        toolbar.setTargetComponent(this);
        return toolbar;
    }

    // ==================== AI 规则同步 ====================

    private void syncAiRules() {
        new Task.Backgroundable(project, I18n.t("task.syncRules"), true) {
            private VirtualFile target;
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    target = AiRulesGenerator.generate(project, indicator);
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
            }

            @Override
            public void onSuccess() {
                if (target != null) {
                    Notify.info(project, I18n.f("notify.rulesSynced", target.getName()));
                    FileEditorManager.getInstance(project).openFile(target, true);
                    refresh();
                } else {
                    Notify.error(project, I18n.f("notify.rulesFail", error));
                }
            }
        }.queue();
    }

    // ==================== 分类 ====================

    /** 文档的最终分类：手动文件夹映射 > 知识库目录 > AI/历史分类 > 内置规则 > 未分类（AI 工具文档固定成组）。 */
    @Nullable
    private String effectiveCategory(@NotNull MdFile f) {
        AiSettings s = AiSettings.getInstance();
        String manual = CategoryService.manualCategory(f.getRelativePath(), s.getDirMapping());
        if (manual != null && !manual.isEmpty()) {
            return manual;
        }
        String lib = libraryCategoryOf(f.getRelativePath());
        if (lib != null) {
            return lib;
        }
        if (f.isFromAiToolDir()) {
            return I18n.t(AI_TOOL_GROUP_KEY);
        }
        String stored = CategoryService.getInstance(project).categoryOf(f.getFile());
        if (stored != null) {
            return stored;
        }
        String content = MdText.read(f.getFile());
        String title = MdText.title(content);
        if (title == null || title.isEmpty()) {
            title = f.getTitle();
        }
        String rule = RuleClassifier.classify(f.getRelativePath(), title, MdText.excerpt(content, 500));
        if (rule != null && !AiClassifier.allCategories(project).contains(rule)) {
            rule = null; // 规则命中的类别已被用户从目录库移除
        }
        return rule != null ? rule : AiClassifier.UNCLASSIFIED;
    }

    /** 相对路径位于四个知识库目录之下时，返回对应目录名作为分组。 */
    @Nullable
    private String libraryCategoryOf(@NotNull String relPath) {
        for (String lib : libraryOrder()) {
            if (relPath.equals(lib) || relPath.startsWith(lib + "/")) {
                return lib;
            }
        }
        return null;
    }

    /** 知识库分组展示顺序：需求库、示例库、成果库、错误库（本项目生效配置）。 */
    private List<String> libraryOrder() {
        Set<String> seen = new LinkedHashSet<>();
        for (String lib : CategoryService.effectiveKnowledgeDirs(project)) {
            if (!lib.isEmpty()) {
                seen.add(lib);
            }
        }
        return new ArrayList<>(seen);
    }

    private void classifyByRuleAll() {
        selectSmartMode();
        rebuildTree();
        Notify.info(project, I18n.t("notify.ruleDone"));
    }

    private void ruleClassifySingle(@NotNull MdFile f) {
        selectSmartMode();
        rebuildTree();
        String content = MdText.read(f.getFile());
        String title = MdText.title(content);
        if (title == null || title.isEmpty()) {
            title = f.getTitle();
        }
        String cat = RuleClassifier.classify(f.getRelativePath(), title, MdText.excerpt(content, 500));
        Notify.info(project, cat == null
                ? I18n.f("notify.ruleOne.miss", f.getName())
                : I18n.f("notify.ruleOne.hit", f.getName(), cat));
    }

    private void selectSmartMode() {
        String smart = I18n.t(GROUP_SMART);
        for (int i = 0; i < groupModeCombo.getItemCount(); i++) {
            if (smart.equals(groupModeCombo.getItemAt(i))) {
                groupModeCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private boolean isSmartMode() {
        return I18n.t(GROUP_SMART).equals(groupModeCombo.getSelectedItem());
    }

    private boolean isDirMode() {
        return I18n.t(GROUP_DIR).equals(groupModeCombo.getSelectedItem());
    }

    private boolean isDateMode() {
        return I18n.t(GROUP_DATE).equals(groupModeCombo.getSelectedItem());
    }

    private void classifyAll() {
        List<MdFile> targets = new ArrayList<>();
        for (MdFile f : files) {
            if (!f.isFromAiToolDir()) {
                targets.add(f);
            }
        }
        if (targets.isEmpty()) {
            Notify.info(project, I18n.t("notify.nothingToClassify"));
            return;
        }
        classifyFiles(targets);
    }

    private void classifySingle(@NotNull MdFile file) {
        classifyFiles(new ArrayList<>(java.util.Collections.singletonList(file)));
    }

    private void classifyFiles(@NotNull final List<MdFile> targets) {
        new Task.Backgroundable(project, I18n.t("task.classify"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                AiClassifier.classify(project, targets, indicator, new AiClassifier.Listener() {
                    @Override
                    public void onProgress(int done, int total) {
                        Ui.invokeLater(MdToolWindowPanel.this::rebuildTree);
                    }

                    @Override
                    public void onFinished(boolean cancelled, boolean error, String message) {
                        if (message == null) {
                            return;
                        }
                        Ui.invokeLater(() -> {
                            if (error) {
                                Notify.error(project, I18n.f("notify.classifyFail", message));
                            } else {
                                Notify.warn(project, message);
                            }
                        });
                    }
                });
            }

            @Override
            public void onSuccess() {
                selectSmartMode();
                rebuildTree();
            }
        }.queue();
    }

    // ==================== 忽略 / 移动归档 ====================

    private void ignoreFile(@NotNull MdFile f) {
        CategoryService.getInstance(project).ignore(f.getFile());
        String url = f.getFile().getUrl();
        files.removeIf(x -> x.getFile().getUrl().equals(url));
        rebuildTree();
        chatPanel.refreshContexts();
        Notify.info(project, I18n.f("notify.ignored", f.getName()));
    }

    /** 分类到指定目录：将文档物理移动到项目根目录下对应文件夹（目录库中的类别）。 */
    private void moveToCategory(@NotNull MdFile f, @NotNull String category) {
        if (f.getRelativePath().equals(category + "/" + f.getName())) {
            Notify.info(project, I18n.f("notify.alreadyThere", f.getName()));
            return;
        }
        final String target = category;
        FileDocumentManager.getInstance().saveAllDocuments();
        final VirtualFile file = f.getFile();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile base = project.getBaseDir();
                if (base == null || !file.isValid()) {
                    return;
                }
                VirtualFile dir = base.findChild(target);
                if (dir == null || !dir.isValid()) {
                    dir = base.createChildDirectory(MdToolWindowPanel.this, target);
                }
                // 2021.1 的 move 仅支持 (requestor, destDir)；同名冲突时先重命名
                if (dir.findChild(file.getName()) != null) {
                    file.rename(MdToolWindowPanel.this,
                            file.getNameWithoutExtension() + "-" + System.currentTimeMillis() + ".md");
                }
                file.move(MdToolWindowPanel.this, dir);
                CategoryService.getInstance(project).store(file, target);
            } catch (IOException e) {
                Notify.error(project, I18n.f("notify.moveFail", e.getMessage()));
                return;
            }
            Ui.invokeLater(() -> {
                Notify.info(project, I18n.f("notify.moved", target, file.getName()));
                refresh();
            });
        });
    }

    // ==================== 树 ====================

    void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        if (groupModeCombo.getSelectedItem() == null || isDirMode()) {
            Map<String, List<MdFile>> byDir = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (MdFile f : files) {
                String rel = f.getRelativePath();
                int slash = rel.indexOf('/');
                String top = slash > 0 ? rel.substring(0, slash) : I18n.t("root.dir");
                byDir.computeIfAbsent(top, k -> new ArrayList<>()).add(f);
            }
            appendGroups(root, byDir);
        } else if (isSmartMode()) {
            Map<String, List<MdFile>> grouped = new LinkedHashMap<>();
            Map<String, List<MdFile>> fallback = new TreeMap<>();
            List<String> vocab = AiClassifier.allCategories(project);
            for (MdFile f : files) {
                String cat = effectiveCategory(f);
                if (cat == null || cat.isEmpty()) {
                    cat = AiClassifier.UNCLASSIFIED;
                }
                if (cat.equals(I18n.t(AI_TOOL_GROUP_KEY)) || vocab.contains(cat)) {
                    grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(f);
                } else {
                    // 手动映射产生的自定义类别，按字典序排在固定类别之后
                    fallback.computeIfAbsent(cat, k -> new ArrayList<>()).add(f);
                }
            }
            Map<String, List<MdFile>> ordered = new LinkedHashMap<>();
            if (grouped.containsKey(I18n.t(AI_TOOL_GROUP_KEY))) {
                ordered.put(I18n.t(AI_TOOL_GROUP_KEY), grouped.remove(I18n.t(AI_TOOL_GROUP_KEY)));
            }
            for (String lib : libraryOrder()) {
                if (grouped.containsKey(lib)) {
                    ordered.put(lib, grouped.remove(lib));
                }
            }
            for (String cat : vocab) {
                if (grouped.containsKey(cat)) {
                    ordered.put(cat, grouped.remove(cat));
                }
            }
            ordered.putAll(grouped);
            ordered.putAll(fallback);
            appendGroups(root, ordered);
        } else if (isDateMode()) {
            // 按修改日期分组：最新日期组在最上，组内按时间倒序
            Map<String, List<MdFile>> byDate = new TreeMap<>(Comparator.reverseOrder());
            for (MdFile f : files) {
                byDate.computeIfAbsent(f.getDateKey(), k -> new ArrayList<>()).add(f);
            }
            for (List<MdFile> group : byDate.values()) {
                group.sort(Comparator.comparingLong(MdFile::getTimeStamp).reversed());
            }
            appendGroups(root, byDate);
        } else {
            for (MdFile f : files) {
                root.add(new DefaultMutableTreeNode(f));
            }
        }
        treeModel.setRoot(root);
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void appendGroups(DefaultMutableTreeNode root, Map<String, List<MdFile>> grouped) {
        for (Map.Entry<String, List<MdFile>> e : grouped.entrySet()) {
            DefaultMutableTreeNode cat = new DefaultMutableTreeNode(e.getKey());
            for (MdFile f : e.getValue()) {
                cat.add(new DefaultMutableTreeNode(f));
            }
            root.add(cat);
        }
    }

    @Nullable
    private MdFile selectedFile() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node != null && node.getUserObject() instanceof MdFile) {
            return (MdFile) node.getUserObject();
        }
        return null;
    }

    /** 多选：返回当前选中的全部文档节点。 */
    @NotNull
    private List<MdFile> selectedFiles() {
        List<MdFile> result = new ArrayList<>();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            for (TreePath p : paths) {
                Object last = p.getLastPathComponent();
                if (last instanceof DefaultMutableTreeNode) {
                    Object o = ((DefaultMutableTreeNode) last).getUserObject();
                    if (o instanceof MdFile) {
                        result.add((MdFile) o);
                    }
                }
            }
        }
        return result;
    }

    /**
     * ZIP 条目名跟随当前分组模式：智能分类 →「类别/文件名」；不分组 → 平铺文件名；
     * 按目录分组 → 保留项目内相对路径。同名冲突时追加 -N 序号。
     */
    private String entryNameFor(MdFile f, Set<String> used) {
        String entry;
        if (isSmartMode()) {
            String cat = effectiveCategory(f);
            if (cat == null || cat.isEmpty()) {
                cat = AiClassifier.UNCLASSIFIED;
            }
            cat = cat.replaceAll("[\\\\/:*?\"<>|]", "_");
            entry = cat + "/" + f.getName();
        } else if (isDirMode()) {
            entry = f.getRelativePath().replace('\\', '/');
        } else {
            entry = f.getName();
        }
        if (used.add(entry)) {
            return entry;
        }
        int dot = entry.lastIndexOf('.');
        int slash = entry.lastIndexOf('/');
        String base = dot > slash ? entry.substring(0, dot) : entry;
        String ext = dot > slash ? entry.substring(dot) : "";
        int n = 2;
        String candidate;
        do {
            candidate = base + "-" + n + ext;
            n++;
        } while (!used.add(candidate));
        return candidate;
    }

    /** 把选中的多个文档打包为 ZIP（结构跟随当前分组模式）。 */
    private void zipSelected() {
        final List<MdFile> targets = selectedFiles();
        if (targets.isEmpty()) {
            Notify.info(project, I18n.t("notify.nothingToClassify"));
            return;
        }
        FileSaverDescriptor desc = new FileSaverDescriptor(
                I18n.t("zip.saveTitle"), I18n.t("zip.saveDesc"), "zip");
        String defaultName = "md-pack-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".zip";
        FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(desc, project);
        com.intellij.openapi.vfs.VirtualFileWrapper target = dialog.save(project.getBaseDir(), defaultName);
        if (target == null) {
            return;
        }
        final String outPath = target.getFile().getPath();
        final int count = targets.size();
        new Task.Backgroundable(project, I18n.t("zip.task"), true) {
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Set<String> usedEntries = new LinkedHashSet<>();
                try (OutputStream os = new FileOutputStream(outPath);
                     ZipOutputStream zip = new ZipOutputStream(os)) {
                    for (MdFile f : targets) {
                        indicator.checkCanceled();
                        byte[] bytes = ApplicationManager.getApplication().runReadAction(
                                (com.intellij.openapi.util.Computable<byte[]>) () -> {
                                    try {
                                        return VfsUtil.loadBytes(f.getFile());
                                    } catch (IOException e) {
                                        return null;
                                    }
                                });
                        if (bytes == null) {
                            continue;
                        }
                        zip.putNextEntry(new ZipEntry(entryNameFor(f, usedEntries)));
                        zip.write(bytes);
                        zip.closeEntry();
                    }
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
            }

            @Override
            public void onSuccess() {
                if (error != null) {
                    Notify.error(project, I18n.f("zip.fail", error));
                    return;
                }
                Notify.info(project, I18n.f("zip.done", count, outPath));
                RevealFileAction.openFile(new java.io.File(outPath));
            }
        }.queue();
    }

    private void openFile(@NotNull MdFile f) {
        FileEditorManager.getInstance(project).openFile(f.getFile(), true);
    }

    private final class TreeMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                MdFile f = selectedFile();
                if (f != null) {
                    openFile(f);
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            maybePopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybePopup(e);
        }

        private void maybePopup(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return;
            }
            int row = tree.getRowForLocation(e.getX(), e.getY());
            if (row < 0) {
                return;
            }
            // 右键点在已选中的多选集合内时保留原选择，否则仅选中当前行
            if (!tree.isRowSelected(row)) {
                tree.setSelectionRow(row);
            }
            MdFile f = selectedFile();
            if (f == null) {
                return;
            }
            ActionPopupMenu menu = ActionManager.getInstance()
                    .createActionPopupMenu("EasyMDAssistant Tree", buildFileActions(f));
            menu.getComponent().show(tree, e.getX(), e.getY());
        }
    }

    private DefaultActionGroup buildFileActions(@NotNull final MdFile f) {
        DefaultActionGroup g = new DefaultActionGroup();
        g.addAction(action(I18n.t("tree.open"), AllIcons.Actions.EditSource, () -> openFile(f)));
        g.addSeparator();
        DefaultActionGroup classify = new DefaultActionGroup(I18n.t("tree.category"), true);
        classify.getTemplatePresentation().setIcon(AllIcons.Actions.IntentionBulb);
        classify.addAction(action(I18n.t("tree.ruleThis"), null, () -> ruleClassifySingle(f)));
        classify.addAction(action(I18n.t("tree.aiThis"), null, () -> classifySingle(f)));
        classify.addSeparator();
        // 目录直接平铺在二级菜单下，点击即移动到对应目录（不弹对话框）
        for (final String cat : AiClassifier.allCategories(project)) {
            classify.addAction(action(cat, AllIcons.Nodes.Folder, () -> moveToCategory(f, cat)));
        }
        g.add(classify);
        DefaultActionGroup ops = new DefaultActionGroup(I18n.t("tree.aiOps"), true);
        for (final QuickOp op : QuickOp.ALL) {
            ops.addAction(action(op.getName(), null, () -> new AiResultDialog(project, f, op).show()));
        }
        g.add(ops);
        g.addAction(action(I18n.t("tree.ask"), AllIcons.Actions.Find, () -> {
            chatPanel.setContext(f);
            chatPanel.focusInput();
        }));
        g.addSeparator();
        g.addAction(action(I18n.t("tree.copyPath"), AllIcons.Actions.Copy, () ->
                CopyPasteManager.getInstance().setContents(new StringSelection(f.getFile().getPath()))));
        g.addAction(action(I18n.t("tb.ignore"), null, () -> ignoreFile(f)));
        g.addAction(action(I18n.t("zip.action"), AllIcons.FileTypes.Archive, this::zipSelected));
        return g;
    }

    private AnAction action(String text, @Nullable Icon icon, final Runnable r) {
        return new AnAction() {
            {
                getTemplatePresentation().setText(text);
                if (icon != null) {
                    getTemplatePresentation().setIcon(icon);
                }
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                r.run();
            }
        };
    }

    private static final class MdTreeCellRenderer extends ColoredTreeCellRenderer {
        private static final SimpleTextAttributes CATEGORY =
                new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null);

        @Override
        public void customizeCellRenderer(@NotNull JTree t, Object value, boolean selected,
                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if (o instanceof MdFile) {
                MdFile f = (MdFile) o;
                setIcon(f.isFromAiToolDir() ? AllIcons.FileTypes.Config : AllIcons.FileTypes.Text);
                append(f.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                String parent = f.getParentDirName();
                if (!parent.isEmpty() && !I18n.t("root.dir").equals(parent)) {
                    append("  " + parent, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                setToolTipText(f.getRelativePath());
            } else if (o instanceof String) {
                setIcon(AllIcons.Nodes.Folder);
                append((String) o, CATEGORY);
                append("  (" + ((DefaultMutableTreeNode) value).getChildCount() + ")",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}
