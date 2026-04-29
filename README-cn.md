# DocScan - Android 文档扫描应用

一款开源的 Android 文档扫描应用，支持自动检测文档边界、透视校正、OCR文字识别和 PDF 导出。

## 功能特性

- **相机扫描** — CameraX 相机预览与拍摄，支持闪光灯和多种对焦模式
- **A4引导框** — 竖向A4比例虚线引导框，覆盖预览区域90%，精确引导文档对齐
- **自动文档检测** — 三级检测策略（GrabCut → Otsu阈值 → 亮度回退），自动识别白色/浅色文档边界
- **透视校正** — 基于四角点的透视变换，将倾斜文档校正为正面视角
- **手动裁剪** — 可拖拽四角调整裁剪区域，带半透明遮罩和网格辅助线
- **图像增强** — 灰度、对比度增强（去噪+锐化+CLAHE）、黑白二值化等滤镜
- **OCR文字识别** — Google ML Kit 离线识别，支持中文/英文/日文/韩文
- **文字叠加预览** — 在图片上叠加显示识别到的文字块边界框
- **多页扫描** — 连续扫描多页文档
- **PDF导出** — A4尺寸PDF，支持页码、元数据、OCR文字嵌入（可搜索）
- **PDF页面排序** — 拖拽排序、上移/下移/置顶/置底/删除页面
- **图片保存** — 保存扫描结果为高质量JPEG图片到相册
- **多选合并** — 主页多选已扫描文件，合并导出为PDF
- **设置管理** — PDF/图片保存目录、OCR语言、PDF质量等配置
- **多语言支持** — 中文、英文、西班牙文，可在设置中切换，支持跟随系统语言
- **关于页面** — 作者信息、捐赠地址（XMR/USDT TRC20/USDT ERC20）及二维码
- **完全离线** — 所有处理在本地完成，无需网络连接

## 技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| 相机 | CameraX | 预览、拍摄、FIT_CENTER模式精确坐标映射 |
| 文档检测 | OpenCV 4.9.0 | GrabCut分割、Otsu阈值、Canny边缘检测、轮廓提取 |
| 白色页面检测 | OpenCV + 亮度分析 | 三级策略：GrabCut → Otsu → 亮度回退 |
| 透视校正 | OpenCV | 4点透视变换，自相交检测 |
| 图像增强 | OpenCV + Android Canvas | 去噪+锐化+CLAHE直方图均衡、自适应二值化 |
| OCR | Google ML Kit | 离线文字识别，支持中/英/日/韩 |
| PDF生成 | PDFBox-Android | A4尺寸PDF，页码、元数据、OCR文字层 |
| UI框架 | Material Design | Material 3 组件 |
| 国际化 | Android Locale | LocaleHelper动态切换，支持中/英/西 |
| 开发语言 | Kotlin | 100% Kotlin，协程异步处理 |

## 项目结构

```
app/src/main/java/com/docscan/
├── DocScanApp.kt               # Application类，OpenCV初始化
├── scanner/                    # 核心扫描引擎
│   ├── DocumentDetector.kt     # 通用文档边缘检测（Canny+轮廓）
│   ├── WhitePageDetector.kt    # 白色页面检测（GrabCut/Otsu/亮度回退）
│   ├── PerspectiveCorrection.kt # 4点透视校正
│   └── ImageEnhancer.kt        # 图像增强滤镜
├── ocr/                        # OCR模块
│   └── OcrEngine.kt            # ML Kit文字识别引擎
├── export/                     # 导出模块
│   ├── PdfExporter.kt          # PDF导出（PDFBox）
│   └── FileHelper.kt           # 文件存储工具
├── model/                      # 数据模型
│   ├── ScanPage.kt             # 扫描页数据（Parcelable）
│   └── ScanDocument.kt         # 文档数据
├── viewmodel/                  # ViewModel
│   └── ScanViewModel.kt        # 扫描状态管理
├── util/                       # 工具类
│   ├── BitmapHelper.kt         # Bitmap解码/旋转/回收
│   ├── AppSettings.kt          # SharedPreferences设置管理
│   └── LocaleHelper.kt         # 多语言切换工具
└── ui/                         # 界面层
    ├── MainActivity.kt         # 主页（浏览/多选/合并PDF）
    ├── ScanActivity.kt         # 扫描页（相机+A4引导框裁剪）
    ├── ResultActivity.kt       # 结果页（预览/滤镜/OCR/裁剪/PDF导出）
    ├── CropActivity.kt         # 独立裁剪页
    ├── PdfOrderActivity.kt     # PDF页面排序（拖拽排序）
    ├── AboutActivity.kt        # 关于页（作者/捐赠信息）
    ├── SettingsActivity.kt     # 设置页
    ├── DocumentOverlayView.kt  # A4引导框叠加层（竖向A4比例）
    ├── CornerSelectionView.kt  # 四角点选择视图（拖拽+遮罩+网格）
    ├── CropImageView.kt        # 裁剪交互视图
    ├── TextOverlayView.kt      # OCR文字块叠加显示
    └── TextRegionView.kt       # 文字区域选择视图
```

