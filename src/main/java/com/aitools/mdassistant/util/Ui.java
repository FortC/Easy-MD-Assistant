package com.aitools.mdassistant.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

/**
 * UI 线程调度统一入口。
 * 不带 ModalityState 的 invokeLater 会排队到「无模态对话框」时才执行：
 * 设置页、AI 结果对话框等模态窗口开着时，后台线程提交的状态更新将永远无法绘制
 * （表现为一直停留在“测试中…”）。因此这里固定使用 ModalityState.any()。
 */
public final class Ui {

    private Ui() {
    }

    public static void invokeLater(Runnable r) {
        ApplicationManager.getApplication().invokeLater(r, ModalityState.any());
    }
}
