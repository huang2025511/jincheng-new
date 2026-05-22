# 构建APK指南

## 快速开始（使用GitHub Actions）

### 方法1：使用GitHub Actions自动构建（最简单）

1. 访问你的仓库：https://github.com/huang2025511/processviewer2026
2. 点击 **Actions** 标签
3. 点击 **"New workflow"**
4. 搜索 "Android" 或直接复制下方的工作流
5. 创建文件 `.github/workflows/build.yml`，内容如下：

```yaml
name: Build Android APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: process-manager-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

6. 提交后，GitHub Actions会自动开始构建
7. 构建完成后，在 Actions 页面下载APK文件

---

### 方法2：本地使用Android Studio构建

#### 前置要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK API 34
- JDK 17 或更高版本

#### 步骤

1. **克隆仓库**
```bash
git clone https://github.com/huang2025511/processviewer2026.git
cd processviewer2026
```

2. **用Android Studio打开项目**
   - 打开 Android Studio
   - File → Open
   - 选择项目文件夹

3. **等待Gradle同步**
   - 首次打开会自动下载依赖
   - 等待右下角进度条完成

4. **构建APK**
   - 菜单：Build → Build Bundle(s) / APK(s) → Build APK(s)
   - 或者：点击顶部工具栏的绿色运行按钮 ▶️

5. **获取APK文件**
   - 构建成功后会弹出通知
   - 点击通知中的 "locate"
   - 或在文件夹中找到：`app/build/outputs/apk/debug/app-debug.apk`

---

## 常见问题

### Q: 构建失败怎么办？
A: 检查以下几点：
- Android SDK是否安装完整
- JDK版本是否为17或以上
- 网络连接是否正常（需要下载依赖）

### Q: 安装APK时提示"未知来源"
A: 在手机设置中允许安装未知来源应用

### Q: 如何构建Release版本？
A: 需要配置签名证书，然后运行：
```bash
./gradlew assembleRelease
```

---

## 项目信息

- 应用名称：进程管理器
- 包名：com.processmanager.app
- 最低SDK：API 26 (Android 8.0)
- 目标SDK：API 34 (Android 14)
