/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.community.java;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.download.java.disco.DiscoFetchJavaListTask;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaRemoteVersion;
import org.jackhuang.hmcl.download.java.disco.DiscoRemoteFileInfo;
import org.jackhuang.hmcl.download.java.disco.DiscoResult;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “Java 推荐”面板（步骤 10），由[步骤 14 的 `java-recommend` 卡片]嵌入使用。
///
/// 职责：
/// - 按当前平台抓取 Disco 列表，给出“推荐哪个 Java”（偏好发行版优先、LTS 优先、JRE 优先）；
/// - 一键安装：走与上游 `JavaDownloadDialog` 相同的下载 → `JavaManager.getInstallJavaTask` 链路；
/// - 只读取 [JavaPreference]，**不**解析 `community-settings.json`。
///
/// 约束：
/// - 构造时**不**发网络请求；调用 [#refresh()] 才加载（避免首页挂载时拖慢启动）；
/// - 不改上游 `JavaDownloadDialog` 的默认选中/排序，界面底部如实说明；
/// - 文案不得出现“更快/加速/性能提升”一类未经基准验证的表述。
@NotNullByDefault
public final class JavaRecommendPane extends VBox {

    /// 最多同时展示的发行版数量（偏好项 + 其它）。
    private static final int MAX_DISTRIBUTIONS = 3;

    /// 推荐的最低 Java 大版本（启动器自身要求 17+）。
    private static final int MIN_RECOMMENDED_MAJOR = 17;

    /// 当前平台。
    private final Platform platform = Platform.SYSTEM_PLATFORM;

    /// 偏好选择框。
    private final JFXComboBox<JavaChoice> preferenceBox = new JFXComboBox<>();

    /// 推荐卡片。
    private final ComponentList recommendedCard = new ComponentList();

    /// 其它可安装卡片。
    private final ComponentList othersCard = new ComponentList();

    /// 状态/错误提示。
    private final Label status = new Label();

    /// 镜像说明。
    private final Label mirrorNote = new Label();

    /// 上游限制说明。
    private final Label limitNote = new Label();

    /// 刷新按钮。
    private final JFXButton refreshButton = FXUtils.newBorderButton(i18n("community.java.action.refresh"));

    /// 已加载的候选。
    private final Map<DiscoJavaDistribution, Candidate> candidates = new EnumMap<>(DiscoJavaDistribution.class);

    /// 是否正在加载。
    private boolean loading;

    /// 当前一轮加载的序号（丢弃过期回调）。
    private final AtomicInteger generation = new AtomicInteger();

    /// 候选：发行版 + 选中的版本 + 包类型。
    ///
    /// @param distribution 发行版
    /// @param version 远端版本
    /// @param packageType 包类型
    private record Candidate(@NotNull DiscoJavaDistribution distribution,
                             @NotNull DiscoJavaRemoteVersion version,
                             @NotNull JavaPackageType packageType) {
    }

