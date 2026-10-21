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
package org.jackhuang.hmcl.community.dashboard.builtin;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.preflight.PreflightConfig;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.ui.MemoryStatusBar;
import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javafx.beans.value.ChangeListener;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.community.env.CommunityPaths.localHome;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 存储 / 内存卡：选中实例所在分区的可用空间与系统物理内存。
///
/// 磁盘只在卡片可见时每 30 秒刷新一次；内存复用上游 [MemoryStatusBar] 的自动更新属性，
/// 不自己维护轮询线程。阈值读取 08 的 `preflight.warnDiskMb / minDiskMb`，避免两套口径。
@NotNullByDefault
public final class StorageWidget extends DashboardWidget {

    private final MemoryStatusBar memoryBar = new MemoryStatusBar();
    private final Label diskLabel = BuiltinWidgets.body("");
    private final Label memoryLabel = BuiltinWidgets.body("");
    private final Label warnLabel = BuiltinWidgets.caption("");
    private final Label pathLabel = BuiltinWidgets.caption("");
    private final Timeline timer = new Timeline(new KeyFrame(Duration.seconds(30), event -> requestRefresh()));

    private @Nullable Tooltip pathTooltip;

    /// 内存属性监听器；必须持有同一实例才能移除。
    private final ChangeListener<@Nullable PhysicalMemoryStatus> memoryListener =
            (observable, oldValue, newValue) -> updateMemory(newValue);

    /// 构造存储卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public StorageWidget(@NotNull DashboardWidgetDescriptor descriptor,
                         @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    @Override
    @NotNull
    protected Node createContent() {
        warnLabel.setVisible(false);
        warnLabel.setManaged(false);
        pathLabel.setVisible(false);
        pathLabel.setManaged(false);

        memoryBar.memoryStatusProperty().addListener(memoryListener);
        onDispose(() -> memoryBar.memoryStatusProperty().removeListener(memoryListener));
        updateMemory(memoryBar.getMemoryStatus());

        VBox box = new VBox(2, diskLabel, memoryLabel, warnLabel, pathLabel);
        box.setFillWidth(true);
        box.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(pathLabel, Priority.ALWAYS);

        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
        onDispose(timer::stop);

        widthProperty().addListener((observable, oldValue, newValue) -> updatePathVisibility());
        heightProperty().addListener((observable, oldValue, newValue) -> updatePathVisibility());
        context.selectedInstanceId().addListener(selectedListener);
        onDispose(() -> context.selectedInstanceId().removeListener(selectedListener));
        return box;
    }

    /// 选中实例变化时重读磁盘。
    private final javafx.beans.value.ChangeListener<@Nullable String> selectedListener =
            (observable, oldValue, newValue) -> requestRefresh();

    @Override
    protected void refreshAsync() {
        if (!isEffectivelyVisible()) {
            return;
        }
        loadAsync(this::loadSnapshot, this::apply);
    }

    @NotNull
    private Snapshot loadSnapshot() {
        String selected = context.selectedInstanceId().getValue();
        HMCLGameRepository repository = context.repository().get();
        PreflightConfig config;
        try {
            config = PreflightConfig.load();
        } catch (Throwable e) {
            LOG.warning("Failed to read disk thresholds, using defaults", e);
            config = new PreflightConfig();
        }
        Path path;
        boolean hasInstance = repository != null && selected != null;
        if (hasInstance) {
            Path runDirectory = repository.getRunDirectory(selected);
            path = Files.exists(runDirectory) ? runDirectory : localHome();
            hasInstance = path.equals(runDirectory);
        } else {
            path = localHome();
        }
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            FileStore store = Files.getFileStore(path);
            return new Snapshot(store.getUsableSpace(), store.getTotalSpace(),
                    config.warnDiskMb(), config.minDiskMb(), hasInstance, path, null);
        } catch (Throwable e) {
            LOG.warning("Failed to read file store of " + path, e);
            return new Snapshot(-1, -1, config.warnDiskMb(), config.minDiskMb(), hasInstance, path, e);
        }
    }

    private void apply(@NotNull Snapshot snapshot) {
        if (snapshot.usable() < 0) {
            diskLabel.setText(i18n("community.storage.unavailable"));
            diskLabel.setTooltip(new Tooltip(snapshot.error() == null ? "" : snapshot.error().getMessage()));
            warnLabel.setVisible(false);
            warnLabel.setManaged(false);
        } else {
            diskLabel.setText(i18n("community.storage.disk",
                    DataSizeUnit.format(snapshot.usable()),
                    DataSizeUnit.format(snapshot.total())));
            long usableMb = snapshot.usable() / 1024L / 1024L;
            if (usableMb < snapshot.minDiskMb()) {
                warnLabel.setText(i18n("community.storage.disk_critical"));
                warnLabel.setStyle("-fx-text-fill: -monet-error;");
                warnLabel.setVisible(true);
                warnLabel.setManaged(true);
            } else if (usableMb < snapshot.warnDiskMb()) {
                warnLabel.setText(i18n("community.storage.disk_low"));
                warnLabel.setStyle("-fx-text-fill: -monet-tertiary;");
                warnLabel.setVisible(true);
                warnLabel.setManaged(true);
            } else {
                warnLabel.setVisible(false);
                warnLabel.setManaged(false);
            }
        }

        pathLabel.setText(snapshot.hasInstance()
                ? i18n("community.storage.path", snapshot.path().getFileName())
                : i18n("community.storage.no_instance"));
        if (pathTooltip != null) {
            pathTooltip.setText(snapshot.path().toString());
        } else {
            pathTooltip = new Tooltip(snapshot.path().toString());
            Tooltip.install(pathLabel, pathTooltip);
        }
        updatePathVisibility();
    }

    private void updateMemory(@Nullable PhysicalMemoryStatus status) {
        if (status == null || status.total() <= 0) {
            memoryLabel.setText(i18n("community.storage.memory_unknown"));
            return;
        }
        String used = status.hasAvailable() ? DataSizeUnit.format(status.getUsed()) : "—";
        memoryLabel.setText(i18n("community.storage.memory", used, DataSizeUnit.format(status.total())));
    }

    private void updatePathVisibility() {
        boolean visible = getWidth() >= 480 || getHeight() >= 200;
        pathLabel.setVisible(visible);
        pathLabel.setManaged(visible);
    }

    private boolean isEffectivelyVisible() {
        if (!isVisible() || getScene() == null || getScene().getWindow() == null) {
            return false;
        }
        Node node = this;
        while (node != null) {
            if (!node.isVisible()) {
                return false;
            }
            node = node.getParent();
        }
        return Platform.isFxApplicationThread();
    }

    /// 一次磁盘快照。
    private record Snapshot(long usable, long total, int warnDiskMb, int minDiskMb,
                            boolean hasInstance, @NotNull Path path, @Nullable Throwable error) {
    }
}
