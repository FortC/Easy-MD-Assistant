package com.aitools.mdassistant.ai;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 代理解析（按优先级）：
 * 1. JVM 默认 ProxySelector —— IDE 环境下通常已接入 IDE 的 HTTP Proxy 设置（含 PAC）；
 * 2. 环境变量 HTTPS_PROXY / HTTP_PROXY / ALL_PROXY（cc switch / Claude Code 等工具的惯例）；
 * 3. 旧版 HttpConfigurable 的 USE_HTTP_PROXY / PROXY_HOST / PROXY_PORT 字段（反射，失败降级）。
 * 本地地址（localhost / 127.*）始终直连。
 *
 * 注意：PAC/自动检测模式下 ProxySelector.select() 会同步做 WPAD 探测（下载并执行 PAC 脚本），
 * 最长可阻塞数分钟。因此第 1 步绝不在 EDT 上执行，并在后台线程上以 5s 超时兜底，
 * 超时后跳过它降级到 2/3 步（通常为直连）。
 */
public final class ProxyResolver {

    private static final long JVM_SELECT_TIMEOUT_MS = 5_000;

    /** 守护线程池：超时取消后可能仍有个别线程阻塞在原生 WPAD 调用里，守护线程不影响 IDE 退出。 */
    private static final ExecutorService SELECT_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mda-proxy-select");
        t.setDaemon(true);
        return t;
    });

    private ProxyResolver() {
    }

    /** 返回 HTTP 代理；null = 直连。 */
    public static Proxy resolve(String targetUrl) {
        if (targetUrl != null && !isLocal(targetUrl)) {
            Proxy p = viaJvmSelector(targetUrl);
            if (p != null) {
                return p;
            }
        }
        Proxy p = fromEnv();
        if (p != null) {
            return p;
        }
        return fromIdeSettings();
    }

    private static Proxy viaJvmSelector(String url) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            return null; // UI 线程上禁止阻塞式网络探测
        }
        Future<Proxy> future = SELECT_POOL.submit(() -> {
            for (Proxy p : ProxySelector.getDefault().select(URI.create(url))) {
                if (p != null && p.type() == Proxy.Type.HTTP) {
                    return p;
                }
            }
            return null;
        });
        try {
            return future.get(JVM_SELECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ignore) {
            future.cancel(true);
            return null; // 解析超时/异常 → 降级
        }
    }

    public static String describe(String targetUrl) {
        Proxy p = resolve(targetUrl);
        if (p == null) {
            return "direct";
        }
        return "proxy " + p.address();
    }

    private static boolean isLocal(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.equalsIgnoreCase("localhost")
                    || host.startsWith("127.") || host.startsWith("[::1]");
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static Proxy fromIdeSettings() {
        try {
            Class<?> c = Class.forName("com.intellij.util.net.HttpConfigurable");
            Object hc = c.getMethod("getInstance").invoke(null);
            boolean enabled = Boolean.parseBoolean(String.valueOf(c.getField("USE_HTTP_PROXY").get(hc)));
            Object hostObj = c.getField("PROXY_HOST").get(hc);
            int port = c.getField("PROXY_PORT").getInt(hc);
            String host = hostObj == null ? "" : String.valueOf(hostObj).trim();
            if (enabled && !host.isEmpty() && port > 0) {
                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            }
        } catch (Throwable ignore) {
            // 旧/新版本字段差异时降级
        }
        return null;
    }

    private static Proxy fromEnv() {
        String[] names = {"HTTPS_PROXY", "https_proxy", "ALL_PROXY", "all_proxy",
                "HTTP_PROXY", "http_proxy"};
        for (String name : names) {
            String v = System.getenv(name);
            if (v == null || v.trim().isEmpty()) {
                continue;
            }
            String u = v.trim();
            int scheme = u.indexOf("://");
            if (scheme >= 0) {
                u = u.substring(scheme + 3);
            }
            int slash = u.indexOf('/');
            if (slash >= 0) {
                u = u.substring(0, slash);
            }
            int at = u.lastIndexOf('@'); // 忽略 user:pass@ 认证前缀
            if (at >= 0) {
                u = u.substring(at + 1);
            }
            int colon = u.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                int port = Integer.parseInt(u.substring(colon + 1));
                if (port > 0) {
                    return new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress(u.substring(0, colon), port));
                }
            } catch (NumberFormatException ignore) {
                // 尝试下一个变量
            }
        }
        return null;
    }
}
