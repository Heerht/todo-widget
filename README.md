# 今日待办 · 原生 AppWidget 小部件

一个完整可构建的 Android 工程：App 自带一个「待办清单」桌面小部件。
小部件依附于 App —— 只有安装了本 APK，桌面长按 →「小部件」里才会出现它；卸载 App，小部件随之消失。

- 语言：Kotlin
- 小部件方案：经典 `AppWidgetProvider` + `RemoteViews` + `RemoteViewsService`（集合型，可滚动）
- 最低支持：Android 7.0（API 24），targetSdk 34
- 依赖：仅 AndroidX Core / AppCompat / Material，无第三方库

---

## 一、它能做什么

| 场景 | 行为 |
| --- | --- |
| 桌面小部件 | 显示今日待办列表、完成进度条、`2/5` 计数 |
| 点条目 | 直接在桌面切换「已完成 / 未完成」（删除线 + 置灰），不用打开 App |
| 点 `+` | 弹出轻量输入框（透明 Dialog Activity），输完即回到桌面 |
| 点刷新 | 手动重新读取数据 |
| 点标题区 | 打开 App 主界面 |
| 拖动边缘 | 变小自动收起进度条/刷新按钮（≥ 2×2 到 4×4 都能用） |
| 深色模式 | 跟随系统自动切换配色 |
| App 主界面 | 列表管理、勾选、删除、清除已完成、右上角一键「添加到桌面」 |

首次安装会写入 3 条示例待办，方便拖到桌面立刻看到效果。不想要就删掉 `TodoRepository.seed()` 里的那段调用。

---

## 二、为什么它「依附于 App」

这一点由三处绑定决定，缺一不可：

```
AndroidManifest.xml
├── <receiver android:name=".widget.TodoWidgetProvider" android:exported="true">
│     ├── <intent-filter> android.appwidget.action.APPWIDGET_UPDATE   ← 系统靠这个发现部件
│     └── <meta-data> android.appwidget.provider = @xml/todo_widget_info
└── <service android:name=".widget.TodoWidgetService"
             android:permission="android.permission.BIND_REMOTEVIEWS">  ← 列表数据源
```

`res/xml/todo_widget_info.xml`（`AppWidgetProviderInfo`）是小部件的「身份证」：默认 4×2 格、可缩放范围、30 分钟系统刷新周期、部件选择器里的预览图 / `previewLayout`。系统桌面读它才知道这个部件叫什么、开多大。

卸载 APK → 该 receiver 不存在 → 桌面上所有实例被系统一并移除。这就是「小部件依附于 App」的实际含义：**部件没有独立进程，也没有独立数据，它只是 App 数据在桌面的一层投影。**

---

## 三、代码结构

```
app/src/main/java/com/example/todowidget/
├── MainActivity.kt                 App 主界面（列表 / 增删 / 清除 / 钉到桌面）
├── QuickAddActivity.kt             小部件「+」弹出的快速输入框（透明 Dialog）
├── data/
│   ├── Todo.kt                     不可变数据模型
│   └── TodoRepository.kt           单例仓库：SharedPreferences + JSON，写盘后自动通知小部件
├── ui/
│   └── TodoAdapter.kt              App 内列表适配器（BaseAdapter，零额外依赖）
└── widget/
    ├── TodoWidgetProvider.kt       接收系统回调与自定义广播（勾选 / 刷新）
    ├── WidgetRenderer.kt           把「数据 + 当前尺寸」拼成 RemoteViews
    └── TodoWidgetService.kt        集合型小部件的 RemoteViewsFactory

app/src/main/res/
├── layout/widget_todo.xml          小部件整体布局（只用 RemoteViews 支持的 View）
├── layout/widget_todo_item.xml     小部件单条待办
├── xml/todo_widget_info.xml        AppWidgetProviderInfo
├── drawable/widget_bg.xml          卡片背景（drawable-v31 里改用系统圆角）
└── values/, values-night/          浅色 / 深色配色

tools/
├── make_assets.py                  生成启动图标与小部件预览图
└── check_resources.py              无 SDK 环境下的资源引用自检
```

数据流是一条直线，没有双向绑定：

```
MainActivity / QuickAddActivity / 小部件点击
        ↓
TodoRepository（唯一数据源，写入 SharedPreferences）
        ↓  persistAndNotify()
TodoWidgetProvider.notifyChanged()
        ↓                                   ↓
updateAppWidget(RemoteViews)      notifyAppWidgetViewDataChanged(ListView)
  刷新标题/进度/按钮                  RemoteViewsFactory.onDataSetChanged()
                                         重新取数、逐条生成 RemoteViews
```

---

## 四、构建运行

> 本机没有 Android SDK / Gradle，只做了静态自检，未实际编译。
> 不想装 Android Studio 的话，走 **GitHub Actions 云构建**，详见 [`docs/安装到手机.md`](docs/安装到手机.md)。

