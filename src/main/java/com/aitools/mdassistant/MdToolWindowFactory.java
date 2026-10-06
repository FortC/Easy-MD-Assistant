package com.aitools.mdassistant;

import com.aitools.mdassistant.ui.MdToolWindowPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class MdToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MdToolWindowPanel panel = new MdToolWindowPanel(project);
        ContentFactory factory = ApplicationManager.getApplication().getService(ContentFactory.class);
        Content content = factory.createContent(panel, "", false);
        content.setCloseable(false);
        content.setPreferredFocusableComponent(panel);
        toolWindow.getContentManager().addContent(content);
        panel.refresh();
    }
}
