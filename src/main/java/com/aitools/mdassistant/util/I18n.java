package com.aitools.mdassistant.util;

import com.aitools.mdassistant.ai.AiSettings;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 界面双语（简体中文 / English），跟随 IDE 或手动切换。文案 key 缺失时回退中文。 */
public final class I18n {

    public static final String LANG_AUTO = "auto";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        ZH.put("tb.refresh", "重新扫描");
        ZH.put("tb.refresh.desc", "重新扫描项目中的全部 Markdown 文档");
        ZH.put("tb.classify", "智能分类");
        ZH.put("tb.classify.desc", "按类别对文档进行分组归类");
        ZH.put("classify.rule", "内置规则分类（免联网）");
        ZH.put("classify.ai", "AI 大模型分类");
        ZH.put("tb.showAiDocs", "显示 AI 工具文档");
        ZH.put("tb.showAiDocs.desc", "显示/隐藏 .codex、.claude、.workbuddy 等 AI 工具目录中的文档");
        ZH.put("tb.settings", "设置");
        ZH.put("tb.settings.desc", "AI 服务、知识库目录、AI 规则等配置");
        ZH.put("tb.syncRules", "同步 AI 规则");
        ZH.put("tb.syncRules.desc", "将知识库规则写入项目的 AI 规范文件（如 AGENTS.md）");
        ZH.put("group.label", "分组:");
        ZH.put("group.dir", "按目录分组");
        ZH.put("group.smart", "智能分类");
        ZH.put("group.none", "不分组");
        ZH.put("group.date", "按日期分组");
        ZH.put("group.aiTool", "AI 工具文档");
        ZH.put("root.dir", "(项目根目录)");
        ZH.put("tree.open", "打开");
        ZH.put("tree.category", "分类");
        ZH.put("tree.ruleThis", "内置规则识别此文档");
        ZH.put("tree.aiThis", "AI 分类此文档");
        ZH.put("tree.aiOps", "AI 操作");
        ZH.put("tree.ask", "用此文档问答");
        ZH.put("tree.copyPath", "复制完整路径");
        ZH.put("notify.noMd", "未在项目中找到 Markdown 文档");
        ZH.put("notify.cap", "文档数量较多，仅加载前 {0} 个");
        ZH.put("notify.ruleDone", "已按内置规则完成分类（免联网）；也可用「AI 大模型分类」获得更准确的结果");
        ZH.put("notify.ruleOne.hit", "《{0}》 → {1}");
        ZH.put("notify.ruleOne.miss", "内置规则未能识别《{0}》的类别");
        ZH.put("notify.nothingToClassify", "没有可分类的文档，请先扫描");
        ZH.put("notify.classifyFail", "AI 分类失败：{0}");
        ZH.put("notify.needConfig", "请先在 Settings > Tools > EasyMDAssistant AI 中配置 API Key 与模型");
        ZH.put("notify.rulesSynced", "已写入 AI 规则：{0}");
        ZH.put("notify.rulesFail", "AI 规则写入失败：{0}");
        ZH.put("task.scan", "扫描 Markdown 文档");
        ZH.put("task.classify", "AI 智能分类");
        ZH.put("task.syncRules", "同步 AI 规则");

