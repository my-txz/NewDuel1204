# v1.zip 下载说明

`v1.zip` 包含本项目的**全部源代码、配置、文档与构建产物**：

- 45 个 Java 源文件（含机器人对决、23 种玩法、UI、观战、排位等全部功能）
- `src/main/resources/` 下的 plugin.yml / config.yml / messages.yml
- `pom.xml`、`README.md`、`LICENSE`、`.github/workflows/build.yml`
- `dist/NewDuel1204-1.0.0.jar`（已编译的可直接部署产物）

## 还原

由于 GitHub 连接器仅支持写入纯文本，仓库中的 `v1.zip` 以 **base64 文本** 形式存储。
下载后运行随附脚本即可还原为真正的 ZIP：

```bash
python3 decode_v1.py -o v1_real.zip
unzip -l v1_real.zip   # 校验：应显示 74 个文件
```

> 提示：本仓库 Releases / 本地交付目录同时提供可直接下载的真 ZIP 二进制。
