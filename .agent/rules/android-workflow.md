# Android 开发与验证规则

1. **构建验证闭环**：每次修改代码后，必须执行 `./gradlew assembleDebug` 确认编译通过再向我汇报；若报错请直接根据日志自行修复。
2. **禁止使用内置浏览器预览**：本项目是原生 Android App，不要调用浏览器工具去打开或验证界面。
3. **依赖管理规范**：添加第三方库时优先使用项目现有依赖配置，禁止凭空编造未验证的远程依赖；Spotify App Remote 必须走本地 `spotify-app-remote/` 模块的 `.aar`。
4. **真机部署与查错**：当需要安装测试时，执行 `./gradlew installDebug`；如果我反馈手机上闪退，主动执行 `adb logcat -d -s AndroidRuntime:E` 获取崩溃堆栈并定位修复。
5. **接口防过时**：涉及 Spotify Web API 时，严格遵守 2026 `/playlists/{playlist_id}/items` 规范（解析 `items[i].item`），严禁使用旧版 `/tracks` 接口。
