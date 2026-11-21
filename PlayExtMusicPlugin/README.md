# PlayExtMusicPlugin

獨立的 Paper 1.21.8 / Java 21 Maven 專案，提供 `/playextmusic`。音檔不是由 Minecraft 客戶端直接讀取 MP3；插件使用 ffmpeg 擷取片段、轉成 OGG，產生 Minecraft 資源包，送給目標玩家，玩家成功載入後再播放。

## 前置條件與限制

- 伺服器使用 Paper 1.21.8 與 Java 21。
- 伺服器主機須安裝 ffmpeg，且 ffmpeg 支援 `libvorbis`。
- 玩家必須能連到設定的 HTTP 公開網址與連接埠，並接受伺服器資源包。
- 這個插件動態送出資源包。它可能與另一個伺服器資源包衝突；若你的 DamageMessagePlugin 已送資源包，建議將兩套資源整合成單一資源包後再使用。
- minVolume 對遠於音量範圍的玩家以在玩家位置播放低音量方式近似原版效果。

## 建置與安裝

1. 進入 `PlayExtMusicPlugin`，執行 `mvn clean package`。
2. 將 `target/PlayExtMusicPlugin.jar` 放入伺服器 `plugins/`，重啟。
3. 編輯 `plugins/PlayExtMusicPlugin/config.yml`，把 `http.public-base-url` 改成玩家可連線的主機網址；若使用防火牆/NAT，開放設定的 TCP 連接埠（預設 8766）。
4. 預設音檔目錄為 `plugins/DamageMessagePlugin/musicSrc`。把 MP3/WAV 等檔案放進去；若 DamageMessagePlugin 資料夾不存在，建立它與 `musicSrc` 子目錄即可。
5. 重啟插件或伺服器後執行指令。

## 指令

```text
/playextmusic <file> <start> <end> [source] [targets] [x y z [volume [minVolume]]]
```

時間可以是秒數或 `分:秒`，例如 `0:01`、`1:20`、`80`。選項必須依序提供；要指定座標，就先提供 source 和 targets。source 支援 `master music record weather block hostile neutral player ambient voice`。目標支援 Bukkit/Paper 實體選擇器，但只有玩家能接收音效。

```text
/playextmusic XXX.mp3 0:01 0:20 ambient @a ~ ~ ~ 1 1
```

生成的 ZIP 和短暫 OGG 檔位於 `plugins/PlayExtMusicPlugin/packs`；ZIP 由插件的 HTTP 服務提供下載。
