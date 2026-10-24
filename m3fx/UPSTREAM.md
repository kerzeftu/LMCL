# m3fx vendored source

This directory is an unmodified copy of the m3fx `src/main/java` and
`src/main/resources` trees from:

- Repository: <https://github.com/Glavo/m3fx>
- Commit: `96ccd6d26fa8b38b7621c47332cfcc0caff034bf`
- Date: 2026-09-11 18:00:02 +0800
- Upstream build: Gradle 9.6.1, Java 17, JavaFX 21

LMCL additions are limited to this `build.gradle.kts`, `LICENSE`, `NOTICE`
and `UPSTREAM.md`. The Java and resource trees are unchanged, including the
upstream `module-info.java`; no exported API or implementation class was
changed. The upstream build, test fixtures, demos and documentation are not
vendored.

The upstream repository did not contain a `LICENSE` file at this commit, so
the Apache License 2.0 text is included here and in the application jar at
`META-INF/LICENSE-m3fx.txt`.