        ZH.put("chat.context", "上下文:");
        ZH.put("chat.noContext", "（无上下文文档）");
        ZH.put("chat.clear", "清空会话");
        ZH.put("chat.inputTitle", " 向 AI 提问（Ctrl+Enter 发送）");
        ZH.put("chat.send", "发送 ➤");
        ZH.put("chat.stop", "停止");
        ZH.put("chat.classify", "归类当前文档");
        ZH.put("chat.classify.tip", "让 AI 阅读当前上下文文档并自动归入类别");
        ZH.put("chat.you", "你");
        ZH.put("about.action", "关于 EasyMDAssistant");
        ZH.put("about.action.desc", "作者与联系方式");
        ZH.put("about.title", "关于 EasyMDAssistant");
        ZH.put("about.desc", "AI Markdown 文档助手：扫描、分类项目文档，并支持流式 AI 对话。");
        ZH.put("about.author", "作者");
        ZH.put("about.email", "邮箱");
        ZH.put("about.home", "主页");
        ZH.put("about.issues", "问题反馈");
        ZH.put("about.license", "许可证");
        ZH.put("chat.ai", "AI");
        ZH.put("chat.sys", "系统");
        ZH.put("chat.hint", "提示：在上方选择文档后提问；点「归类当前文档」可让 AI 自动分类。\n"
                + "文档上的右键菜单提供总结/翻译/润色等 AI 操作。\n\n");
        ZH.put("chat.stopped", "\n（已停止）\n\n");
        ZH.put("chat.err", "\n[出错] {0}\n\n");
        ZH.put("chat.applied", "\n✔ 已归入分类：{0}\n\n");
        ZH.put("chat.unrecognized", "\n（未能从回复中识别出类别，未自动应用）\n\n");
        ZH.put("chat.classifyReq", "请求 AI 分类《{0}》…");
        ZH.put("chat.pickContext", "请先在上方选择一个上下文文档");
        ZH.put("chat.inputHint", "输入问题，Ctrl+Enter 发送…");

        ZH.put("dlg.caption", "对 {0} 执行「{1}」，结果如下：");
        ZH.put("dlg.close", "关闭");
        ZH.put("dlg.copy", "复制结果");
        ZH.put("dlg.append", "追加到文档末尾");
        ZH.put("dlg.saveAs", "另存为新文档");
        ZH.put("dlg.stopGen", "停止生成");
        ZH.put("dlg.done", "\n（完成）\n");
        ZH.put("dlg.stopped", "\n（已停止生成）\n");
        ZH.put("dlg.empty", "当前没有可写入的结果");
        ZH.put("dlg.appended", "已追加到 {0}");
        ZH.put("dlg.saveFail", "保存失败：{0}");
        ZH.put("dlg.writeFail", "写入失败：{0}");
        ZH.put("dlg.notConfigured", "（尚未配置 AI：请到 Settings > Tools > EasyMDAssistant AI 配置后重试）\n");
        ZH.put("dlg.readFail", "(无法读取文档内容)");
        ZH.put("dlg.title", "AI {0} — {1}");

        ZH.put("op.summarize", "总结全文");
        ZH.put("op.translate", "翻译成英文");
        ZH.put("op.polish", "润色优化");
        ZH.put("op.outline", "生成大纲");
        ZH.put("op.todos", "提取待办");

