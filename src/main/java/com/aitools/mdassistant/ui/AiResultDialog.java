package com.aitools.mdassistant.ui;

import com.aitools.mdassistant.ai.AiClient;
import com.aitools.mdassistant.ai.AiConfig;
import com.aitools.mdassistant.ai.AiSettings;
import com.aitools.mdassistant.model.MdFile;
import com.aitools.mdassistant.util.I18n;
import com.aitools.mdassistant.util.MdText;
import com.aitools.mdassistant.util.Notify;
import com.aitools.mdassistant.util.Ui;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

/** AI 快捷操作结果对话框：流式展示结果，支持复制 / 追加到原文 / 另存为新文档。 */
public class AiResultDialog extends com.intellij.openapi.ui.DialogWrapper {

    private static final int MAX_DOC_CHARS = 20000;

    private final Project project;
    private final MdFile file;
    private final QuickOp op;

    private final JTextArea resultArea = new JBTextArea(24, 70);
    private final AiClient client;

    public AiResultDialog(@NotNull Project project, @NotNull MdFile file, @NotNull QuickOp op) {
        super(project, true);
        this.project = project;
        this.file = file;
        this.op = op;
        setTitle(I18n.f("dlg.title", op.getName(), file.getName()));
        setOKButtonText(I18n.t("dlg.close"));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setEditable(false);
        client = new AiClient(AiConfig.from(AiSettings.getInstance()));
        init();
        if (!AiSettings.getInstance().isConfigured()) {
            appendUi(I18n.t("dlg.notConfigured"));
            return;
        }
        start();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    public void doCancelAction() {
        client.cancel();
        close(CANCEL_EXIT_CODE);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(760, 560));

        JBLabel caption = new JBLabel(I18n.f("dlg.caption", file.getRelativePath(), op.getName()));
        panel.add(caption, BorderLayout.NORTH);
        panel.add(ScrollPaneFactory.createScrollPane(resultArea), BorderLayout.CENTER);

        JButton copyBtn = new JButton(I18n.t("dlg.copy"));
        JButton appendBtn = new JButton(I18n.t("dlg.append"));
        JButton saveAsBtn = new JButton(I18n.t("dlg.saveAs"));
        JButton stopBtn = new JButton(I18n.t("dlg.stopGen"));
        stopBtn.setForeground(new Color(0xC0392B));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(copyBtn);
        buttons.add(appendBtn);
        buttons.add(saveAsBtn);
        buttons.add(new JBLabel("    "));
        buttons.add(stopBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        copyBtn.addActionListener(e ->
                CopyPasteManager.getInstance().setContents(new StringSelection(resultArea.getText())));
        appendBtn.addActionListener(e -> appendToDocument());
        saveAsBtn.addActionListener(e -> saveAsNewDocument());
        stopBtn.addActionListener(e -> {
            client.cancel();
            appendUi(I18n.t("dlg.stopped"));
        });
        return panel;
    }

    private void start() {
        String content = MdText.read(file.getFile());
        if (content == null) {
            content = I18n.t("dlg.readFail");
        }
        if (content.length() > MAX_DOC_CHARS) {
            content = content.substring(0, MAX_DOC_CHARS) + "\n……（内容过长，已截断）";
        }
        String prompt = op.prompt + "\n\n以下是文档《" + file.getName() + "》的内容：\n\n" + content;
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                client.chat(null, Collections.singletonList(new AiClient.Msg("user", prompt)),
                        new AiClient.Callback() {
                            @Override
                            public void onDelta(String text) {
                                appendUi(text);
                            }

                            @Override
                            public void onSuccess(String fullText) {
                                appendUi(I18n.t("dlg.done"));
                            }

                            @Override
                            public void onError(String message) {
                                appendUi(I18n.f("chat.err", message));
                            }
                        }));
    }

    private void appendUi(final String text) {
        Ui.invokeLater(() -> {
            resultArea.append(text);
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }

    private void appendToDocument() {
        final String result = resultArea.getText().trim();
        if (result.isEmpty()) {
            Notify.warn(project, I18n.t("dlg.empty"));
            return;
        }
        String original = MdText.read(file.getFile());
        if (original == null) {
            original = "";
        }
        final String merged = original + "\n\n---\n\n## AI " + op.getName() + " · "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) + "\n\n" + result + "\n";
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile vf = file.getFile();
                Document doc = FileDocumentManager.getInstance().getDocument(vf);
                if (doc != null) {
                    doc.setText(merged);
                    FileDocumentManager.getInstance().saveDocument(doc);
                } else {
                    VfsUtil.saveText(vf, merged);
                }
                Ui.invokeLater(() -> {
                    Notify.info(project, I18n.f("dlg.appended", file.getName()));
                    FileEditorManager.getInstance(project).openFile(file.getFile(), true);
                });
            } catch (IOException e) {
                Notify.error(project, I18n.f("dlg.writeFail", e.getMessage()));
            }
        });
    }

    private void saveAsNewDocument() {
        final String result = resultArea.getText().trim();
        if (result.isEmpty()) {
            Notify.warn(project, I18n.t("dlg.empty"));
            return;
        }
        final VirtualFile parent = file.getFile().getParent();
        if (parent == null) {
            return;
        }
        String base = file.getFile().getNameWithoutExtension();
        String name = base + "-ai.md";
        if (parent.findChild(name) != null) {
            name = base + "-ai-" + System.currentTimeMillis() + ".md";
        }
        final String fileName = name;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile nf = parent.createChildData(this, fileName);
                VfsUtil.saveText(nf, result);
                Ui.invokeLater(() ->
                        FileEditorManager.getInstance(project).openFile(nf, true));
            } catch (IOException e) {
                Notify.error(project, I18n.f("dlg.saveFail", e.getMessage()));
            }
        });
    }
}