**方式一（推荐）**：Android Studio → Open → 选择 `android-todo-widget` 目录 → 等待 Gradle Sync → Run。

**方式二（命令行）**：

```bash
cd android-todo-widget

# 工程只带了 gradle-wrapper.properties，wrapper jar 需要生成一次
gradle wrapper --gradle-version 8.7

# 指向本机 SDK（Android Studio 会自动生成，命令行需手写）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17、Android SDK Platform 34、Build-Tools 34.x。

**添加小部件**：

1. 装好 App 后，长按桌面空白处 →「小部件」→ 找到「今日待办」→ 拖到桌面；
2. 或打开 App，点右上角的网格图标直接请求添加（`requestPinAppWidget`，Android 8.0+，部分国产桌面不支持该 API，会提示走方式 1）。

---

## 五、几个必须知道的实现细节

**1. RemoteViews 是受限的「遥控器」**
桌面进程没法执行你的代码，只能用 `RemoteViews` 描述界面。支持的 View 只有 FrameLayout / LinearLayout / RelativeLayout / GridLayout / TextView / Button / ImageView / ImageButton / ProgressBar / ListView / GridView / StackView 等，**不能放自定义 View，不能用 `?attr/` 主题属性**（颜色必须写成资源），布局里也不能写 `android:onClick`。所以小部件里所有颜色都走 `@color/widget_*`，深色模式靠 `values-night` 自动替换。

**2. 集合型小部件：三个 API 缺一不可**
```kotlin
views.setRemoteAdapter(R.id.widget_list, adapterIntent)   // 挂数据源
views.setEmptyView(R.id.widget_list, R.id.widget_empty)   // 空态
views.setPendingIntentTemplate(R.id.widget_list, pending) // 条目点击模板
```
条目的 Intent 必须自带唯一 `data`，否则桌面上放两个同款小部件会共用同一个工厂、数据串台。

**3. PendingIntent 的可变性（Android 12 起强制）**
- 条目模板 → 必须 `FLAG_MUTABLE`，系统才能把 `fillInIntent` 的 extras 填进去；
- 其他点击 → `FLAG_IMMUTABLE`；
- 全部使用显式 Intent（直接指向类），避免被外部应用劫持。

**4. `updatePeriodMillis` 最小 30 分钟**
所以「实时同步」不能靠定时器，只能靠事件驱动：App 内任何一次数据变更都会 `notifyChanged()`，桌面立刻重绘。手动刷新按钮只是兜底。

**5. 只更新外壳不够**
`updateAppWidget()` 只重绘标题、进度条这些静态元素；ListView 的内容要额外调用 `notifyAppWidgetViewDataChanged()`，否则会出现「进度已变、列表还是旧的」。

**6. 尺寸自适应**
`onAppWidgetOptionsChanged` 里读 `OPTION_APPWIDGET_MIN_WIDTH/HEIGHT`（单位 dp）：宽度 < 220dp 隐藏刷新按钮，高度 < 130dp 隐藏进度条。

**7. Android 12+ 圆角**
`res/drawable-v31/widget_bg.xml` 直接引用 `@android:dimen/system_app_widget_background_radius`，和桌面其他部件、系统图标保持同一套圆角。同时用 `android:previewLayout` 让部件选择器显示实时排版，而不是一张静态图。

**8. 为什么「+」要开 Activity 而不是内嵌 EditText**
RemoteViews 从 API 28 起支持 EditText，但焦点、输入法、返回键的行为在桌面进程里各家启动器表现不一。用透明 Dialog Activity 体验稳定，还能顺便复用 App 的输入校验。

---

## 六、继续改造

| 需求 | 改哪里 |
| --- | --- |
| 换成 Room / 网络数据 | 只改 `TodoRepository` 内部；`all()` 之后照旧 `notifyChanged()` |
| 加截止时间 / 分组 | `Todo` 加字段，`WidgetRenderer` 里按日期过滤，`widget_todo_item` 加一行小字 |
| 加「仅显示未完成」开关 | 在 `AppWidgetProviderInfo` 加 `configure` Activity，或读 SharedPreferences 里的偏好 |
| 做多个尺寸版本 | 新建第二个 `<receiver>` + 独立的 `todo_widget_small_info.xml`；或用 API 31 的 `setViewLayout` 响应式布局 |
| 点条目打开详情 | 把 `setPendingIntentTemplate` 换成 `getActivity` 模板，`fillInIntent` 带 todoId |
| 想用 Jetpack Glance | Glance 是 Compose 风格的新写法，底层仍是 RemoteViews；结构可保持不变，只重写渲染层 |

---

## 七、自检

```bash
python tools/check_resources.py
```

检查所有 `@drawable/@color/@string/@layout/@style/@dimen` 引用是否存在、Kotlin 里的 `R.id` 与布局 id 是否对得上、`values-night` 是否漏了颜色。当前状态：通过。
