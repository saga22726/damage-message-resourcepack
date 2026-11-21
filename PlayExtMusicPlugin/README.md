# PlayExtMusicPlugin

Paper 1.21.8 / Java 21 插件，提供 `/playextmusic`。插件將音檔片段轉成 OGG，累積放進同一個固定 URL 的資源包；相同片段重播時不重新建立或傳送資源包。首次加入伺服器時會載入現有音效包；以後新增不同片段時，目標玩家只需載入更新版一次。

## 前置條件

- Paper 1.21.8、Java 21。
- 伺服器安裝支援 `libvorbis` 的 ffmpeg。
- 玩家可以連線到 `http.public-base-url` 與設定連接埠，並接受伺服器資源包。
- 若伺服器有其他資源包，需先整合資源包；Minecraft 客戶端的伺服器資源包會互相取代，不能各自獨立疊加。

## 安裝與設定

1. 在 `PlayExtMusicPlugin` 目錄執行 `mvn clean package`，把 `target/PlayExtMusicPlugin.jar` 放入伺服器 `plugins/`，重啟。
2. 將要用的 MP3/WAV/OGG 放入設定的 `music-directory`（預設 `plugins/DamageMessagePlugin/musicSrc`）。
3. 編輯 `plugins/PlayExtMusicPlugin/config.yml`，將 `http.public-base-url` 設成玩家可連線的公開網址，例如 `http://你的伺服器IP:8766`，並確保防火牆/NAT 開放 TCP 8766。
4. 玩家第一次呼叫尚未加入資源包的音效時，會收到資源包載入提示；已載入後可直接播放。玩家重新加入時會預載目前資源包。

## 使用方式

```text
/playextmusic <file> <start> <end> [source] [targets] [x y z [volume [minVolume]]]
```

時間可用秒數或 `分:秒`，例如 `0:01`、`1:20`。選項依序提供；要指定座標須先給 source 和 targets。範例：

```text
/playextmusic going_4_gold.mp3 0:00 0:08 ambient @a
```

第一次播放一段新的片段時，插件會轉換並加入固定的 `playextmusic-preload.zip`。同一個來源檔案與時間片段之後重播不會再次載入。若上傳新音檔或使用不同時間範圍，資源包內容會增加，目標玩家需載入更新版一次；這是 Minecraft 資源包更新本身的限制。產生的包與轉換快取在 `plugins/PlayExtMusicPlugin/packs`，不要手動刪除 `clips` 資料夾，除非願意重新轉換片段。