        ZH.put("set.sectionAi", "AI 服务");
        ZH.put("set.sectionUi", "界面与扫描");
        ZH.put("set.sectionLib", "知识库目录");
        ZH.put("set.sectionRules", "项目 AI 规则");
        ZH.put("set.sectionMapping", "文件夹分类映射");
        ZH.put("set.provider", "服务商");
        ZH.put("set.providerOpenAi", "OpenAI 兼容 (chat/completions)");
        ZH.put("set.providerAnthropic", "Anthropic (messages)");
        ZH.put("set.baseUrl", "API 地址 (Base URL)");
        ZH.put("set.model", "模型 ID");
        ZH.put("set.apiKey", "API Key");
        ZH.put("set.maxTokens", "最大输出 Tokens");
        ZH.put("set.timeout", "请求超时（秒）");
        ZH.put("set.test", "测试连接");
        ZH.put("set.copyCurl", "复制 cURL");
        ZH.put("set.curlCopied", "已复制等价 cURL 命令：请在终端（Git Bash/PowerShell）粘贴运行——若 curl 通而插件不通请反馈，若 curl 也不通则是网络/配置问题");
        ZH.put("set.testing", "测试中…");
        ZH.put("set.testingUrl", "测试中… {0}");
        ZH.put("set.testTimeout", "失败：等待 {0} 秒超时（{1}）。请检查网络，或在 IDE 设置/环境变量(HTTPS_PROXY)中配置代理");
        ZH.put("set.hint401", "—— API Key 无效或无权限，请核对 Key");
        ZH.put("set.hint404", "—— 接口路径不存在，请核对 Base URL（通常填到域名即可，如 https://api.deepseek.com；带版本号的地址请确认路径）");
        ZH.put("set.hint407", "—— 代理要求认证，暂不支持");
        ZH.put("set.hintTimeout", "—— 网络不通或需要代理：请配置 IDE 的 HTTP Proxy 或 HTTPS_PROXY 环境变量");
        ZH.put("set.hintDns", "—— 域名无法解析，请检查地址拼写或 DNS/代理");
        ZH.put("set.hintAnthropic", "提示：api.anthropic.com 国内通常无法直连；请把 Base URL 换成你的中转/镜像地址（如 cc switch 使用的本地或国内中转），或在 IDE 的 HTTP Proxy 中配置代理后重启");
        ZH.put("set.testOk", "连接成功，模型已响应：{0}");
        ZH.put("set.testFail", "失败：{0}");
        ZH.put("set.testFill", "请先填写 API Key 与模型 ID");
        ZH.put("set.lang", "界面语言");
        ZH.put("set.langAuto", "跟随 IDE");
        ZH.put("set.langHint", "切换语言后重新打开 EasyMDAssistant 工具窗口生效");
        ZH.put("set.aiDirs", "AI 工具目录（每行一个）");
        ZH.put("set.showAiDocs", "在文档树中显示 AI 工具目录的文档");
        ZH.put("set.chatCollapsed", "打开工具窗口时默认折叠对话区（仅保留提问输入框）");
        ZH.put("set.groupMode", "默认分组方式");

        ZH.put("zip.action", "打包成 ZIP…");
        ZH.put("zip.saveTitle", "打包文档");
        ZH.put("zip.saveDesc", "选择 ZIP 保存位置");
        ZH.put("zip.task", "打包 ZIP");
        ZH.put("zip.done", "已打包 {0} 个文档 → {1}");
        ZH.put("zip.fail", "打包失败：{0}");
        ZH.put("set.aiDirsHint", "扫描时默认过滤这些目录；勾选上方选项后会在「AI 工具文档」分组中显示。");
        ZH.put("set.libReq", "需求库目录名");
        ZH.put("set.libRes", "成果库目录名");
        ZH.put("set.libErr", "错误库目录名");
        ZH.put("set.libExa", "示例库目录名");
        ZH.put("set.autoCreateLibs", "同步 AI 规则时自动创建缺失的知识库目录");
        ZH.put("set.libHint", "四个知识库目录位于项目根目录下；清空名称即停用对应的库。");
        ZH.put("set.rulesTarget", "AI 规则文件（项目内路径）");
        ZH.put("set.ruleAvoid", "写入「避免」：引用错误库中的教训");
        ZH.put("set.ruleExample", "写入「优先参考」：生成代码优先参考示例库");
        ZH.put("set.ruleArchive", "写入「归档」：生成结果归档到成果库");
        ZH.put("set.ruleRead", "写入「需求」：开发前先阅读需求库");
        ZH.put("set.ruleExtra", "附加规则（追加到生成内容末尾）");
        ZH.put("set.mapping", "文件夹映射");
        ZH.put("set.mapDir", "目录前缀");
        ZH.put("set.mapCat", "类别");
        ZH.put("set.mapHint", "匹配规则：相对路径以前缀开头即命中，最长前缀优先；类别可填内置 10 类或自定义。");
        ZH.put("set.aiHint", "地址可只填到域名，自动拼接 /v1/chat/completions 或 /v1/messages。");

        ZH.put("classify.badFormat", "模型返回格式不正确：{0}");
        ZH.put("classify.progress", "AI 分类中 {0}/{1}");

