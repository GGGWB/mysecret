# MySecret - 加密密码管理器

一款 Android 本地密码管理应用，所有数据使用 **AES-256-GCM** 加密存储，无网络通信，完全离线。

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🔐 严格加密 | AES-256-GCM 加密 + PBKDF2-HMAC-SHA256 密钥派生（210,000 次迭代） |
| 📋 密码列表 | 卡片式列表，首字母头像，清晰美观 |
| 🔍 即时搜索 | 实时搜索应用名、用户名、网址、备注 |
| ↕️ 排序 | 按名称 A→Z / Z→A / 按修改时间 |
| ➕ 添加/编辑 | 表单录入，支持网址和备注 |
| 🎲 密码生成器 | 可选长度（8-32）、大小写、数字、符号 |
| 📋 安全剪贴板 | 复制后 30 秒自动清除剪贴板 |
| 👁️ 密码可见性 | 详情页一键显示/隐藏密码 |
| 🔒 手动锁定 | 一键锁定，返回需重新输入主密码 |
| 🌙 深色模式 | 自动跟随系统暗色模式 |
| 💾 本地备份 | 导出加密备份文件到应用私有目录 |
| 🌐 远程备份 | 支持 WebDAV 和 SMB/Samba 协议，一键上传到 NAS/网盘 |
| 🔄 备份恢复 | 从 .mysecret 备份文件恢复密码库 |

```
主密码 ──PBKDF2(SHA256, 210000次)──→ AES-256 密钥
                                        │
密码库 JSON ──AES-256-GCM加密──→ vault.enc 文件
```

**文件格式**：`salt(16字节) | iv(12字节) | iterations(4字节) | 密文+GCM认证标签`

- 每次保存使用全新随机 salt 和 iv
- GCM 模式提供机密性 + 完整性认证，篡改即解密失败
- 主密码从不存储，仅存储加密验证令牌用于校验
- 密码库文件保存在应用私有目录，其他应用无法访问

## 📱 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 34)
- **UI**: Material Design 3 + ViewBinding
- **存储**: 本地加密文件 (Gson 序列化)
- **加密**: Javax.Crypto (AES-256-GCM + PBKDF2)
- **异步**: Kotlin Coroutines
- **构建**: Gradle 8.7 + AGP 8.5 + Kotlin 1.9.24
- **网络**: OkHttp 4.12（WebDAV 远程备份）
- **SMB**: jcifs-ng 2.1.39（SMB 远程备份）

## 📂 项目结构

```
my_secret/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mysecret/
│       │   ├── data/
│       │   │   ├── model/          # 数据模型 (Credential, Vault)
│       │   │   ├── PrefsManager.kt # SharedPreferences 管理
│       │   │   ├── SessionManager.kt# 会话状态管理
│       │   │   └── VaultRepository.kt# 加密文件读写
│       │   ├── security/
│       │   │   ├── CryptoVault.kt       # 加密核心
│       │   │   ├── PasswordGenerator.kt # 密码生成器
│       │   │   └── SecureClipboard.kt   # 安全剪贴板
│       │   ├── backup/
│       │   │   ├── WebDavClient.kt      # WebDAV 客户端
│       │   │   ├── SmbClient.kt         # SMB 客户端
│       │   │   └── BackupManager.kt     # 备份管理器
│       │   └── ui/
│       │       ├── unlock/   # 解锁/设置主密码界面
│       │       ├── main/     # 密码列表主界面
│       │       ├── editor/   # 添加/编辑密码界面
│       │       ├── detail/   # 密码详情界面
│       │       └── backup/   # 备份与远程同步界面
│       └── res/
│           ├── layout/    # 所有布局文件
│           ├── drawable/  # 图标和矢量图
│           ├── values/    # 颜色、字符串、主题
│           └── menu/      # 菜单资源
├── gradle/
│   ├── libs.versions.toml  # 依赖版本目录
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 🚀 构建与运行

### 方法一：Android Studio（推荐）

1. 打开 Android Studio
2. 选择 `File → Open`，选择 `my_secret` 文件夹
3. 等待 Gradle 同步完成
4. 连接 Android 手机或启动模拟器
5. 点击 ▶️ Run 运行

### 方法二：命令行

```bash
# 需要 ANDROID_HOME 环境变量指向 Android SDK
# 首次使用会自动下载 Gradle 8.7
./gradlew assembleDebug

# 生成的 APK 位于：
# app/build/outputs/apk/debug/app-debug.apk
```

## 📖 使用说明

### 首次使用

1. 打开应用，设置一个**强主密码**（至少 8 位）
2. ⚠️ **主密码无法找回**，遗忘后所有数据不可恢复
3. 创建密码库后即可开始添加密码

### 日常使用

1. **解锁**：输入主密码进入应用
2. **添加**：点击右下角 ➕，填写应用名、用户名、密码
3. **生成密码**：在添加界面点击"生成密码"按钮
4. **搜索**：在顶部搜索框输入关键词
5. **排序**：点击工具栏排序图标选择排序方式
6. **查看**：点击列表项查看详情
7. **复制**：详情页点击复制按钮（30 秒后自动清除剪贴板）
8. **锁定**：点击工具栏锁图标立即锁定

### 备份与恢复

1. **进入备份界面**：主界面点击工具栏 💾 备份图标
2. **本地备份**：点击"导出加密备份到本地"，生成 `.mysecret` 加密文件
3. **配置远程备份**：
   - 选择远程类型：WebDAV 或 SMB
   - 填写服务器地址（如 `https://nas.example.com:5005/dav/` 或 `smb://192.168.1.100/share/`）
   - 填写用户名、密码
   - 填写远程备份路径/文件名（如 `backup/mysecret.mysecret`）
   - 点击"保存配置"，再点击"测试连接"验证
4. **远程备份**：配置完成后，点击"立即备份"将加密文件上传到远程服务器
5. **恢复**：点击"从备份恢复"，选择 `.mysecret` 文件，输入备份时的主密码即可恢复

> 💡 远程备份支持群晖/威联通 NAS 的 WebDAV 服务、Nextcloud、以及任何支持 WebDAV 或 SMB 协议的设备。

## ⚠️ 安全须知

- **请牢记主密码**，它无法被重置或恢复
- 主密码不存储在设备中，也无法通过网络找回
- 建议主密码长度 ≥ 12 位，混合大小写字母、数字和符号
- 定期备份 `vault.enc` 文件（在应用私有目录中），但备份文件同样需要主密码才能解密
- 不要将主密码告知他人

## 📝 开发说明

- 所有密码操作在 IO 协程中执行，不阻塞 UI
- 密码使用 `CharArray` 持有，使用后立即清零
- 备份已禁用（`allowBackup=false`），防止数据通过 ADB 备份泄露
- 无 INTERNET 权限，确保零网络通信