## 构建与运行

### 环境要求

- JDK 17+
- Android SDK (compileSdk 34)
- Android Studio (推荐) 或 Gradle 8.5+

### 构建步骤

1. 克隆项目
2. 在 `local.properties` 中配置 SDK 路径：
   ```properties
   sdk.dir=/path/to/android-sdk
   ```
3. 构建Debug APK：
   ```bash
   ./gradlew assembleDebug
   ```
4. 构建Release APK：
   ```bash
   ./gradlew assembleRelease
   ```

### 输出文件

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## 使用方式

### 基本扫描流程

1. 打开应用 → 点击右下角扫描按钮
2. 对准文档 → 将文档放入A4虚线引导框内
3. 点击拍摄按钮拍照
4. 系统自动按引导框区域裁剪并校正
5. 查看扫描结果 → 可添加更多页面
6. 导出为PDF或保存图片到相册

### 手动裁剪

1. 在结果页切换到"裁剪"模式
2. 自动检测文档边界或手动拖拽四角
3. 确认裁剪 → 透视校正输出

### 多页合并PDF

1. 在主页点击"选择"进入多选模式
2. 选择要合并的页面
3. 点击"合并PDF" → 跳转排序页
4. 拖拽调整页面顺序
5. 确认导出PDF

### OCR文字识别

1. 在结果页切换到"文字"模式查看识别结果
2. 切换到"叠加"模式查看文字块位置
3. 可复制识别文字到剪贴板

### 语言切换

1. 在设置页点击"语言"选项
2. 选择系统默认/中文/English/Español
3. 语言将在重启应用后生效

## 核心算法

### 多语言国际化

```
res/
├── values/strings.xml          # 默认资源（中文）
├── values-zh/strings.xml       # 中文
├── values-en/strings.xml       # 英文
└── values-es/strings.xml       # 西班牙文
```

切换流程：
1. 用户在设置页选择语言 → 保存到 SharedPreferences
2. `DocScanApp.attachBaseContext()` 调用 `LocaleHelper.onAttach()` 应用语言
3. `LocaleHelper.applyLanguage()` 设置 Locale 并创建新的 ConfigurationContext
4. 切换后提示重启应用生效

### 裁剪坐标映射

```
A4引导框角点(视图坐标)
    → 根据预览宽高比计算预览在视图中的实际区域
    → 减去预览区域偏移，除以预览区域尺寸 → 归一化坐标(0~1)
    → 根据预览/拍摄图片宽高比差异映射到拍摄图片坐标
    → PerspectiveCorrection.correct() → 透视校正裁剪
```

关键点：
- 预览分辨率根据传感器旋转角度（90°/270°）交换宽高，确保竖向宽高比正确
- PreviewView 使用 FIT_CENTER 模式，预览内容完整显示不裁剪
- 引导框基于预览实际区域绘制，与预览内容精确对齐

### 白色页面检测（三级策略）

```
1. GrabCut分割 → 前景掩码 → 形态学处理 → 轮廓查找 → 四边形逼近
2. Otsu阈值分割 → 二值掩码 → 形态学处理 → 轮廓查找 → 四边形逼近
3. 亮度分析回退 → Otsu阈值 → 形态学清理 → 连通域 → 边界提取 → 四边形逼近
```

### 透视校正

```
4角点输入 → 验证(4点/范围/非自相交)
    → 计算输出宽高(上下边/左右边最大值)
    → getPerspectiveTransform() → warpPerspective()
    → 校正后Bitmap
```

## 作者

**lorime**

邮箱：lorime@126.com

## 捐赠

如果这个项目对您有帮助，欢迎捐赠支持开发！

> XMR : 4DSQMNzzq46N1z2pZWAVdeA6JvUL9TCB2bnBiA3ZzoqEdYJnMydt5akCa3vtmapeDsbVKGPFdNkzzqTcJS8M8oyK7WGj5qMvNZRw61w6wMF
>
> USDT (TRC20) : TG6DCBoQszDxc64owRZKkSHqZfcAQrqR8uM
>
> USDT (ERC20) : 0x4323d39BA9b6Bd0570920e63a8D3a192b4459330

二维码见 `erweima/` 目录。

## 许可证

MIT License
