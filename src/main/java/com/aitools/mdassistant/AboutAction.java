package com.aitools.mdassistant;

import com.aitools.mdassistant.util.I18n;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * 「关于 / 联系作者」入口：Help 菜单与工具栏均可打开。
 * 联系方式来自发布人档案（clb &lt;lamthebest@foxmail.com&gt;）与项目仓库地址；
 * 版本号运行时从插件描述符动态读取，不硬编码。
 */
public class AboutAction extends AnAction implements DumbAware {

    public static final String PLUGIN_ID = "com.clb.easymdassistant";
    public static final String REPO_URL = "https://github.com/FortC/Easy-MD-Assistant";

    public AboutAction() {
        getTemplatePresentation().setText(I18n.t("about.action"));
        getTemplatePresentation().setDescription(I18n.t("about.action.desc"));
        getTemplatePresentation().setIcon(AllIcons.General.Information);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 语言切换后无需重启即可刷新菜单文案
        e.getPresentation().setText(I18n.t("about.action"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        String version = plugin != null && plugin.getVersion() != null ? plugin.getVersion() : "";
        String info = "<html><body style='width:420px'>"
                + "<h3>EasyMDAssistant&nbsp;&nbsp;v" + version + "</h3>"
                + "<p>" + I18n.t("about.desc") + "</p><hr/>"
                + I18n.t("about.author") + "：clb<br/>"
                + I18n.t("about.email") + "：<a href='mailto:lamthebest@foxmail.com'>lamthebest@foxmail.com</a><br/>"
                + I18n.t("about.home") + "：<a href='" + REPO_URL + "'>github.com/FortC/Easy-MD-Assistant</a><br/>"
                + I18n.t("about.issues") + "：<a href='" + REPO_URL + "/issues'>GitHub Issues</a><br/>"
                + I18n.t("about.license") + "：Apache-2.0"
                + "</body></html>";
        Messages.showInfoMessage(info, I18n.t("about.title"));
    }
}
