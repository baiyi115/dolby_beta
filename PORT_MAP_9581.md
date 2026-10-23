# 网易云 9.5.81 坐标定位记录

APK: 网易云音乐 9.5.81 (versionCode 9005081)，21 dex，177,263 classes

## 结论速览

- `com.netease.cloudmusic.*` 具名包下的类跨版本稳定，可直接 `Class.forName`。
- 混淆短名每次构建都会变，必须靠 DexKit 锚点或结构匹配定位。
- `dr.h`（顶部频道适配器）是当前版本可直接硬编码的混淆类，但换版本必须重验。

## 已验证的坐标

### AD-01 开屏广告

| 目标 | 类名 | 方法签名 | 状态 |
|---|---|---|---|
| LoadingAdManager | `com.netease.cloudmusic.module.ad.LoadingAdManager` | `E(AdInfo)Z` public | ✅ |
| 同上 | 同上 | `G(Activity)V` public | ✅ |
| 同上 | 同上 | `o(Z)ArrayList` public | ✅ |
| LoadingAdActivity | `com.netease.cloudmusic.activity.LoadingAdActivity` | `onCreate(Bundle)V` | ✅ |
| LoadingAdFragment | `com.netease.cloudmusic.fragment.LoadingAdFragment` | `onViewCreated(View,Bundle)` | ✅ |
| LoadingAdShakeHelper | `com.netease.cloudmusic.module.ad.boot.LoadingAdShakeHelper` | `onSensorChanged(SensorEvent)` | ✅ |
| 免广 AB 开关 | `o60.w0` | `g()Z` public static final | ✅ |
| AdInfo | `com.netease.cloudmusic.module.ad.meta.AdInfo` | — | ✅ |

`o60.w0.g()` 字节码：`a()` 读 `qd-noAd-aos0513.` + lazy 值 → `d()` 取值 → 与 `"c"` 比较 → 返回相等。**返回 true = 在 c 组 = 看广告；返回 false = 免广**。

锚点：`loading_ad_judge`（唯一命中）

### AD-04 搜索广告

- `com.netease.cloudmusic.music.biz.search.start.rnview.RNSearchAdViewManger` ✅
- `com.netease.cloudmusic.music.biz.search.complex.viewholder.ad.SearchAdItemLargeView` ✅
- `com.netease.cloudmusic.music.biz.search.complex.viewholder.ad.SearchAdItemMediumView` ✅
- 额外发现：`SearchAdItemClickStayView` / `SearchAdItemClickStayMediumView` / `SearchAdItemLpRtViewV1`

`RNSearchAdViewManger` 没有返回 View 的无参方法，广告渲染入口是
`createViewInstance(ThemedReactContext)`，返回 `RNSearchAdView`。

### UI-03 首页模块净化

- `com.netease.cloudmusic.dsl.container.data.MixContainerData` ✅ 唯一命中
- `getBlockCodeOrderList()Ljava/lang/String;` ✅
- `getResources()Ljava/util/List;` ✅
- `com.netease.cloudmusic.discovery.data.meta.DiscoveryBlockData#getBlockList()` ✅

### UI-04 顶部频道

| 目标 | 类名 | 说明 |
|---|---|---|
| 适配器 | `dr.h` | ✅ 继承 `FragmentStateAdapter` |
| 数据源写入 | `dr.h.T0(List)V` | ✅ `b.clear()` → `b.addAll(p1)` → `notifyDataSetChanged()` |
| 条目类型 | `com.netease.cloudmusic.module.strategy.meta.DiscoveryBaseTabInfo` | 标题走 `getTitle()` |
| BaseArkV3Fragment | `com.netease.cloudmusic.discovery.view.newframework.BaseArkV3Fragment` | ✅ |
| MusicTabLayout | `com.netease.cloudmusic.theme.ui.tab.MusicTabLayout` | ✅ `$k` 内部类仍在 |
| HomeTopTabInfo | `com.netease.cloudmusic.discovery.view.module.hometoptab.model.HomeTopTabInfo` | ✅ |

锚点：`RecommendTwoFlowAdapter`（唯一命中 `dr.h`）

