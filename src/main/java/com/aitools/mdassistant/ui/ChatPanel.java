package com.aitools.mdassistant.ui;

import com.aitools.mdassistant.ai.AiClient;
import com.aitools.mdassistant.ai.AiConfig;
import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.cat.AiClassifier;
import com.aitools.mdassistant.cat.CategoryService;
import com.aitools.mdassistant.model.MdFile;
import com.aitools.mdassistant.ui.settings.AiConfigurable;
import com.aitools.mdassistant.util.I18n;
import com.aitools.mdassistant.util.MdText;
import com.aitools.mdassistant.util.Notify;
import com.aitools.mdassistant.util.Ui;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AI 对话面板：上下文条 + 对话记录 + 提问输入区。
 * 上下文与对话区可整体折叠（只保留提问输入区），发送提问时自动展开。
 */
public class ChatPanel extends JPanel {

    private static final int MAX_HISTORY = 16;
    private static final int MAX_DOC_CHARS = 15000;

    private final Project project;
    private final Supplier<List<MdFile>> filesSupplier;
    private final Runnable onCategoryChanged;
    private final Consumer<Boolean> onAreaToggle;

    private final JTextPane chatView = new JTextPane();
    private final ComboBox<String> contextCombo = new ComboBox<>();
    private final List<MdFile> comboFiles = new ArrayList<>();
    private final JBTextArea input = new JBTextArea(3, 24);
    private final JButton classifyBtn = new JButton();
    private final JButton sendBtn = new JButton();
    private final JButton stopBtn = new JButton();
    private final JButton clearBtn = new JButton();
    private final JLabel collapseToggle = new JLabel();

    private JComponent contextBar;
    private JScrollPane chatScroll;
    private TitledBorder inputBorder;

    private boolean chatAreaCollapsed;

    private final List<AiClient.Msg> history = new ArrayList<>();
    private AiClient client;

    private final SimpleAttributeSet headUser = new SimpleAttributeSet();
    private final SimpleAttributeSet headAi = new SimpleAttributeSet();
    private final SimpleAttributeSet headSys = new SimpleAttributeSet();
    private final SimpleAttributeSet bodyStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet errStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet okStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet hintStyle = new SimpleAttributeSet();

    public ChatPanel(@NotNull Project project,
                     @NotNull Supplier<List<MdFile>> filesSupplier,
                     @NotNull Runnable onCategoryChanged,
                     @Nullable Consumer<Boolean> onAreaToggle) {
        this.project = project;
        this.filesSupplier = filesSupplier;
        this.onCategoryChanged = onCategoryChanged;
        this.onAreaToggle = onAreaToggle;
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(2));

        StyleConstants.setBold(headUser, true);
        StyleConstants.setForeground(headUser, new Color(0x1E66C9));
        StyleConstants.setBold(headAi, true);
        StyleConstants.setForeground(headAi, new Color(0x0E7A46));
        StyleConstants.setBold(headSys, true);
        StyleConstants.setForeground(headSys, new Color(0x8C6D1F));
        StyleConstants.setForeground(errStyle, new Color(0xC0392B));
        StyleConstants.setForeground(okStyle, new Color(0x0E7A46));
        StyleConstants.setItalic(hintStyle, true);
        StyleConstants.setForeground(hintStyle, new Color(0x8A8A8A));
        StyleConstants.setForeground(bodyStyle, chatView.getForeground());

        chatView.setEditable(false);
        insert(I18n.t("chat.hint"), hintStyle);

        classifyBtn.setText(I18n.t("chat.classify"));
        classifyBtn.setToolTipText(I18n.t("chat.classify.tip"));
        sendBtn.setText(I18n.t("chat.send"));
        stopBtn.setText(I18n.t("chat.stop"));
        clearBtn.setText(I18n.t("chat.clear"));

        contextBar = buildContextBar();
        chatScroll = ScrollPaneFactory.createScrollPane(chatView);