    /// 构造面板（不发网络请求）。
    public JavaRecommendPane() {
        getStyleClass().add("community-java-recommend");
        setSpacing(8);

        Label title = new Label(i18n("community.java.title"));
        title.getStyleClass().add("title-label");

        Label platformLabel = new Label(i18n("community.java.platform", osName(), platform.getArchitecture().getDisplayName()));
        platformLabel.getStyleClass().add("hint");

        preferenceBox.setItems(FXCollections.observableArrayList(JavaChoice.values()));
        preferenceBox.setConverter(FXUtils.stringConverter(choice -> i18n(choice.i18nKey())));
        preferenceBox.getSelectionModel().select(JavaPreference.get().choice());
        preferenceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                JavaPreference.setDistribution(newValue.distribution());
        });
        FXUtils.setLimitWidth(preferenceBox, 180);

        refreshButton.setOnAction(event -> refresh());

        HBox preferenceRow = new HBox(8);
        Label preferenceLabel = new Label(i18n("community.java.preference"));
        preferenceRow.getChildren().setAll(preferenceLabel, preferenceBox, refreshButton);

        status.getStyleClass().add("hint");
        status.setWrapText(true);

        mirrorNote.setWrapText(true);
        mirrorNote.getStyleClass().addAll("hint", "info");
        mirrorNote.setText(i18n("community.java.mirror.note", CommunityJavaMirror.effectiveApiRoot()));
        mirrorNote.setVisible(JavaPreference.get().mirrorEnabled());
        mirrorNote.setManaged(mirrorNote.isVisible());

        limitNote.setWrapText(true);
        limitNote.getStyleClass().add("hint");
        limitNote.setText(i18n("community.java.limit.upstream_dialog"));

        getChildren().setAll(title, platformLabel, preferenceRow, mirrorNote, status, recommendedCard, othersCard, limitNote);
    }

    /// 按当前平台与偏好加载列表。
    ///
    /// 可重复调用；加载中重复调用会被忽略。
    public void refresh() {
        if (loading)
            return;

        int current = generation.incrementAndGet();
        loading = true;
        candidates.clear();
        recommendedCard.getContent().clear();
        othersCard.getContent().clear();
        status.setText(i18n("community.java.loading"));

        JavaPreference.Preference preference = JavaPreference.get();
        preferenceBox.getSelectionModel().select(preference.choice());

        List<DiscoJavaDistribution> supported = new ArrayList<>();
        for (DiscoJavaDistribution distribution : DiscoJavaDistribution.values()) {
            if (distribution.isSupport(platform))
                supported.add(distribution);
        }

        if (supported.isEmpty()) {
            loading = false;
            status.setText(i18n("community.java.error.list_failed"));
            return;
        }

        DiscoJavaDistribution preferred = preference.distribution();
        DiscoJavaDistribution primary = preferred != null && supported.contains(preferred)
                ? preferred
                : supported.get(0);
        boolean fallback = preferred != null && !supported.contains(preferred);

        List<DiscoJavaDistribution> targets = new ArrayList<>();
        targets.add(primary);
        for (DiscoJavaDistribution distribution : supported) {
            if (targets.size() >= MAX_DISTRIBUTIONS)
                break;
            if (!targets.contains(distribution))
                targets.add(distribution);
        }

        AtomicInteger remaining = new AtomicInteger(targets.size());
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();

        for (DiscoJavaDistribution distribution : targets) {
            new DiscoFetchJavaListTask(downloadProvider, distribution, platform)
                    .setExecutor(Schedulers.io())
                    .thenApplyAsync(versions -> selectCandidate(distribution, versions))
                    .whenComplete(Schedulers.javafx(), (candidate, exception) -> {
                        if (generation.get() != current)
                            return;

                        if (exception != null) {
                            LOG.warning("Failed to load Java list for " + distribution, exception);
                        } else if (candidate != null) {
                            candidates.put(distribution, candidate);
                        }

                        if (remaining.decrementAndGet() == 0)
                            render(primary, fallback, preferred);
                    })
                    .start();
        }
    }

    /// 返回是否已成功加载过列表。
    ///
    /// @return 有候选返回 `true`
    public boolean isLoaded() {
        return !candidates.isEmpty();
    }

    /// 渲染推荐/其它列表。
    private void render(@NotNull DiscoJavaDistribution primary, boolean fallback,
                        @Nullable DiscoJavaDistribution preferred) {
        loading = false;

        Candidate recommended = candidates.get(primary);
        if (recommended != null) {
            recommendedCard.getContent().setAll(
                    ComponentList.createComponentListTitle(i18n("community.java.recommended")),
                    buildRow(recommended));
        }

        List<Candidate> others = new ArrayList<>();
        for (Map.Entry<DiscoJavaDistribution, Candidate> entry : candidates.entrySet()) {
            if (entry.getKey() != primary)
                others.add(entry.getValue());
        }
        others.sort(Comparator
                .comparing((Candidate candidate) -> candidate.version().isLTS()).reversed()
                .thenComparing(candidate -> candidate.version().getJdkVersion(), Comparator.reverseOrder()));

        if (!others.isEmpty()) {
            List<Node> nodes = new ArrayList<>();
            nodes.add(ComponentList.createComponentListTitle(i18n("community.java.others")));
            for (Candidate candidate : others)
                nodes.add(buildRow(candidate));
            othersCard.getContent().setAll(nodes);
        }

        if (candidates.isEmpty()) {
            status.setText(i18n("community.java.error.list_failed"));
        } else if (fallback && preferred != null) {
            status.setText(i18n("community.java.fallback", preferred.getDisplayName()));
        } else {
            status.setText("");
        }
    }

    /// 构造一行。
    private Node buildRow(@NotNull Candidate candidate) {
        LinePane pane = new LinePane();
        pane.setTitle(i18n("community.java.line.title",
                candidate.distribution().getDisplayName(),
                candidate.version().getJdkVersion()));

        List<String> tags = new ArrayList<>();
        if (candidate.version().isLTS())
            tags.add(i18n("community.java.lts"));
        tags.add(candidate.packageType().getDisplayName());
        if (candidate.version().isJavaFXBundled())
            tags.add("FX");
        pane.setSubtitle(String.join(" · ", tags));

        JFXButton installButton = FXUtils.newBorderButton(i18n("community.java.action.install"));
        installButton.setOnAction(event -> install(candidate));
        pane.setRight(installButton);
        return pane;
    }

    /// 选择某发行版的最佳候选：LTS 优先、大版本高优先、JRE 优先，且大版本 ≥ 17。
    ///
    /// @param distribution 发行版
    /// @param versions 列表
    /// @return 候选；没有可用版本时返回 `null`
    @Nullable
    private static Candidate selectCandidate(@NotNull DiscoJavaDistribution distribution,
                                             @NotNull Map<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>> versions) {
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>> entry : versions.entrySet()) {
            JavaPackageType packageType = entry.getKey();
            for (Map.Entry<Integer, DiscoJavaRemoteVersion> versionEntry : entry.getValue().entrySet()) {
                if (versionEntry.getKey() < MIN_RECOMMENDED_MAJOR)
                    continue;
                candidates.add(new Candidate(distribution, versionEntry.getValue(), packageType));
            }
        }

        if (candidates.isEmpty()) {
            // 没有 17+ 时退回任意可用版本（老游戏/特殊平台）。
            for (Map.Entry<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>> entry : versions.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    Map.Entry<Integer, DiscoJavaRemoteVersion> last = entry.getValue().lastEntry();
                    candidates.add(new Candidate(distribution, last.getValue(), entry.getKey()));
                }
            }
        }

        return candidates.stream()
                .min(Comparator
                        .comparing((Candidate candidate) -> candidate.version().isLTS() ? 0 : 1)
                        .thenComparing(candidate -> -candidate.version().getJdkVersion())
                        .thenComparing(candidate -> packageRank(candidate.packageType())))
                .orElse(null);
    }

    /// 包类型优先级：JRE 体积最小，优先；其次 JDK；FX 包最后。
    private static int packageRank(@NotNull JavaPackageType packageType) {
        return switch (packageType) {
            case JRE -> 0;
            case JDK -> 1;
            case JREFX -> 2;
            case JDKFX -> 3;
        };
    }

    /// 一键安装。
    ///
    /// 流程与上游 `JavaDownloadDialog` 一致：取 `pkg_info` → 下载压缩包（带校验）→
    /// `JavaManager.getInstallJavaTask` 注册。差异：不弹上游命名向导，按上游默认命名规则自动命名；
    /// 若同名已安装则提示，不重复下载。
    private void install(@NotNull Candidate candidate) {
        String name = defaultName(candidate);
        if (JavaManager.REPOSITORY.isInstalled(platform, name)) {
            Controllers.dialog(i18n("community.java.error.exists", name),
                    i18n("community.java.title"), MessageDialogPane.MessageType.WARNING);
            return;
        }

        Path targetFile;
        try {
            targetFile = Files.createTempFile("lmcl-java-", "." + candidate.version().getArchiveType());
        } catch (IOException e) {
            LOG.warning("Failed to create temp file for Java download", e);
            Controllers.dialog(i18n("community.java.error.install_failed"), i18n("community.java.title"),
                    MessageDialogPane.MessageType.ERROR);
            return;
        }
        targetFile.toFile().deleteOnExit();

        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
        String javaVersion = candidate.version().getJavaVersion();
        JavaInfo info = new JavaInfo(platform, javaVersion, candidate.distribution().getVendor());
        Map<String, Object> updateInfo = new LinkedHashMap<>();
        updateInfo.put("type", "disco");
        updateInfo.put("info", candidate.version());

        Path finalTargetFile = targetFile;
        Task<Void> task = new GetTask(downloadProvider.injectURLWithCandidates(
                candidate.version().getLinks().pkgInfoUri()))
                .setExecutor(Schedulers.io())
                .thenComposeAsync(json -> {
                    DiscoResult<DiscoRemoteFileInfo> result = JsonUtils.fromNonNullJson(
                            json, DiscoResult.typeOf(DiscoRemoteFileInfo.class));
                    if (result.getResult().size() != 1)
                        throw new IOException("Illegal result: " + json);

                    DiscoRemoteFileInfo fileInfo = result.getResult().get(0);
                    if (StringUtils.isNotBlank(fileInfo.checksumType())
                            && !fileInfo.checksumType().equals("sha1")
                            && !fileInfo.checksumType().equals("sha256")
                            && !fileInfo.checksumType().equals("md5"))
                        throw new IOException("Unsupported checksum type: " + fileInfo.checksumType());
                    if (StringUtils.isBlank(fileInfo.directDownloadUri()))
                        throw new IOException("Missing download URI: " + json);

                    Task<FileDownloadTask.IntegrityCheck> integrityCheckTask;
                    if (StringUtils.isNotBlank(fileInfo.checksum()))
                        integrityCheckTask = Task.completed(
                                new FileDownloadTask.IntegrityCheck(fileInfo.checksumType(), fileInfo.checksum()));
                    else if (StringUtils.isNotBlank(fileInfo.checksumUri()))
                        integrityCheckTask = new GetTask(downloadProvider.injectURLWithCandidates(fileInfo.checksumUri()))
                                .thenApplyAsync(checksum -> {
                                    checksum = checksum.trim();
                                    int idx = checksum.indexOf(' ');
                                    if (idx > 0)
                                        checksum = checksum.substring(0, idx);
                                    return new FileDownloadTask.IntegrityCheck(fileInfo.checksumType(), checksum);
                                });
                    else
                        integrityCheckTask = Task.completed(null);

                    return integrityCheckTask
                            .thenComposeAsync(integrityCheck -> new FileDownloadTask(
                                    downloadProvider.injectURLWithCandidates(fileInfo.directDownloadUri()),
                                    finalTargetFile, integrityCheck).setName(fileInfo.fileName()))
                            .thenSupplyAsync(() -> finalTargetFile);
                })
                .thenComposeAsync(file -> JavaManager.getInstallJavaTask(platform, name, updateInfo, file))
                .whenComplete(Schedulers.javafx(), (installed, exception) -> {
                    try {
                        Files.deleteIfExists(finalTargetFile);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete temp Java archive: " + finalTargetFile, e);
                    }

                    if (exception != null) {
                        Throwable resolved = org.jackhuang.hmcl.util.Lang.resolveException(exception);
                        LOG.warning("Failed to install Java " + name, resolved);
                        if (!(resolved instanceof java.util.concurrent.CancellationException)) {
                            Controllers.dialog(DownloadProviders.localizeErrorMessage(resolved),
                                    i18n("community.java.error.install_failed"),
                                    MessageDialogPane.MessageType.ERROR);
                        }
                    } else {
                        Controllers.dialog(i18n("community.java.install.done", name),
                                i18n("community.java.title"), MessageDialogPane.MessageType.SUCCESS);
                        refresh();
                    }
                });

        Controllers.taskDialog(task, i18n("community.java.install.title"), TaskCancellationAction.NORMAL);
    }

    /// 上游同款默认名：`<api 发行版>-<java 版本>-<包类型小写>`。
    private static String defaultName(@NotNull Candidate candidate) {
        String javaVersion = candidate.version().getJavaVersion();
        int idx = javaVersion.indexOf('+');
        if (idx > 0)
            javaVersion = javaVersion.substring(0, idx);
        return candidate.distribution().getApiParameter() + "-" + javaVersion + "-"
                + candidate.packageType().name().toLowerCase(Locale.ROOT);
    }

    /// 返回当前平台的可读名称。
    private String osName() {
        OperatingSystem os = platform.getOperatingSystem();
        return switch (os) {
            case WINDOWS -> "Windows";
            case LINUX -> "Linux";
            case MACOS -> "macOS";
            default -> os.getCheckedName();
        };
    }
}
