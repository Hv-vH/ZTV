# 正式版构建与升级

当前正式版为 0.4.1，`versionCode = 8`，应用 ID 为 `com.example.cztvtv`。Release 禁用调试能力，使用独立的固定发布签名；本版本仍保持已验证的非混淆构建。

## 本机构建

```bash
./build-local.sh --offline :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
node --test tools/player-script.test.mjs
```

已配置签名时，APK 为 `app/build/outputs/apk/release/app-release.apk`。交付副本为同目录的 `ZTV-0.4.1-release.apk`。签名校验使用 Build Tools 的 `apksigner verify --verbose --print-certs`。

发布证书 SHA-256 指纹：

```text
F4:EE:15:AF:05:36:25:64:26:5C:6A:1A:A0:F7:4B:9F:17:ED:F1:E9:6F:3D:42:8F:82:B8:35:00:12:E7:4A:16
```

## 固定签名与备份

本机发布密钥为 `.toolchain/release-signing/ztv-release.jks`，签名配置为项目根目录的 `keystore.properties`；两者均限制为当前用户可读写，并被 Git 忽略。配置通过 Gradle 的 `localRelease` signingConfig 读取，不在源码中保存密码。

请将这两个文件一起保存到用户自己的安全备份中。后续版本继续使用同一密钥、相同应用 ID，并递增 `versionCode`，才能覆盖升级。不要重新生成密钥来编译更新包，也不要把密钥、密码或含密钥的备份上传到 GitHub。

迁移到另一台开发机时，把密钥恢复到配置中的路径，并恢复 `keystore.properties`。文件格式如下，尖括号内容需填写真实值：

```properties
storeFile=.toolchain/release-signing/ztv-release.jks
keyAlias=ztv-release
storePassword=<本机保存的密码>
keyPassword=<本机保存的密码>
```

新克隆只有源码，未恢复签名配置时，Gradle 只能生成 `app-release-unsigned.apk`，不能作为可安装正式版交付。

## 首次从调试版迁移

0.4.0 及之前的交付包为 Debug 签名，与本次 Release 证书不同。Android 不允许不同证书直接覆盖；首次换成正式版，需要先卸载旧测试版，再安装正式版。卸载会清除应用数据，电视桌面的收藏入口也可能需要重新添加；当前应用没有持久化的用户频道配置。

```bash
adb uninstall com.example.cztvtv
adb install app/build/outputs/apk/release/ZTV-0.4.1-release.apk
```

以后正式版可直接覆盖安装：

```bash
adb install -r app/build/outputs/apk/release/ZTV-0.4.1-release.apk
```

源码保存在用户自己的 `Hv-vH/ZTV` 仓库，正式版本用同名 Git 标签定位；APK 与签名材料仅保存在本机。
