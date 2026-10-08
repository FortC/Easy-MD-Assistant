package com.aitools.mdassistant.ui.settings;

import com.aitools.mdassistant.ai.CcSwitchImporter;
import com.aitools.mdassistant.util.I18n;
import com.intellij.ui.table.JBTable;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

/** 「导入 cc-switch 配置」候选选择弹窗：表格列出发现的服务商，双击或 OK 选中。 */
public class CcSwitchChooserDialog extends DialogWrapper {

    private final List<CcSwitchImporter.Candidate> candidates;
    private JBTable table;
    private int selected = -1;

    public CcSwitchChooserDialog(@NotNull List<CcSwitchImporter.Candidate> candidates) {
        super(true);
        this.candidates = candidates;
        setTitle(I18n.t("set.importCc"));
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{I18n.t("cc.col.source"), I18n.t("cc.col.name"), I18n.t("cc.col.proto"),
                        I18n.t("cc.col.base"), I18n.t("cc.col.model"), I18n.t("cc.col.key")},
                candidates.size()) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (int i = 0; i < candidates.size(); i++) {
            CcSwitchImporter.Candidate c = candidates.get(i);
            model.setValueAt(c.source, i, 0);
            model.setValueAt(c.name, i, 1);
            model.setValueAt("anthropic".equals(c.provider)
                    ? I18n.t("set.providerAnthropic") : I18n.t("set.providerOpenAi"), i, 2);
            model.setValueAt(c.baseUrl.isEmpty() ? "-" : c.baseUrl, i, 3);
            model.setValueAt(c.model.isEmpty() ? "-" : c.model, i, 4);
            model.setValueAt(c.maskedKey(), i, 5);
        }
        table = new JBTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        // Key 列等宽字体掩码列，避免抖动
        table.getColumnModel().getColumn(5).setCellRenderer(new MonoRenderer());

        ListSelectionListener sel = (ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                selected = table.getSelectedRow();
            }
        };
        table.getSelectionModel().addListSelectionListener(sel);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    selected = table.getSelectedRow();
                    doOKAction();
                }
            }
        });
        if (!candidates.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            selected = 0;
        }

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setPreferredSize(new java.awt.Dimension(640, Math.max(120, 24 + candidates.size() * 26 + 40)));
        panel.add(new JLabel(I18n.t("cc.pick")), BorderLayout.NORTH);
        panel.add(com.intellij.ui.ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    @Nullable
    public CcSwitchImporter.Candidate getSelected() {
        return selected >= 0 && selected < candidates.size() ? candidates.get(selected) : null;
    }

    /** 等宽渲染掩码 Key 列。 */
    private static final class MonoRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            c.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            return c;
        }
    }
}