        chatAreaCollapsed = AiSettings.getInstance().isChatAreaCollapsed();
        applyChatAreaCollapsed(false);

        add(contextBar, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);
        add(buildInputArea(), BorderLayout.SOUTH);

        refreshContexts();
    }

    // ==================== 布局 ====================

    private JComponent buildContextBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        JLabel label = new JLabel(I18n.t("chat.context"));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setToolTipText(I18n.t("chat.context.tip"));
        bar.add(label, BorderLayout.WEST);
        contextCombo.setPreferredSize(new Dimension(120, contextCombo.getPreferredSize().height));
        contextCombo.setToolTipText(I18n.t("chat.context.tip"));
        bar.add(contextCombo, BorderLayout.CENTER);
        clearBtn.setMargin(JBUI.insets(1, 6));
        clearBtn.addActionListener(e -> clearConversation());
        bar.add(clearBtn, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildInputArea() {
        JPanel area = new JPanel(new BorderLayout(6, 0)) {
            @Override
            public Dimension getPreferredSize() {
                // 提问区保持稳定的最小可见高度（标题框 + 输入框 + 按钮行）
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width, Math.max(d.height, 140));
            }
        };
        inputBorder = BorderFactory.createTitledBorder(I18n.t("chat.inputTitle"));
        area.setBorder(BorderFactory.createCompoundBorder(inputBorder, JBUI.Borders.empty(2, 4, 4, 4)));

        collapseToggle.setToolTipText(I18n.t("chat.area.toggle"));
        collapseToggle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        collapseToggle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                setChatAreaCollapsed(!chatAreaCollapsed);
            }
        });
        JPanel toggleWrap = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        toggleWrap.add(collapseToggle);
        area.add(toggleWrap, BorderLayout.WEST);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setMargin(JBUI.insets(4, 6));
        input.getEmptyText().setText(I18n.t("chat.inputHint"));
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ctrl ENTER"), "send");
        input.getActionMap().put("send", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                send();
            }
        });
        area.add(ScrollPaneFactory.createScrollPane(input), BorderLayout.CENTER);

        // 按钮放底部整行居中，窄宽度下不会裁切
        JPanel btnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 2));
        sendBtn.setFont(sendBtn.getFont().deriveFont(Font.BOLD));
        sendBtn.setForeground(new Color(0x1E66C9));
        sendBtn.addActionListener(e -> send());
        stopBtn.setForeground(new Color(0xC0392B));
        stopBtn.addActionListener(e -> stop());
        stopBtn.setVisible(false);
        classifyBtn.addActionListener(e -> classifyCurrent());
        btnRow.add(sendBtn);
        btnRow.add(stopBtn);
        btnRow.add(classifyBtn);
        area.add(btnRow, BorderLayout.SOUTH);
        return area;
    }

    /** 折叠/展开上下文条与对话记录区（仅当前会话；初始默认值由设置项控制）。 */
    private void setChatAreaCollapsed(boolean collapsed) {
        chatAreaCollapsed = collapsed;
        applyChatAreaCollapsed(true);
    }

    public boolean isChatAreaCollapsed() {
        return chatAreaCollapsed;
    }

    private void applyChatAreaCollapsed(boolean notifyToggle) {
        contextBar.setVisible(!chatAreaCollapsed);
        chatScroll.setVisible(!chatAreaCollapsed);
        collapseToggle.setIcon(chatAreaCollapsed ? AllIcons.General.ArrowRight : AllIcons.General.ArrowDown);
        collapseToggle.setToolTipText(I18n.t("chat.area.toggle"));
        if (inputBorder != null) {
            inputBorder.setTitle(I18n.t(chatAreaCollapsed ? "chat.area.collapsedTitle" : "chat.inputTitle"));
        }
        revalidate();
        repaint();
        if (notifyToggle && onAreaToggle != null) {
            onAreaToggle.accept(chatAreaCollapsed);
        }
    }

    // ==================== 上下文 ====================

    public void refreshContexts() {
        String prev = (String) contextCombo.getSelectedItem();
        contextCombo.removeAllItems();
        comboFiles.clear();
        contextCombo.addItem(I18n.t("chat.noContext"));
        for (MdFile f : filesSupplier.get()) {
            comboFiles.add(f);
            contextCombo.addItem(f.getRelativePath());
        }
        if (prev != null && !prev.equals(I18n.t("chat.noContext"))) {
            for (int i = 0; i < contextCombo.getItemCount(); i++) {
                if (prev.equals(contextCombo.getItemAt(i))) {
                    contextCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        contextCombo.setMaximumRowCount(20);
    }

    public void setContext(@NotNull MdFile file) {
        for (int i = 0; i < comboFiles.size(); i++) {
            if (comboFiles.get(i).getFile().equals(file.getFile())) {
                if (chatAreaCollapsed) {
                    setChatAreaCollapsed(false); // 外部指定上下文时自动展开
                }
                contextCombo.setSelectedIndex(i + 1);
                return;
            }
        }
    }

    public void focusInput() {
        input.requestFocusInWindow();
        input.requestFocus();
    }

    @Nullable
    private MdFile currentContext() {
        int idx = contextCombo.getSelectedIndex();
        if (idx <= 0 || idx - 1 >= comboFiles.size()) {
            return null;
        }
        return comboFiles.get(idx - 1);
    }

    void clearConversation() {
        history.clear();
        chatView.setText("");
    }

    // ==================== 普通问答 ====================

    private void send() {
        final String q = input.getText().trim();
        if (q.isEmpty()) {
            return;
        }
        if (!ensureConfigured()) {
            return;
        }
        if (chatAreaCollapsed) {
            setChatAreaCollapsed(false); // 提问时展开以显示回答
        }
        history.add(new AiClient.Msg("user", q));
        if (history.size() > MAX_HISTORY) {
            history.subList(0, history.size() - MAX_HISTORY).clear();
        }
        appendMessage(I18n.t("chat.you"), q, headUser);

        input.setText("");
        setBusy(true);

        MdFile ctx = currentContext();
        String system = buildSystem(ctx);
        List<AiClient.Msg> msgs = new ArrayList<>(history);
        client = new AiClient(AiConfig.from(AiSettings.getInstance()));

        insertLater(I18n.t("chat.ai") + "\n", headAi);
        final AiClient c = client;
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                c.chat(system, msgs, new AiClient.Callback() {
                    @Override
                    public void onDelta(String text) {
                        insertLater(text, bodyStyle);
                    }

                    @Override
                    public void onSuccess(String fullText) {
                        history.add(new AiClient.Msg("assistant", fullText));
                        insertLater("\n\n", bodyStyle);
                        setBusy(false);
                    }

                    @Override
                    public void onError(String message) {
                        insertLater(I18n.f("chat.err", message), errStyle);
                        setBusy(false);
                    }
                }));
    }

    // ==================== 对话式 AI 分类 ====================

    private void classifyCurrent() {
        final MdFile ctx = currentContext();
        if (ctx == null) {
            Notify.warn(project, I18n.t("chat.pickContext"));
            return;
        }
        if (!ensureConfigured()) {
            return;
        }
        if (chatAreaCollapsed) {
            setChatAreaCollapsed(false);
        }
        String content = MdText.read(ctx.getFile());
        if (content == null) {
            content = I18n.t("dlg.readFail");
        }
        if (content.length() > 3000) {
            content = content.substring(0, 3000);
        }
        String title = MdText.title(content);
        if (title == null || title.isEmpty()) {
            title = ctx.getTitle();
        }

        appendMessage(I18n.t("chat.sys"), I18n.f("chat.classifyReq", ctx.getName()), headSys);
        setBusy(true);

        List<String> allowed = AiClassifier.allCategories(project);
        String system = "你是文档分类助手。只输出一个类别名，不要输出任何解释。可选类别："
                + String.join("、", allowed);
        String user = "文档《" + title + "》路径: " + ctx.getRelativePath()
                + "\n内容摘要：\n" + content;
        client = new AiClient(AiConfig.from(AiSettings.getInstance()));
        final AiClient c = client;
        final MdFile file = ctx;
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                c.chat(system, java.util.Collections.singletonList(new AiClient.Msg("user", user)),
                        new AiClient.Callback() {
                            @Override
                            public void onDelta(String text) {
                                insertLater(text, bodyStyle);
                            }

                            @Override
                            public void onSuccess(String fullText) {
                                finishClassify(file, fullText);
                            }

                            @Override
                            public void onError(String message) {
                                insertLater(I18n.f("chat.err", message), errStyle);
                                setBusy(false);
                            }
                        }));
    }

    private void finishClassify(MdFile file, String reply) {
        String cat = null;
        String clean = AiClassifier.stripFences(reply == null ? "" : reply.trim());
        for (String c : AiClassifier.allCategories(project)) {
            if (clean.contains(c)) {
                cat = c;
                break;
            }
        }
        final String applied = cat;
        Ui.invokeLater(() -> {
            if (applied != null) {
                CategoryService.getInstance(project).store(file.getFile(), applied);
                insertLater(I18n.f("chat.applied", applied), okStyle);
                onCategoryChanged.run();
            } else {
                insertLater(I18n.t("chat.unrecognized"), hintStyle);
            }
            setBusy(false);
        });
    }

    // ==================== 通用 ====================

    private boolean ensureConfigured() {
        if (AiSettings.getInstance().isConfigured()) {
            return true;
        }
        Notify.error(project, I18n.t("notify.needConfig"));
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AiConfigurable.class);
        return false;
    }

    private void stop() {
        AiClient c = client;
        if (c != null) {
            c.cancel();
        }
        insertLater(I18n.t("chat.stopped"), bodyStyle);
        setBusy(false);
    }

    private void setBusy(final boolean busy) {
        Runnable r = () -> {
            sendBtn.setEnabled(!busy);
            classifyBtn.setEnabled(!busy);
            stopBtn.setVisible(busy);
            input.setEnabled(!busy);
            input.setBackground(busy ? javax.swing.UIManager.getColor("TextField.disabledBackground")
                    : javax.swing.UIManager.getColor("TextField.background"));
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            Ui.invokeLater(r);
        }
    }

    @NotNull
    private String buildSystem(@Nullable MdFile ctx) {
        StringBuilder sb = new StringBuilder(
                "你是一名项目文档助手，帮助用户阅读、整理、优化 Markdown 文档。默认使用简体中文回答，结构清晰，代码用 Markdown 代码块包裹。");
        if (ctx != null) {
            String content = MdText.read(ctx.getFile());
            if (content == null) {
                content = I18n.t("dlg.readFail");
            }
            if (content.length() > MAX_DOC_CHARS) {
                content = content.substring(0, MAX_DOC_CHARS) + "\n……（内容过长，已截断）";
            }
            sb.append("\n\n当前用户选定的参考文档《").append(ctx.getName())
                    .append("》（路径 ").append(ctx.getRelativePath()).append("）内容如下：\n")
                    .append(content);
        }
        return sb.toString();
    }

    private void appendMessage(String who, String text, SimpleAttributeSet headStyle) {
        insertLater(who + "\n", headStyle);
        insertLater(text + "\n\n", bodyStyle);
    }

    private void insertLater(final String text, final SimpleAttributeSet style) {
        Ui.invokeLater(() -> insert(text, style));
    }

    private void insert(String text, SimpleAttributeSet style) {
        StyledDocument doc = chatView.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
            chatView.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignore) {
        }
    }
}