        ZH.put("tb.ignore", "忽略此文档");
        ZH.put("notify.ignored", "已忽略《{0}》；可在 设置 → Tools → EasyMDAssistant AI → 忽略的文档 中还原");
        ZH.put("tree.moveTo", "分类到指定目录…");
        ZH.put("tree.moveTo.msg", "选择目标目录（文档将被移动到项目根目录下的对应文件夹）：");
        ZH.put("tree.moveTo.title", "分类到指定目录");
        ZH.put("notify.moved", "已移动到 {0}/ ：{1}");
        ZH.put("notify.moveFail", "移动失败：{0}");
        ZH.put("notify.alreadyThere", "《{0}》已在该目录下");
        ZH.put("chat.context.tip", "选择作为对话参考的文档：向 AI 提问时会携带该文档全文（最多 15000 字符）");
        ZH.put("chat.context.toggle", "折叠 / 展开上下文选择");
        ZH.put("chat.area.toggle", "折叠 / 展开对话区（上下文与对话记录）");
        ZH.put("chat.area.collapsedTitle", " 向 AI 提问（对话区已折叠）");
        ZH.put("set.sectionLibs", "目录库（类别，全局生效）");
        ZH.put("set.catName", "类别名");
        ZH.put("set.libReset", "恢复默认目录库");
        ZH.put("set.libScopeHint", "知识库目录为项目级配置（新项目默认沿用全局）；「保存为全局默认」后所有项目统一使用当前选择。");
        ZH.put("set.saveGlobal", "保存为全局默认（所有项目）");
        ZH.put("set.useGlobal", "恢复全局默认");
        ZH.put("set.disabled", "（未启用）");
        ZH.put("set.sectionIgnore", "忽略的文档（当前项目）");
        ZH.put("set.ignorePath", "文档");
        ZH.put("set.ignoreTime", "忽略时间");
        ZH.put("set.restoreSel", "还原选中");
        ZH.put("set.restoreAll", "全部还原");
        ZH.put("set.ignoreHint", "被忽略的文档不会出现在扫描结果中；还原后点击工具栏「重新扫描」即可恢复显示。");
        ZH.put("notify.globalSaved", "已保存为全局默认，所有项目将使用当前知识库目录");
        ZH.put("notify.globalRestored", "已恢复为全局默认（本项目）");

        // ==================== English ====================
        EN.put("tb.refresh", "Rescan");
        EN.put("tb.refresh.desc", "Rescan all Markdown documents in the project");
        EN.put("tb.classify", "Classify");
        EN.put("tb.classify.desc", "Group documents by category");
        EN.put("classify.rule", "Rule-based classify (offline)");
        EN.put("classify.ai", "Classify with AI");
        EN.put("tb.showAiDocs", "Show AI tool docs");
        EN.put("tb.showAiDocs.desc", "Show or hide documents from AI tool dirs like .codex, .claude, .workbuddy");
        EN.put("tb.settings", "Settings");
        EN.put("tb.settings.desc", "AI service, libraries, AI rules and more");
        EN.put("tb.syncRules", "Sync AI rules");
        EN.put("tb.syncRules.desc", "Write library rules into the project AI rules file (e.g. AGENTS.md)");
        EN.put("group.label", "Group:");
        EN.put("group.dir", "By directory");
        EN.put("group.smart", "Smart category");
        EN.put("group.none", "Flat list");
        EN.put("group.date", "By date");
        EN.put("group.aiTool", "AI tool docs");
        EN.put("root.dir", "(project root)");
        EN.put("tree.open", "Open");
        EN.put("tree.category", "Classify");
        EN.put("tree.ruleThis", "Recognize with built-in rules");
        EN.put("tree.aiThis", "Classify this doc with AI");
        EN.put("tree.aiOps", "AI actions");
        EN.put("tree.ask", "Ask about this doc");
        EN.put("tree.copyPath", "Copy full path");
        EN.put("notify.noMd", "No Markdown documents found in the project");
        EN.put("notify.cap", "Too many documents, only the first {0} loaded");
        EN.put("notify.ruleDone", "Rule-based classification done (offline); use \"Classify with AI\" for better results");
        EN.put("notify.ruleOne.hit", "\"{0}\" → {1}");
        EN.put("notify.ruleOne.miss", "Built-in rules could not recognize \"{0}\"");
        EN.put("notify.nothingToClassify", "Nothing to classify; scan first");
        EN.put("notify.classifyFail", "AI classification failed: {0}");
        EN.put("notify.needConfig", "Configure API key and model in Settings > Tools > EasyMDAssistant AI first");
        EN.put("notify.rulesSynced", "AI rules written to {0}");
        EN.put("notify.rulesFail", "Failed to write AI rules: {0}");
        EN.put("task.scan", "Scanning Markdown documents");
        EN.put("task.classify", "AI classification");
        EN.put("task.syncRules", "Syncing AI rules");

