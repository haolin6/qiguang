# 栖光开发规范

本项目采用 `main`、短期任务分支和 Pull Request 的协作流程。个人开发也按此流程练习；维护者明确确认后才合并。

## 开始一项改动

1. 确认需求范围与验收结果；不清楚时先补齐必要信息，不把额外功能混进当前任务。
2. 检查 `git status`，保留已有改动，不通过重置或清理来丢弃未提交文件。
3. 切到 `main` 并以 `git pull --ff-only` 同步。出现分歧时先核对原因。
4. **修改功能前先创建任务分支**。不同任务使用不同分支，不直接在 `main` 开发。

```sh
git status
git switch main
git pull --ff-only
git switch -c feat/short-description
```

分支示例：`feat/focus-reminder-flash`、`fix/widget-alignment`、`docs/development-workflow`。一项需求可以有多次完整、可说明的提交，不必只提交一次。

## 实现与检查

- 只修改当前任务相关文件；保持已有功能、用户配置和设备绑定兼容。
- 根据改动选择验证，不用无关的重复测试代替实际验收。
- 检查 `git diff` 和暂存区差异，只暂存本次任务文件。
- 运行隐私扫描。密钥、真实设备地址、Token、私人邮箱、电脑绝对路径和导出数据不得进入提交。

| 改动 | 提 PR 前的最低验证 |
| --- | --- |
| 文档、代理指引、PR 模板 | 相对链接与格式检查、隐私扫描；无需为纯文档改动编译 App |
| 灯光协议、计时、取消或状态逻辑 | 相关策略／边界测试、原生编译；区分模拟器和真实设备验证 |
| 界面或桌面组件 | 原生编译、相关交互与布局检查，查看浅深色和放大字体效果 |
| 连接、配置或升级 | 配置保留／迁移检查，使用合成数据保护真实凭据 |

常用命令：

```sh
python tools/build.py --unit
python tools/build.py
python tools/privacy_scan.py
git diff --check
git diff --cached
```

模拟器需要时单独运行 `python tools/build.py --acceptance` 和 `python tools/verify_emulator.py`。软件验证不等于实灯验收。真实手机安装、灯光操作或恢复测试按用户本次授权执行；延期的项目在 PR 中明确保留。

## 提交与 Pull Request

1. 检查通过后，在任务分支提交并推送；不推送到 `main`。
2. 创建目标为 `main` 的 PR。若有必要的检查尚未完成，只开 Draft，并标明未完成内容；检查通过后再转为待审查。
3. PR 描述说明：问题、最终行为、验证结果、未验收项和需要关注的兼容性。
4. 提供 PR 链接和当前提交，向维护者说明已经走到哪一步。

```sh
git add <本次改动的文件>
git commit -m "feat: describe the change"
git push -u origin feat/short-description
```

**创建 PR、检查通过、收到工具执行权限或允许推送分支，都不代表获准合并。**

## 合并确认

- PR 创建后等待维护者对本次 PR 当前内容的明确确认。未收到确认，不合并、不启用自动合并、不直接推送 `main`。
- 确认后仍应核对 PR、分支、最新提交与检查结果。若又发生实质性修改，需要重新获得确认。
- 维护者可以自行审查并合并。**当前不设置“必须第二个人批准”**，也不为执行这套流程额外修改 GitHub 审批规则。
- 已确认内容可以在 GitHub 页面手动合并；若让代理代为合并，也需要先有同样明确的确认。

合并后切回 `main`，拉取合并结果，再结束已完成的分支。普通 Merge 后可用：

```sh
git switch main
git pull --ff-only
git branch -d feat/short-description
```

GitHub 上的远端任务分支也可在合并后删除。若使用 Squash 等其他合并方式，先确认改动已进入主线，再处理本地分支，不通过强制操作掩盖未合并内容。

## 发布与交付

合并和发布是两个步骤。不要因为测试通过或 PR 已创建，就打标签、发布 Release 或覆盖已有发布物。发布应在本次任务的明确授权范围内，从确认后的主线进行。

新版本 APK 和源码包还需检查签名、摘要及隐私信息。私人旧版本、原厂 APK、真实设备配置与验收基线仅留在本机受保护目录。

## 本机设置

JDK／SDK 路径和本机配置见 [README](README.md)，隐私边界见 [PRIVACY](docs/PRIVACY.md)。新克隆仓库后，可安装本地提交前扫描钩子：

```sh
python tools/install_privacy_hook.py
```

维护者的额外私人阻止列表保存在忽略目录，不进入仓库。代理协作还应遵守 [AGENTS.md](AGENTS.md)。
