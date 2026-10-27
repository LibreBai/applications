# 💰 牛马草料计算器

把月薪换算成"每秒都在到账"的实时数字，让打工人随时看到今天、这个月已经挣了多少钱——顺便算清今天带薪摸鱼白赚了多少。

两个形态，同一个计算模型，全部离线、零依赖：

| 形态 | 交付物（dist/） | 用法 |
|---|---|---|
| Windows 桌面版 | `dist/牛马草料计算器.exe` | 双击即用（Win10/11 免安装，64 KB 绿色单文件） |
| 网页版 | `dist/牛马草料计算器网页版.html` | 浏览器打开即用（Edge/Chrome，手机亦可） |

## 目录结构
```
牛马草料计算器/
├── README.md                       ← 本文件
├── 编译.bat                        ← 双击一键编译（内部调 release.ps1，产物进 dist/）
├── release.ps1                     ← 一键发布：编译 + 改名 + 同步 → dist/
├── .gitignore                      ← 忽略 build/ 编译中间产物（exe 由仓库根 .gitignore 统一忽略）
├── dist/                           ← 交付用户的文件（只放这个，直接发出去）
│   ├── 牛马草料计算器.exe
│   ├── 牛马草料计算器网页版.html
│   └── 使用说明.txt                ← 用户说明，直接编辑维护
├── build/                          ← 编译中间产物（可随时删，脚本自动重建）
│   └── NiuMaIncome.exe
├── docs/                           ← 文档
│   ├── 需求文档.md                 定位、功能/非功能需求、里程碑、迭代路线
│   ├── 概要设计.md                 核心计算模型（两版共用）、桌面版技术选型
│   ├── 详细设计.md                 网页/桌面实现细节、自测记录、文件清单
│   ├── 原型/
│   │   └── web原型_v0.7.1.html     网页原型存档（冻结快照，不随版本维护）
│   └── images/                     文档配图（界面自检历史截图等）
└── src/
    ├── desktop/                    ← 桌面版源码（C# WinForms）
    │   ├── Program.cs              全部源码（单文件）
    │   ├── build.bat               编译 → build\NiuMaIncome.exe
    │   ├── make_icon.ps1           图标生成（build.bat 缺图标时自动调用）
    │   └── icon.ico
    └── web/
        └── index.html              ← 网页版唯一源码（源码即交付物）
```

## 交付用户
把 `dist/` 里的文件发出去即可：exe 给 Windows 用户双击，html 任何设备浏览器可用，使用说明随附。

## 从源码自己编译（git 拿到代码的人看这里）

条件：Windows 10/11，**什么都不用装**（编译器是系统自带的 .NET Framework `csc.exe`）。

双击根目录的 **`编译.bat`**，等它显示 `RELEASE OK`，产物就在 `dist\`：`牛马草料计算器.exe` 和 `牛马草料计算器网页版.html`。

命令行等价写法：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File release.ps1
```

> 本项目在 applications 仓库中被根 `.gitignore` 的 `*.exe` 规则忽略——git 里只有源码，exe 一律由使用者按本节自行编译。

## 开发与发布

### 日常改动（推荐）
改完源码后双击 `编译.bat`（或跑上面的 powershell 命令），两条产物自动更新到 `dist/`。

### 手动分步
- 桌面版：编辑 `src/desktop/Program.cs` → 运行 `src/desktop/build.bat`（产物 `build\NiuMaIncome.exe`）→ 拷贝改名 `dist\牛马草料计算器.exe`。依赖仅 Windows 自带 .NET Framework 4.8 + csc.exe，无需任何 SDK。
- 网页版：编辑 `src/web/index.html` → 拷贝覆盖 `dist\牛马草料计算器网页版.html`。

> build.bat 保持纯 ASCII（cmd 按 ANSI 代码页解析 .bat），所以编译产物用英文名，中文改名由 release.ps1 完成。

## 文档索引
- 要看"做什么"：[docs/需求文档.md](docs/需求文档.md)
- 要看"怎么算"（21.75 口径、时间线、摸鱼额度公式）：[docs/概要设计.md](docs/概要设计.md)
- 要看"怎么实现、怎么构建"：[docs/详细设计.md](docs/详细设计.md)
