# 9.5.81 hook 点逐条核验结果

APK: 网易云音乐 9.5.81 (9005081)，MT 工作区 `qfj9xfug`
核验方式：`mt_apk_dex_read_class_outline` 读取真实方法表，逐个对照

## 判定说明

- ✅ **可直接用**：方法存在，签名与代码一致
- ⚠️ **需改实现**：方法存在但签名/思路不同
- ❌ **不存在**：类里没有这个方法，hook 永远挂不上
- 🚫 **不可行**：宿主没有对应能力，应移除该功能

---

## AD 广告净化

### AD-01 开屏

| 挂载点 | 结果 | 实证 |
|---|---|---|
| `LoadingAdManager.E(AdInfo)Z` | ✅ | 存在，public |
| `LoadingAdManager.G(Activity)V` | ✅ | 存在，public |
| `LoadingAdManager.o(Z)ArrayList` | ✅ | 存在，public |
| 循环拦所有 `(AdInfo)Z` | ❌ | 类里**没有**别的 `(AdInfo)Z`。`B`/`C` 是 `(b1,I,Z,Z)`，`z` 是 `(AdInfo,I,Z,J)` → 循环空转，应删 |
| `LoadingAdActivity.onCreate(Bundle)` | ✅ | 存在 |
| `LoadingAdFragment.onViewCreated` | ✅ | 存在（继承自 Fragment） |
| `LoadingAdShakeHelper.onSensorChanged(SensorEvent)` | ✅ | 存在，public，124 条指令 |
| `o60.w0.g()Z` | ✅ | 存在，字节码确认极性 |

**AD-01 结论：可用，删掉那段空转循环即可。**

### AD-02 运营弹窗

`LaunchDialog` 全部方法是 **static**：
- `showDialog(Activity, Data)` public static
- `showCompactDialog(Activity, Data)` public static
- `showPictureDialog(Activity, Data, OnDismissListener)` public static
- `showSinglePicDialog(Activity, Data)` public static
- `showMultiModulePopUpDialog(Activity, Data)` private static
- `showOnePassLoginDialog(Activity, Data)` private static
- `checkIsLoginDialog(Data)Z` private static

我的代码找 `show`/`showDialog`/`a` 三个名字 → `showDialog` 有重载（2 参和 3 参），`findMethod` 返回 null。

⚠️ **需改**：分别 hook `showCompactDialog` / `showPictureDialog` / `showSinglePicDialog` / `showMultiModulePopUpDialog`，**跳过** `showOnePassLoginDialog`（一键登录要保留），也跳过 `showDialog`（它内部会按类型分发，勾掉它会连登录弹窗一起干掉）。

### AD-03 上报与外跳

| 挂载点 | 结果 |
|---|---|
| `AdCloudReportTask.execute()Z` | ✅ 存在（314 条指令） |
| `StartAppHelper$b.startApp(Activity,Intent,Listener,Z)Z` | ✅ 存在，4 参 |
| `AdExposureDeeplinkPreRequestTask.init()` | ✅ 存在 |
| `AdExposureDeeplinkTask.init()` | ✅ 存在 |

**AD-03 结论：可用。**

### AD-04 搜索广告

`RNSearchAdViewManger` 里**没有任何返回 View 的无参方法**。真实入口：
- `showAdLargeView(RNSearchAdView, Ad, I, String)` private
- `showAdMediumView(...)` private
- `hideAllViews()` private
- `createViewInstance(ThemedReactContext)` protected → 返回 RNSearchAdView

已实装：hook `showAdLargeView` / `showAdMediumView`（均返回 void，直接跳过），保留 RN 侧正常的
`createViewInstance`。**不要**用 `new View(context)` 顶替 `createViewInstance` 的返回值：它声明返回
`RNSearchAdView`（另有同名的 PUBLIC BRIDGE SYNTHETIC 重载返回擦除后的 View），调用方会按声明类型
checkcast；而且原方法还负责设置 `mEmitter`、订阅 ViewModel 的 LiveData、写入 `mViewRef`，跳过它会让
后续 `setExtraParams` / `hideAllViews` 拿到空字段。

### AD-05 预加载

| 类 | 结果 |
|---|---|
| `AdColdBootEntranceTask.init()` | ✅ 存在（41 条指令） |
| `PodcastRewardAdTask.init()` | ✅ 存在（15 条指令） |
| `APreloadRecommendViewTask.init()` | ✅ 存在（6 条指令） |

**AD-05 结论：可用。**

### AD-06/07 信息流与评论

`Banner` 有 283 个方法，需要确认 `isAdBanner` 是否存在。

---

## UI 精简

### UI-01 更新弹窗

