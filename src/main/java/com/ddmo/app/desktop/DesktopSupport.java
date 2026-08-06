package com.ddmo.app.desktop;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 桌面模式辅助：单实例、打开浏览器、系统托盘退出。
 * 仅在 {@code -Ddesktop.mode=true} 时由主启动类调用。
 */
public final class DesktopSupport {

    private static final AtomicReference<String> LOCAL_URL = new AtomicReference<>("http://localhost:8080");
    private static FileChannel lockChannel;
    private static FileLock fileLock;
    private static TrayIcon trayIcon;

    private DesktopSupport() {
    }

    /**
     * 解析监听端口：系统属性 / 命令行 / 默认 8080。
     */
    public static int resolvePort(String[] args) {
        String prop = System.getProperty("server.port");
        if (prop != null && !prop.isBlank()) {
            return Integer.parseInt(prop.trim());
        }
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--server.port=")) {
                    return Integer.parseInt(arg.substring("--server.port=".length()).trim());
                }
            }
        }
        return 8080;
    }

    /**
     * 若本地服务已在运行：打开浏览器并退出当前进程（避免再起一个 Spring/JVM）。
     * 若未运行：抢占单实例锁；抢不到则短暂等待后重试探测。
     *
     * @return true 表示当前进程应继续启动 Spring；false 表示已处理完毕应结束 main
     */
    public static boolean claimOrHandoff(int port) {
        String url = "http://localhost:" + port;
        LOCAL_URL.set(url);

        if (isPortOpen(port)) {
            log("service already running on " + port + ", open browser and exit");
            openBrowserAndPause(url);
            return false;
        }

        if (!tryAcquireLock()) {
            log("lock held by another instance, waiting for service...");
            if (waitForPort(port, 20_000)) {
                openBrowserAndPause(url);
                return false;
            }
            log("another instance seems stuck; open browser anyway if possible");
            openBrowserAndPause(url);
            return false;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(DesktopSupport::release, "show-desktop-shutdown"));
        installTray(url);
        return true;
    }

    public static void onServerReady(String url) {
        if (url != null && !url.isBlank()) {
            LOCAL_URL.set(url);
        }
        String open = LOCAL_URL.get();
        openBrowser(open);
        if (trayIcon != null) {
            try {
                trayIcon.displayMessage("Show", "服务已启动，正在打开应用窗口", TrayIcon.MessageType.INFO);
            } catch (Exception ignored) {
                // some platforms do not support balloon
            }
        }
    }

    /**
     * 优先用 Edge/Chrome 的应用模式（独立窗口、无地址栏/标签），失败再回退系统默认浏览器。
     */
    public static void openBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (tryOpenAppMode(url)) {
            return;
        }
        openSystemBrowserFallback(url);
    }

    /**
     * Chromium 系 --app=URL：独立应用窗口，接近桌面客户端体验。
     */
    private static boolean tryOpenAppMode(String url) {
        for (Path browser : resolveChromiumBrowsers()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        browser.toString(),
                        "--app=" + url,
                        "--new-window"
                );
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.start();
                log("opened app-mode window via " + browser.getFileName() + " -> " + url);
                return true;
            } catch (Exception e) {
                log("app-mode failed for " + browser + ": " + e.getMessage());
            }
        }
        // PATH 中的 msedge / chrome（部分机器无完整路径）
        for (String cmd : new String[]{"msedge", "chrome", "google-chrome", "chromium"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--app=" + url, "--new-window");
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.start();
                log("opened app-mode window via PATH command: " + cmd);
                return true;
            } catch (Exception ignored) {
                // try next
            }
        }
        return false;
    }

    private static java.util.List<Path> resolveChromiumBrowsers() {
        java.util.LinkedHashSet<Path> candidates = new java.util.LinkedHashSet<>();
        String pf = System.getenv("ProgramFiles");
        String pf86 = System.getenv("ProgramFiles(x86)");
        String local = System.getenv("LOCALAPPDATA");

        addIfExists(candidates, pf, "Microsoft\\Edge\\Application\\msedge.exe");
        addIfExists(candidates, pf86, "Microsoft\\Edge\\Application\\msedge.exe");
        addIfExists(candidates, local, "Microsoft\\Edge\\Application\\msedge.exe");

        addIfExists(candidates, pf, "Google\\Chrome\\Application\\chrome.exe");
        addIfExists(candidates, pf86, "Google\\Chrome\\Application\\chrome.exe");
        addIfExists(candidates, local, "Google\\Chrome\\Application\\chrome.exe");

        addIfExists(candidates, pf, "Microsoft\\Edge Beta\\Application\\msedge.exe");
        addIfExists(candidates, pf, "Microsoft\\Edge Dev\\Application\\msedge.exe");
        return new java.util.ArrayList<>(candidates);
    }

    private static void addIfExists(java.util.Set<Path> out, String root, String relative) {
        if (root == null || root.isBlank()) {
            return;
        }
        Path p = Path.of(root, relative);
        if (Files.isRegularFile(p)) {
            out.add(p);
        }
    }

    private static void openSystemBrowserFallback(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log("fallback: system default browser -> " + url);
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                log("fallback: rundll32 FileProtocolHandler -> " + url);
            } else {
                System.err.println("[desktop] cannot open browser, visit: " + url);
            }
        } catch (Exception e) {
            System.err.println("[desktop] open browser failed: " + e.getMessage() + ", visit: " + url);
        }
    }

    /** Open UI then brief pause so the OS can hand off before process exits. */
    private static void openBrowserAndPause(String url) {
        openBrowser(url);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean tryAcquireLock() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".show");
            Files.createDirectories(dir);
            Path lockFile = dir.resolve("desktop.lock");
            lockChannel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            fileLock = lockChannel.tryLock();
            if (fileLock == null) {
                closeQuietly(lockChannel);
                lockChannel = null;
                return false;
            }
            return true;
        } catch (Exception e) {
            log("acquire lock failed: " + e.getMessage());
            return false;
        }
    }

    private static void release() {
        try {
            if (trayIcon != null && SystemTray.isSupported()) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
        } catch (Exception ignored) {
        }
        trayIcon = null;
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } catch (Exception ignored) {
        }
        fileLock = null;
        closeQuietly(lockChannel);
        lockChannel = null;
    }

    private static void installTray(String url) {
        if (!SystemTray.isSupported()) {
            log("SystemTray not supported; close via Task Manager if needed");
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("Open UI");
            openItem.addActionListener(e -> openBrowser(LOCAL_URL.get()));
            MenuItem exitItem = new MenuItem("Exit Show");
            exitItem.addActionListener(e -> {
                release();
                System.exit(0);
            });
            menu.add(openItem);
            menu.addSeparator();
            menu.add(exitItem);

            Image image = createTrayImage();
            trayIcon = new TrayIcon(image, "Show - running (app window; right-click to exit)", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> openBrowser(LOCAL_URL.get()));
            SystemTray.getSystemTray().add(trayIcon);
            log("tray installed; right-click tray icon to exit");
        } catch (AWTException e) {
            log("install tray failed: " + e.getMessage());
        }
    }

    private static Image createTrayImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(79, 70, 229));
            g.fillRoundRect(0, 0, size - 1, size - 1, 4, 4);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.drawString("S", 3, 12);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean waitForPort(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(port)) {
                return true;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void log(String msg) {
        System.out.println("[desktop] " + msg);
        try {
            Path log = Path.of(System.getProperty("user.home"), ".show", "desktop.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log,
                    java.time.LocalDateTime.now() + " " + msg + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