        EN.put("chat.context", "Context:");
        EN.put("chat.noContext", "(no context document)");
        EN.put("chat.clear", "Clear chat");
        EN.put("chat.inputTitle", " Ask AI (Ctrl+Enter to send)");
        EN.put("chat.send", "Send ➤");
        EN.put("chat.stop", "Stop");
        EN.put("chat.classify", "Categorize this doc");
        EN.put("chat.classify.tip", "Let AI read the context document and categorize it automatically");
        EN.put("chat.you", "You");
        EN.put("about.action", "About EasyMDAssistant");
        EN.put("about.action.desc", "Author & contact info");
        EN.put("about.title", "About EasyMDAssistant");
        EN.put("about.desc", "AI Markdown assistant: scan, classify and chat about your project docs.");
        EN.put("about.author", "Author");
        EN.put("about.email", "Email");
        EN.put("about.home", "Homepage");
        EN.put("about.issues", "Feedback");
        EN.put("about.license", "License");
        EN.put("chat.ai", "AI");
        EN.put("chat.sys", "System");
        EN.put("chat.hint", "Tip: select a document above as context, then ask. \"Categorize this doc\" classifies it via AI.\n"
                + "Right-click a document for summarize / translate / polish actions.\n\n");
        EN.put("chat.stopped", "\n(stopped)\n\n");
        EN.put("chat.err", "\n[error] {0}\n\n");
        EN.put("chat.applied", "\n✔ Categorized as: {0}\n\n");
        EN.put("chat.unrecognized", "\n(could not recognize a category from the reply; not applied)\n\n");
        EN.put("chat.classifyReq", "Asking AI to categorize \"{0}\"…");
        EN.put("chat.pickContext", "Select a context document first");
        EN.put("chat.inputHint", "Type a question, Ctrl+Enter to send…");

        EN.put("dlg.caption", "{1} — {0}. Result:");
        EN.put("dlg.close", "Close");
        EN.put("dlg.copy", "Copy");
        EN.put("dlg.append", "Append to document");
        EN.put("dlg.saveAs", "Save as new doc");
        EN.put("dlg.stopGen", "Stop generating");
        EN.put("dlg.done", "\n(done)\n");
        EN.put("dlg.stopped", "\n(generation stopped)\n");
        EN.put("dlg.empty", "Nothing to write yet");
        EN.put("dlg.appended", "Appended to {0}");
        EN.put("dlg.saveFail", "Save failed: {0}");
        EN.put("dlg.writeFail", "Write failed: {0}");
        EN.put("dlg.notConfigured", "(AI not configured: configure it in Settings > Tools > EasyMDAssistant AI)\n");
        EN.put("dlg.readFail", "(cannot read document)");
        EN.put("dlg.title", "AI {0} — {1}");

        EN.put("op.summarize", "Summarize");
        EN.put("op.translate", "Translate to English");
        EN.put("op.polish", "Polish");
        EN.put("op.outline", "Outline");
        EN.put("op.todos", "Extract TODOs");