`CMUpdateConfig` 只有 8 个方法，**全部是查询或跳转**：
- `hasNewVersion()Z` public static
- `getLatestVersionName()String` public static
- `getDefaultDownloadUrl()String` public static
- `cmUpdateConfig(v)d` public static
- `updateConfig(v)d` public **实例**
- `checkNewVersion(Context, w, d)` public **实例**
- `getPreferences(Context)` private

**我的代码找名字含 `update`/`download` 的 boolean 无参方法 → 只有 `hasNewVersion` 符合，但它是 static。**

`appupdate.d` 里才是真正判断：
- `D()Z` → 读 `forceUpdate`
- `E()Z` → 读 `updateViaMarket`
- `I()Z` → 读 `needPopUp`
- `H()Z` → 结合 `y()`（apkRemindVersionCode）
- `C()Z` / `B()Z` → 综合判断

⚠️ **需改**：hook `hasNewVersion()` → false（一行解决），再兜底 `d.D()` / `d.I()` → false。

### UI-02 启动延迟

`StartTimeHandler` 的方法：
- `checkDelayDoAction(Runnable, String)` public static final
- `checkDelayMainPageView(View)` public static final
- `checkDelayMainPage(RecyclerView)` public static final
- `isNeedDelayStart()Z` public static final
- `getStartAppMainPageDelayTime()J` public static final
- `getStartAppRedPageDelayTime(String)` public static final

我的代码找名字含 `delay/wait/sleep` 的 void 方法 → 会错误命中 `lambda-4`/`lambda-5`（Thread.sleep），**不是入口**。

⚠️ **需改**：hook `isNeedDelayStart()` → false，或 `getStartAppMainPageDelayTime()` / `getStartAppRedPageDelayTime(String)` → 0L。

### UI-03 首页模块

| 挂载点 | 结果 |
|---|---|
| `MixContainerData.getBlockCodeOrderList()String` | ✅ 存在 |
| `MixContainerData.getResources()List` | ✅ 存在 |
| `DiscoveryBlockData.getBlockList()List` | ✅ 存在 |

**UI-03 结论：坐标正确。问题只在设置页没有输入框（hideSet 恒为空）。**

### UI-04 顶部频道

| 挂载点 | 结果 |
|---|---|
| `dr.h.T0(List)V` | ✅ 存在，字节码确认 |
| `HomeTopTabInfo.getCanDelete()Boolean` | ✅ 存在 |
| `HomeTopTabInfo.getCanDrag()Boolean` | ✅ 存在 |

**UI-04 结论：坐标正确。问题在 keepSet 读错了 key。**

### UI-07 引导

`PushGuideHintView.maybeDisplayHint(AbsHintContainer, a, r)` ✅ 存在，public。

**UI-07 结论：可用。**

---

## 隐私保护

### PV-01 OAID

| 挂载点 | 结果 |
|---|---|
| `MusicOaidUploadManager.u()V` | ✅ public static final |
| `MusicOaidUploadManager.p(String)V` | ✅ private |
| `MusicOaidUploadManager.r(String)V` | ✅ private |
| `MusicOaidUploadManager.v(String)V` | ✅ private |
| `MusicOaidUploadManager.o(String)V` | ✅ public static final ← **真正的入口** |
| `AdOaidUploadTask.init()V` | ✅ 存在（8 条指令） |

⚠️ **需改**：`o(String)` 才是一切的起点（`init()` 里调的就是它），应优先 hook 它。

### PV-02 日志写盘

`NeedLogToFileTask.init()V` ✅ 存在。

### PV-04 APM / PV-05 统计

`StatisticImpl` 方法全部存在：
- `logApm(fastjson.JSONObject)` ✅ public
- `forceApmUpload()V` ✅ public
- `forceUpload()V` ✅ public
- `logMonitor(...)` / `logRealTimeMonitor(...)` ✅ public

⚠️ **需改**：`logApm` 的参数是 `com.alibaba.fastjson.JSONObject`（不是 `org.json`），已用名字匹配解决。

### PV-05 监控开关

`LogMonitorConfigManager.b()Z` ✅ public static final。

---

## 需要彻底重写的

| 项 | 原因 |
|---|---|
| AD-02 | 找错方法名，4 个真实入口都没挂上 |
| AD-04 | 已改为跳过 `showAdLargeView` / `showAdMediumView`；**不**拦 `createViewInstance`（返回类型 + manager 状态） |
| AD-09 | `AdAudioClipManager` 无 View getter，要改 hook `e(String,String)` / `isPlaying()` |
| UI-01 | 找错类，应 hook `CMUpdateConfig.hasNewVersion()` |
| UI-02 | 找错方法，应 hook `isNeedDelayStart()` |
| PV-01 | 漏了真正的入口 `o(String)` |
| UI-03 / UI-04 设置页 | 缺输入框，key 读错 |