**注意**：`T0` 会把入参直接放进内部 `ArrayList b`，所以替换入参 `p1` 即可完成过滤，`getItemCount()` 返回 `b.size()` 自动跟随。

### UI-05 底部导航

| 类 | 说明 |
|---|---|
| `nh0.h` | 底栏数据仓库，锚点 `BottomNavigationRepository` |
| `lh0.o` | 底栏渲染层 |

`nh0.h#h()Ljava/util/List;` 是底栏数据构造（416 条指令，读 SharedPreferences + `key_bottom_tab_info`）。
`lh0.o` 是渲染层：`c(Z,List,MainTabContext)` / `b(Z,List)` 接收 `tabCodeList`，内部 `a(List)`/`d(List)` 做布局。

### UI-08 自动播放

| 逻辑名 | 类 | 锚点 |
|---|---|---|
| 蓝牙自动播放 | `mt0.d` | `bluetoothAutoPlay2History` |
| 云村自动播放 | `ot0.r` | `HOMEPAGE_BOTTOM_TAB_PUBLISH_GUIDE` |
| 启动自动播放总开关 | `ev0.j` | `key_start_music_after_launch_mode_enable_` |
| 用户设置聚合 | `com.netease.cloudmusic.utils.in` | `MESSAGE_CENTER_NEW_SONG_VERSION_CODE_TEMP` |

`mt0.d` 的 `a/b/c/d/e` 全部读 `setting#autoPlayConfig`，是蓝牙与车载的真实开关。
`ot0.r` 上只有 `j()`（`mlogPlayIn4G`）与自动播放相关，其余 `a/g/h/i/k/l` 是社区域偏好。
`ev0.j` 的 `g/h` 是启动总开关。

### UI-01 更新弹窗

| 类 | 说明 |
|---|---|
| `com.netease.cloudmusic.appupdate.CMUpdateConfig` | 锚点 `MarketUpdate [logMarketStage] fail` |
| `com.netease.cloudmusic.appupdate.d` | `apkRemindVersionCode` |

`CMUpdateConfig.hasNewVersion()Z` 是更新的总闸门（public static）。
`appupdate.d` 的 `D()`（forceUpdate）、`I()`（needPopUp）、`C()` 是具体判定。

### AD-10 激励广告与免费听

| 类 | 说明 |
|---|---|
| `com.netease.cloudmusic.ui.hint.PlayerAdMotivationView` | 播放页底部免听提示条 |
| `com.netease.cloudmusic.ui.ad.dslview.greeting.AdGreetingDSLView` | 首页问候语广告位 |
| `com.netease.cloudmusic.module.ad.motivation.commonui.*` | 四个激励弹窗 |

`PlayerAdMotivationView` 的入口是 `maybeDisplayHint(AbsHintContainer, a, r)`。
`AdGreetingDSLView` 的渲染入口是 `showGreetingResource(JSONObject)` 与
`showGreetingResourceList()`，不是 `bindData` / `setData` / `render`。

### 未能验证 / 需补定位

- `AdReportHooks` 中 `StartAppHelper$b` 的 `startApp` 为 4 参
- `ClipboardUtils` 无稳定坐标，改用系统 `ClipboardManager`
- `StatisticHooks` 的 `LogMonitorConfigManager#b()Z` 已确认
- `HintHooks` 的统一入口
- ABTest 的 `abtest2.b`、`StartupABManager` 方法名

## 混淆名变化观察

不同构建之间两字母包名会整体平移：

```
q60 → o60    th0 → nh0    rh0 → lh0    tt0 → mt0
vt0 → ot0    lv0 → ev0    fw0 → yv0
```

这源于 dex 中插入新类导致后续短名整体后移。**不能依赖这个规律做批量改写**——
一旦插入的类数量变化就全错，锚点才是可靠手段。

## 定位策略

1. **能用具名类的直接用**，不需要任何定位逻辑。
2. **混淆类只走 DexKit 锚点 + 结构校验**，把发现的类名写进 per-version profile。
3. **不要硬编码跨版本的混淆坐标**，只保留 hook 逻辑与 profile 抽象。
4. `dr.h` 当前可硬编码，但需在代码中注明是巧合、换版本需重验。
