# patches/

本目录存放"我们相对上游的全部改动"，供 patch 策略重建与上游同步使用。

- `BASELINE.txt`：上次实际验证过的上游提交（可复现基线），由 `scripts/sync-upstream.sh` 更新。
- `community.patch`：`git diff upstream/release/3.16..main` 的导出（首次发布前生成，不要手改）。

重建流程（详见 `docs/steps/01-仓库建立与上游同步机制.md` §3.4）：

```bash
git fetch upstream --prune --tags --force
git switch -c build/<version> upstream/release/3.16
git apply --3way patches/community.patch
./gradlew build
```
