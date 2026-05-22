# 🚀 快速获取APK - 2分钟搞定！

## 方法：直接在GitHub网页上操作（最简单！）

### 步骤1：打开仓库
访问：https://github.com/huang2025511/processviewer2026

### 步骤2：编辑现有文件
1. 点击仓库中的 `.github/workflows/build.yml` 文件
2. 点击右上角的编辑按钮（铅笔图标 ✏️）

### 步骤3：替换文件内容
把下面的完整代码复制进去，替换原有的所有内容：

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

      - name: Generate Gradle Wrapper
        run: |
          cat > build.gradle << 'EOF'
          task wrapper(type: Wrapper) {
              gradleVersion = '8.2'
          }
          EOF
          gradle wrapper --gradle-version 8.2 --no-daemon

      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: process-manager-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

### 步骤4：提交文件
1. 填写提交信息：`Fix Gradle wrapper issue`
2. 点击绿色按钮 **"Commit changes"**

### 步骤5：获取APK！
1. 点击仓库页面顶部的 **"Actions"** 标签
2. 你会看到构建正在进行（黄色圆点）
3. 等待3-5分钟，直到变成绿色对勾 ✓
4. 点击那个构建记录
5. 滚动到页面底部，找到 **"Artifacts"** 区域
6. 点击 **"process-manager-debug"** 下载APK！

---

## 📱 安装到手机
1. 把下载的APK文件传到手机
2. 在手机文件管理器中打开
3. 点击安装，允许未知来源
4. 完成！

---

## 🎉 就是这么简单！
