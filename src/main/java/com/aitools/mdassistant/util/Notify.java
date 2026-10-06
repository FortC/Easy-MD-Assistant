package com.aitools.mdassistant.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public final class Notify {

    private Notify() {
    }

    public static void info(@Nullable Project project, String message) {
        show(project, message, NotificationType.INFORMATION);
    }

    public static void warn(@Nullable Project project, String message) {
        show(project, message, NotificationType.WARNING);
    }

    public static void error(@Nullable Project project, String message) {
        show(project, message, NotificationType.ERROR);
    }

    private static void show(@Nullable Project project, String message, NotificationType type) {
        Notification n = NotificationGroupManager.getInstance()
                .getNotificationGroup("EasyMDAssistant")
                .createNotification(message, type);
        n.setTitle("EasyMDAssistant");
        n.notify(project);
    }
}