        EN.put("set.sectionAi", "AI service");
        EN.put("set.sectionUi", "UI & scanning");
        EN.put("set.sectionLib", "Library directories");
        EN.put("set.sectionRules", "Project AI rules");
        EN.put("set.sectionMapping", "Folder category mappings");
        EN.put("set.provider", "Provider");
        EN.put("set.providerOpenAi", "OpenAI compatible (chat/completions)");
        EN.put("set.providerAnthropic", "Anthropic (messages)");
        EN.put("set.baseUrl", "API base URL");
        EN.put("set.model", "Model ID");
        EN.put("set.apiKey", "API key");
        EN.put("set.maxTokens", "Max output tokens");
        EN.put("set.timeout", "Timeout (seconds)");
        EN.put("set.test", "Test connection");
        EN.put("set.copyCurl", "Copy cURL");
        EN.put("set.curlCopied", "Equivalent cURL copied: run it in a terminal — if curl works but the plugin does not, report it; if curl fails too, it is a network/config issue");
        EN.put("set.testing", "Testing…");
        EN.put("set.testingUrl", "Testing… {0}");
        EN.put("set.testTimeout", "Failed: timed out after {0}s ({1}). Check the network or configure a proxy (IDE settings or HTTPS_PROXY)");
        EN.put("set.hint401", "— invalid or unauthorized API key");
        EN.put("set.hint404", "— endpoint path not found; check the base URL (domain only, e.g. https://api.deepseek.com)");
        EN.put("set.hint407", "— proxy authentication required (not supported yet)");
        EN.put("set.hintTimeout", "— network unreachable or a proxy is required (IDE HTTP Proxy or HTTPS_PROXY)");
        EN.put("set.hintDns", "— cannot resolve the host; check the address or DNS/proxy");
        EN.put("set.hintAnthropic", "Tip: api.anthropic.com is usually unreachable without a proxy in some regions; use your relay/mirror base URL (e.g. the local service used by cc switch) or configure the IDE HTTP proxy and restart");
        EN.put("set.testOk", "Connected, model replied: {0}");
        EN.put("set.testFail", "Failed: {0}");
        EN.put("set.testFill", "Fill in API key and model first");
        EN.put("set.lang", "UI language");
        EN.put("set.langAuto", "Follow IDE");
        EN.put("set.langHint", "Reopen the EasyMDAssistant tool window after changing the language");
        EN.put("set.aiDirs", "AI tool dirs (one per line)");
        EN.put("set.showAiDocs", "Show AI tool docs in the tree");
        EN.put("set.chatCollapsed", "Collapse the chat area by default (keep only the input box)");
        EN.put("set.groupMode", "Default grouping");

        EN.put("zip.action", "Pack into ZIP…");
        EN.put("zip.saveTitle", "Pack documents");
        EN.put("zip.saveDesc", "Choose where to save the ZIP");
        EN.put("zip.task", "Packing ZIP");
        EN.put("zip.done", "Packed {0} documents → {1}");
        EN.put("zip.fail", "Pack failed: {0}");
        EN.put("set.aiDirsHint", "These dirs are filtered while scanning; tick the option above to show them under \"AI tool docs\".");
        EN.put("set.libReq", "Requirement library dir");
        EN.put("set.libRes", "Result library dir");
        EN.put("set.libErr", "Error library dir");
        EN.put("set.libExa", "Example library dir");
        EN.put("set.autoCreateLibs", "Auto-create missing library dirs when syncing rules");
        EN.put("set.libHint", "The four library dirs live in the project root; clear a name to disable that library.");
        EN.put("set.rulesTarget", "AI rules file (project path)");
        EN.put("set.ruleAvoid", "Include \"Avoid\": lessons from the error library");
        EN.put("set.ruleExample", "Include \"Prefer\": reference the example library first");
        EN.put("set.ruleArchive", "Include \"Archive\": outputs go to the result library");
        EN.put("set.ruleRead", "Include \"Requirements\": read the requirement library first");
        EN.put("set.ruleExtra", "Extra rules (appended)");
        EN.put("set.mapping", "Folder mappings");
        EN.put("set.mapDir", "Directory prefix");
        EN.put("set.mapCat", "Category");
        EN.put("set.mapHint", "A path starting with the prefix matches; longest prefix wins. Category may be built-in or custom.");
        EN.put("classify.badFormat", "Unexpected model reply: {0}");
        EN.put("classify.progress", "AI classifying {0}/{1}");

