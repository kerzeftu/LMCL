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
package org.jackhuang.hmcl.community.jvm;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// [JvmArgWriter] 的纯函数部分（组合/合并/移除）测试。
@NotNullByDefault
public final class JvmArgWriterTest {

    /// 跟随上游/自定义不生成内容。
    @Test
    public void composeOnlyForG1AndZgc() {
        assertEquals("", JvmArgWriter.compose(JvmPreset.UPSTREAM, 4096, null));
        assertEquals("", JvmArgWriter.compose(JvmPreset.CUSTOM, 4096, null));
        assertEquals("-Xms4096m -Xmx4096m", JvmArgWriter.compose(JvmPreset.G1, 4096, null));
        assertEquals("-Xms12288m -Xmx12288m -XX:+UseZGC",
                JvmArgWriter.compose(JvmPreset.ZGC, 12288, null));
    }

    /// 追加 JEP 346 参数。
    @Test
    public void composeAppendsExtraArgs() {
        assertEquals("-Xms4096m -Xmx4096m -XX:MinHeapFreeRatio=5",
                JvmArgWriter.compose(JvmPreset.G1, 4096, List.of("-XX:MinHeapFreeRatio=5")));
    }

    /// 合并：先移除旧片段，再追加新片段，保留用户参数。
    @Test
    public void mergeKeepsUserArgs() {
        String merged = JvmArgWriter.merge(
                "-Xms4096m -Xmx4096m -Dfoo=bar",
                "-Xms4096m -Xmx4096m",
                "-Xms8192m -Xmx8192m");
        assertEquals("-Dfoo=bar -Xms8192m -Xmx8192m", merged);
    }

    /// 合并：旧片段不在当前串里时直接追加。
    @Test
    public void mergeWithoutPrevious() {
        assertEquals("-Dfoo=bar -Xms2048m -Xmx2048m",
                JvmArgWriter.merge("-Dfoo=bar", null, "-Xms2048m -Xmx2048m"));
        assertEquals("-Dfoo=bar", JvmArgWriter.merge("-Dfoo=bar", null, ""));
    }

    /// strip 只移除生成片段，不碰用户参数。
    @Test
    public void stripGeneratedFragment() {
        assertEquals("-Dfoo=bar",
                JvmArgWriter.strip("-Dfoo=bar -Xms4096m -Xmx4096m", "-Xms4096m -Xmx4096m"));
        assertEquals("", JvmArgWriter.strip("-Xms4096m -Xmx4096m", "-Xms4096m -Xmx4096m"));
        assertEquals("-Dfoo=bar", JvmArgWriter.strip("-Dfoo=bar", ""));
    }

    /// 重复 token 只移除一次（按生成片段出现次数）。
    @Test
    public void stripRemovesOneOccurrencePerToken() {
        assertEquals("-Xmx2048m", JvmArgWriter.strip("-Xmx4096m -Xmx2048m", "-Xmx4096m"));
    }

    /// 一键移除单个参数。
    @Test
    public void removeToken() {
        assertEquals("-Xmx4096m", JvmArgWriter.removeToken("-Xms4096m -Xmx4096m", "-Xms4096m"));
        assertEquals("", JvmArgWriter.removeToken("-XX:+UseG1GC", "-XX:+UseG1GC"));
        assertEquals("-Xmx4096m", JvmArgWriter.removeToken("-Xmx4096m", "-XX:+UseG1GC"));
    }
}
