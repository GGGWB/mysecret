# 贡献指南 / Contributing

感谢你对 MySecret 的兴趣！🎉 欢迎各种形式的贡献。

Thank you for your interest in MySecret! Contributions of all kinds are welcome.

## 🐛 报告 Bug / Reporting Bugs

发现 bug？请通过 [GitHub Issues](../../issues) 提交，并包含：
- 设备型号与 Android 版本
- MySecret 版本号
- 复现步骤
- 预期行为 vs 实际行为
- （可选）截图或录屏

**安全相关 bug**（如可能导致密码泄露）请不要公开提 issue，请私发邮件或加密通信联系。

## 💡 功能建议 / Feature Suggestions

欢迎在 [Issues](../../issues) 提想法。请说明：
- 要解决什么问题
- 你期望的交互方式
- 是否愿意自己实现

## 🔧 提交代码 / Submitting Code

1. Fork 本仓库
2. 创建分支：`git checkout -b feat/your-feature`（功能）或 `fix/your-bugfix`（修复）
3. 提交更改，commit message 用中英文均可，建议遵循：
   - `feat: 新增 XX 功能`
   - `fix: 修复 XX 问题`
   - `docs: 更新文档`
   - `refactor: 重构 XX`
4. 提交 Pull Request，描述改动内容与动机

### 代码风格 / Code Style

- Kotlin 代码遵循 [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- XML 资源命名用小写下划线（snake_case）
- 新增字符串资源请同时提供 `values/`（中文）和 `values-en/`（英文）

### 构建 / Build

```bash
./gradlew assembleDebug    # 调试包
./gradlew assembleRelease  # 发布包（需配置签名）
```

## 📝 行为准则 / Code of Conduct

请保持友善、尊重。任何形式的骚扰或人身攻击都不被接受。

## 📄 许可证 / License

提交的代码将在 [MIT License](./LICENSE) 下发布。

---

再次感谢你的贡献！即使是修正一个 typo、翻译一段文字，都非常有价值。