        EN.put("tb.ignore", "Ignore this document");
        EN.put("notify.ignored", "Ignored \"{0}\"; restore it in Settings → Tools → EasyMDAssistant AI → Ignored documents");
        EN.put("tree.moveTo", "Move to category folder…");
        EN.put("tree.moveTo.msg", "Choose the target folder (the document will be moved under it in the project root):");
        EN.put("tree.moveTo.title", "Move to category folder");
        EN.put("notify.moved", "Moved to {0}/ : {1}");
        EN.put("notify.moveFail", "Move failed: {0}");
        EN.put("notify.alreadyThere", "\"{0}\" is already in that folder");
        EN.put("chat.context.tip", "Pick the reference document for the conversation: its full text (up to 15000 chars) is sent to the AI");
        EN.put("chat.context.toggle", "Collapse / expand the context selector");
        EN.put("chat.area.toggle", "Collapse / expand the chat area (context and history)");
        EN.put("chat.area.collapsedTitle", " Ask AI (chat area collapsed)");
        EN.put("set.sectionLibs", "Category library (global)");
        EN.put("set.catName", "Category name");
        EN.put("set.libReset", "Reset to defaults");
        EN.put("set.libScopeHint", "Knowledge dirs are per-project (new projects inherit the global defaults); \"Save as global default\" applies the current selection to all projects.");
        EN.put("set.saveGlobal", "Save as global default (all projects)");
        EN.put("set.useGlobal", "Restore global defaults");
        EN.put("set.disabled", "(disabled)");
        EN.put("set.sectionIgnore", "Ignored documents (this project)");
        EN.put("set.ignorePath", "Document");
        EN.put("set.ignoreTime", "Ignored at");
        EN.put("set.restoreSel", "Restore selected");
        EN.put("set.restoreAll", "Restore all");
        EN.put("set.ignoreHint", "Ignored documents are excluded from scans; rescan after restoring.");
        EN.put("notify.globalSaved", "Saved as global default; all projects will use these knowledge dirs");
        EN.put("notify.globalRestored", "Restored global defaults (this project)");
        EN.put("set.aiHint", "Base URL can be domain only; /v1/chat/completions or /v1/messages is appended automatically.");
        EN.put("set.hint.line1", "Providers: OpenAI-compatible (OpenAI / DeepSeek / GLM / OneAPI...) or Anthropic (Claude).");
        EN.put("set.hint.line2", "Examples: https://api.openai.com · https://api.deepseek.com · https://open.bigmodel.cn/api/paas/v4 · https://api.anthropic.com");

        ZH.put("set.hint.line1", "支持两种协议：OpenAI 兼容（OpenAI / DeepSeek / 智谱 GLM / OneAPI 等）与 Anthropic（Claude 官方或兼容网关）。");
        ZH.put("set.hint.line2", "地址示例：https://api.openai.com · https://api.deepseek.com · https://open.bigmodel.cn/api/paas/v4 · https://api.anthropic.com");
    }

    private I18n() {
    }

    public static String t(String key) {
        if (isEn()) {
            String v = EN.get(key);
            if (v != null) {
                return v;
            }
        }
        return ZH.getOrDefault(key, key);
    }

    /** 带 {0}/{1} 占位符的格式化。 */
    public static String f(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    public static boolean isEn() {
        return LANG_EN.equals(effective());
    }

    public static String effective() {
        String pref = null;
        try {
            pref = AiSettings.getInstance().getUiLanguage();
        } catch (Throwable ignore) {
        }
        if (LANG_ZH.equals(pref) || LANG_EN.equals(pref)) {
            return pref;
        }
        return Locale.getDefault().getLanguage().startsWith("zh") ? LANG_ZH : LANG_EN;
    }
}
