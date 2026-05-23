# GitHub Actions 测试指南

## 如何在 GitHub 上测试这个项目

### 1. 自动触发测试

每次你推送代码到 `main` 分支时，GitHub Actions 会自动：
- 编译项目
- 运行单元测试
- 构建 APK
- 上传测试结果和 APK

### 2. 手动触发测试

你也可以手动触发构建和测试：

1. 打开仓库页面：https://github.com/huang2025511/jincheng-new
2. 点击顶部的 **Actions** 标签
3. 选择 **Build and Test** 工作流
4. 点击 **Run workflow** 按钮
5. 选择 **main** 分支
6. 点击绿色的 **Run workflow**

### 3. 查看构建和测试结果

1. 在 **Actions** 页面，你会看到所有的工作流运行记录
2. 点击任意一次运行，查看详情
3. 查看构建步骤是否成功（绿色勾号）
4. 查看测试结果
5. 在 **Artifacts** 区域下载 APK 和测试报告

### 4. Artifacts 说明

每次成功运行后，你可以下载：
- **app-debug** - 可安装的 APK 文件
- **test-results** - 单元测试结果报告

### 5. 测试类型

当前配置了：
- **单元测试** - 快速测试代码逻辑
- **构建测试** - 验证项目可以成功编译

### 6. 工作流程配置

工作流配置文件位于：`.github/workflows/build.yml`

主要步骤：
1. 检出代码
2. 设置 JDK 17
3. 设置 Android SDK
4. 编译 Debug APK
5. 运行单元测试
6. 上传 APK 和测试结果

### 7. Pull Requests

当你创建 Pull Request 时：
- GitHub Actions 会自动运行测试
- 你可以在 PR 页面看到测试结果
- 测试通过后才能合并代码

### 8. 本地测试

你也可以在本地运行测试：

```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 构建 APK
./gradlew assembleDebug
```
