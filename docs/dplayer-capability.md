# DPlayer 能力清单（本社区视频播放选型）

> 播放器：[DIYgod/DPlayer](https://github.com/DIYgod/DPlayer) · 官方文档：[Guide](https://dplayer.diygod.dev/guide.html) · [中文文档](https://dplayer.diygod.dev/zh/)
> 用途：帖子详情 / Feed 内视频点击后统一用 DPlayer 展示。
> 本文只整理「框架能做什么 + 本项目怎么扩展」，**不代替官方文档**。

---

## 1. 一句话结论

DPlayer 是 HTML5 **弹幕向**播放器，本社区一期够用：**MP4/WebM 点播 + 封面 + 倍速 + 全屏 + 切源**。
后续可按需开：**清晰度、缩略图预览、字幕、弹幕、HLS/FLV 流、直播**。

安装：`npm install dplayer --save`（MIT）。

---

## 2. 功能总表（按扩展优先级）

| 能力                       | 官方支持         | 一期（帖子视频）      | 二期/以后            | 扩展时看哪里                                                                               |
| -------------------------- | ---------------- | --------------------- | -------------------- | ------------------------------------------------------------------------------------------ |
| MP4 / WebM / Ogg 点播      | ✅               | ✅ 必做               | —                    | `video.url` + `type: 'auto'/'normal'`                                                      |
| 封面海报                   | ✅               | ✅                    | —                    | `video.pic` ← 帖子封面                                                                     |
| 自动播放                   | ✅               | ❌（移动端限制多）    | 可选静音自动播       | `autoplay`                                                                                 |
| 循环                       | ✅               | ❌                    | 可选                 | `loop`                                                                                     |
| 主题色                     | ✅               | ✅ 用品牌橙 `#ff6600` | —                    | `theme`                                                                                    |
| 中文 UI                    | ✅               | ✅                    | —                    | `lang: 'zh-cn'`                                                                            |
| 默认音量 / 记忆音量        | ✅               | ✅                    | —                    | `volume`                                                                                   |
| 倍速                       | ✅               | ✅                    | 可改档位列表         | `playbackSpeed` / `dp.speed()`                                                             |
| 快捷键（进退、音量、空格） | ✅               | ✅                    | —                    | `hotkey`                                                                                   |
| 截图                       | ✅               | 可选                  | —                    | `screenshot`（需 CORS）                                                                    |
| AirPlay / Chromecast       | ✅               | ❌                    | 可选                 | `airplay` / `chromecast`                                                                   |
| Logo 水印                  | ✅               | ❌                    | 品牌角标             | `logo` + CSS                                                                               |
| 进度条时间点标记           | ✅               | ❌                    | 章节/高光            | `highlight[]`                                                                              |
| 右键菜单自定义             | ✅               | ❌                    | 「举报」「复制链接」 | `contextmenu[]`                                                                            |
| 互斥播放（同时只播一个）   | ✅               | ✅ 推荐               | —                    | `mutex: true`                                                                              |
| 禁止点击切换播放           | ✅               | 看交互                | —                    | `preventClickToggle`                                                                       |
| 清晰度切换                 | ✅               | ❌                    | 多码率上线后         | `video.quality` + `dp.switchQuality`                                                       |
| 进度条缩略图               | ✅               | ❌                    | 体验增强             | `video.thumbnails` + [DPlayer-thumbnails](https://github.com/MoePlayer/DPlayer-thumbnails) |
| 外挂字幕 WebVTT            | ✅               | ❌                    | 字幕社区             | `subtitle`                                                                                 |
| 弹幕（点播 API）           | ✅               | ❌                    | 社区弹幕             | `danmaku` + 自建 API                                                                       |
| 弹幕（B 站 addition）      | ✅               | ❌                    | 一般不用             | `danmaku.addition`                                                                         |
| 直播 + 实时弹幕            | ✅               | ❌                    | 直播业务             | `live` + `apiBackend` + WS                                                                 |
| HLS (m3u8)                 | ✅ 需 hls.js     | ❌                    | 大文件转码分发       | `type: 'hls'`                                                                              |
| FLV                        | ✅ 需 flv.js     | ❌                    | 特殊源               | `type: 'flv'`                                                                              |
| MPEG-DASH                  | ✅ 需 dash.js    | ❌                    | CDN 自适应           | `type: 'dash'`                                                                             |
| WebTorrent                 | ✅ 需 webtorrent | ❌                    | 不计划               | `type: 'webtorrent'`                                                                       |
| 任意 MSE / P2P             | ✅ `customType`  | ❌                    | CDNBye 等            | `video.customType`                                                                         |
| 切换视频源（不销毁实例）   | ✅               | ✅ Feed 点下一条      | —                    | `dp.switchVideo()`                                                                         |
| 播放 / 暂停 / 跳转 / 销毁  | ✅               | ✅                    | —                    | API 见 §4                                                                                  |
| 原生 video 句柄            | ✅               | 统计进度用            | 完播率               | `dp.video`                                                                                 |

---

## 3. 本社区场景映射

### 3.1 一期最小接入（帖子视频）

```js
import DPlayer from 'dplayer';

const dp = new DPlayer({
  container: el,
  theme: '#ff6600',
  lang: 'zh-cn',
  hotkey: true,
  mutex: true,
  preload: 'metadata',
  volume: 0.7,
  playbackSpeed: [0.75, 1, 1.25, 1.5, 2],
  video: {
    url: article.videoUrl, // 过审后的公网 MinIO URL
    pic: article.coverUrl, // 封面
    type: 'auto', // mp4/webm 足够
  },
});
```

对应产品：

- Feed / 详情点击视频 → 打开播放层（Modal / 详情页内嵌）
- 作者预览待审视频 → 用 **Presigned URL** 当 `video.url`（注意过期刷新）
- 切帖子：优先 `switchVideo`，离开页 `destroy`

### 3.2 建议二期

| 产品需求            | DPlayer 能力                                |
| ------------------- | ------------------------------------------- |
| 高清 / 标清         | `video.quality` + 后端多码率                |
| 拖拽预览画面        | `video.thumbnails`                          |
| 弹幕评论            | 自建 danmaku API + `danmaku.id = articleId` |
| 大视频 CDN 切片     | 转 HLS + `hls.js`                           |
| 举报 / 分享         | `contextmenu`                               |
| 高光时刻            | `highlight`                                 |
| 完播 / 播放进度埋点 | `dp.on('timeupdate'                         | 'ended' | …)` |

### 3.3 暂不建议

- 直播弹幕（要独立 WS 后端）
- WebTorrent / 复杂 P2P（除非明确带宽策略）
- 默认自动播放（移动端兼容差）

---

## 4. API 速查（扩展时用）

官方完整说明见 [Guide · API](https://dplayer.diygod.dev/guide.html)。

### 4.1 控制

| 方法                                       | 作用                 |
| ------------------------------------------ | -------------------- |
| `dp.play()` / `dp.pause()` / `dp.toggle()` | 播放控制             |
| `dp.seek(seconds)`                         | 跳转                 |
| `dp.speed(rate)`                           | 倍速                 |
| `dp.volume(0~1, nostorage?, nonotice?)`    | 音量                 |
| `dp.switchVideo(video, danmaku?)`          | 换片                 |
| `dp.switchQuality(index)`                  | 切清晰度             |
| `dp.notice(text, ms?, opacity?)`           | 右上提示             |
| `dp.destroy()`                             | 销毁（路由离开必调） |

### 4.2 弹幕（二期）

| 方法                                             | 作用                           |
| ------------------------------------------------ | ------------------------------ |
| `dp.danmaku.send(danmaku, cb)`                   | 发弹幕到后端                   |
| `dp.danmaku.draw(danmaku)`                       | 仅本地绘制（直播 WS 推下来用） |
| `dp.danmaku.opacity` / `clear` / `hide` / `show` | 显示控制                       |

### 4.3 全屏

| 方法                                      | 作用   |
| ----------------------------------------- | ------ |
| `dp.fullScreen.request('browser'\|'web')` | 进全屏 |
| `dp.fullScreen.cancel(...)`               | 退全屏 |

### 4.4 事件（埋点 / UI 同步）

**视频原生类**：`play` `pause` `ended` `error` `timeupdate` `seeking` `volumechange` `ratechange` …

**播放器类**：`screenshot` `quality_start/end` `fullscreen` `destroy` `danmaku_*` `subtitle_*` `notice_*` …

```js
dp.on('ended', () => {
  /* 完播统计 */
});
dp.on('error', () => {
  /* 坏链提示 */
});
```

---

## 5. React 接入注意（本仓库）

| 点              | 说明                                                                                                                                      |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 不是 React 组件 | 用 `useRef` + `useEffect` 挂载；卸载 `destroy()`                                                                                          |
| 社区封装        | 建议 `base-ui/VideoPlayer` 或 `components/DPlayerHost`，业务只传 `url/pic`                                                                |
| 样式            | 主题色跟 DESIGN 主色；容器宽度跟内容盒走                                                                                                  |
| CORS            | 截图、跨域视频需 MinIO/CDN 配 CORS                                                                                                        |
| 依赖体积        | HLS/FLV **按需**再装 `hls.js` / `flv.js`，一期不要打进首包                                                                                |
| 参考封装        | [react-dplayer](https://github.com/hnsylitao/react-dplayer)、[rc-dplayer](https://github.com/tianfeng98/rc-dplayer)（可参考，不必强依赖） |

目录建议（落地时）：

```
src/base-ui/VideoPlayer/     # ✅ 已落地：DPlayer 封装（16:9 / 浮动拖动 / ±5s / 倍速）
src/components/PostVideo/    # 帖子场景：封面点击 → 播放（待接）
```

---

## 6. 与现有发帖链路的关系

| 阶段        | 视频地址                 | 是否适合 DPlayer                    |
| ----------- | ------------------------ | ----------------------------------- |
| 分片上传中  | 本地 blob / 未合并       | 可用本地 `URL.createObjectURL` 预览 |
| 草稿 / 待审 | `pending://` → Presigned | ✅ 作者预览                         |
| 已发布      | 公共桶 URL               | ✅ Feed / 详情                      |

发帖存储与审核见：`game-community-platform/docs/content-posting-design.md`。

---

## 7. 官方与生态索引（找扩展时从这里跳）

| 资源             | 链接                                              |
| ---------------- | ------------------------------------------------- |
| GitHub           | https://github.com/DIYgod/DPlayer                 |
| 英文 Guide       | https://dplayer.diygod.dev/guide.html             |
| 中文文档         | https://dplayer.diygod.dev/zh/                    |
| 缩略图工具       | https://github.com/MoePlayer/DPlayer-thumbnails   |
| 弹幕 Node 示例   | https://github.com/MoePlayer/DPlayer-node         |
| 直播弹幕 WS 示例 | https://github.com/Izumi-kun/dplayer-live-backend |

---

## 8. 扩展决策备忘（避免重复踩坑）

1. **一期只接点播 MP4/WebM**，弹幕/HLS 等业务真要做再加依赖。
2. **页面级只保留一个活跃实例**（`mutex` + 路由 `destroy`）。
3. **待审预览**要处理 Presigned 过期：播放前刷新 URL 或 `switchVideo`。
4. **截图 / 跨域**依赖 MinIO 公共桶 CORS，未配好不要开 `screenshot`。
5. 需要「列表很多视频」时：默认只渲染封面，点击再 `new DPlayer`，避免 DOM 爆炸。
