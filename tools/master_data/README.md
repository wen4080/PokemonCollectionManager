# 主資料產生工具

這組工具只在 Windows 筆電執行。Android App 不會在啟動時解析上游資料，而是讀取這裡產生的 JSON 與圖片資產。

建議流程：

```powershell
python fetch_master_data.py --output-dir downloads
python parse_game_master.py --input-dir downloads --output-dir generated
python fetch_background_previews.py --output-dir ../../app/src/main/assets/images/background_previews --manifest generated/background_preview_manifest.json
python build_pokemon_catalog.py --input-dir downloads --parsed-dir generated --output-dir generated --background-preview-manifest generated/background_preview_manifest.json --version 2026.09.29.1
python build_asset_manifest.py --manifest generated/master_manifest.json --output generated/background_asset_manifest.json --asset-dir ../../app/src/main/assets --download --groups backgrounds
python -m pip install --target ../../.tooling/python-packages Pillow==11.3.0
$env:PYTHONPATH = (Resolve-Path '../../.tooling/python-packages').Path
python optimize_background_assets.py --manifest generated/master_manifest.json --asset-root ../../app/src/main/assets --output-manifest generated/master_manifest_optimized.json --quality 84 --max-size 512 --prune-sources
python validate_master_data.py --manifest generated/master_manifest_optimized.json --asset-root ../../app/src/main/assets --require-background-assets
```

型態名稱會依序使用 `form_name_overrides.json` 的物種專用校正、Pokémon GO 繁體中文資源，以及產生器內已確認的共用名稱。這能避免共用上游代號造成錯誤翻譯，例如土龍節節必須是「二節形態／三節形態」，而一家鼠才使用官方的「三隻家庭／四隻家庭」。蒼響與藏瑪然特的 `HERO` 也會顯示為「百戰勇者」，不會套用波普海豚的「全能形態」。

型態產生也會先檢查物種是否真的有獨立的 `NORMAL`。若 Game Master 只有實際存在的替代型態，就不會為該物種硬補「一般型態」；例如土龍節節只顯示二節形態／三節形態，一家鼠只顯示三隻家庭／四隻家庭。若上游完全沒有該物種的型態資料，才保留一般型態作為安全預設，避免暫時缺資料時無法建立收藏。`master_manifest.json` 的 `formPolicyAudit` 會記錄這次判定，驗證器會檢查清單與實際型態是否一致。

每次產生都會輸出 `formNameAudit`。若新上游型態只有技術代號、沒有可靠繁中名稱，產生器會標記需要確認，正式驗證會阻止這筆未整理的暫名進入可發布資料，避免自主更新把錯誤名稱直接送到手機。

將 `generated` 內的 `pokemon_species.json`、`pokemon_forms.json`、`pokemon_costumes.json`、`pokemon_backgrounds.json`、`master_manifest.json` 複製到：

`app/src/main/assets/master/`

圖片可放在：

`app/src/main/assets/images/`

缺圖不會讓 App 啟動失敗；產生器會列出缺少數量，App 也會顯示圖片佔位圖並保留名稱。

正式建置必須加上 `--require-background-assets` 驗證。這會要求每一張可選背景的優先靜態圖片都已實際打包，不能只因 JSON 有 `imageKey` 就誤報為已收錄。`build_asset_manifest.py --groups backgrounds --download` 只下載背景相關二維圖片，不會下載 3D 模型、聲音或其他不需要的素材。

`optimize_background_assets.py` 會把背景轉成最長邊 512 像素、保留透明度的 WebP，並在成功後移除 APK 不再使用的原始大圖。原始 PokeMiners 圖片鍵會保留在 `sourceImageKey`／`sourcePreviewImageKey`，不會失去來源追蹤。這能大幅降低 APK 大小與手機 Grid 解碼負擔。

解析器會同時處理兩種 Pokémon GO 圖片資產格式：舊版 `Addressable Assets/pm*.c*.icon.png`，以及活動型態使用的 `pokemon_icon_pm*_pgo_*.png`／`pm*.f*.icon.png`。活動型態會轉成裝扮相容資料，不會只停留在「特殊型態」清單。

背卡會同時掃描 `Images/LocationCards/lc_*.png` 與 `sb_*.png`。能由 Game Master 高信心配對的圖片會綁定既有背卡；無法安全判讀的圖片會建立穩定的 `ASSET_` 背卡鍵，並保留縮圖與可讀名稱，避免因代號格式不同而漏資料或錯配。

Game Master 若提供 `vfxAddress`，產生器會另存為 `vfxKey`。`sb_*.png` 通常只是遊戲卡面的靜態底圖；App 會標示遊戲內另有動態效果，且不會把未取得的特效層冒充為完整靜態圖片。

`fetch_background_previews.py` 只在建置階段下載公開收藏索引中的完整靜態預覽，並精確選取頁面內 `alt="Background"` 的當前背卡，不會誤抓上一張／下一張背卡。產生結果會記錄來源頁與原圖網址；App 優先顯示完整預覽，找不到時才退回 PokeMiners 靜態底圖並明確標示缺少特效圖層。

Game Master 內同一張收藏背卡可能依 Pokémon 或型態列出多個 VFX 代號。產生器會將確認屬於同一收藏背卡的代號合併，例如 GO Fest 2025 傳說巨人、蒼響／藏瑪然特，以及 Mega Finale；但若主資料本身提供不同且可辨識的收藏款式（例如 GO Fest 2026 超夢 001／002），則保留為不同背景，不會因共用靜態底圖而錯誤合併。沒有獨立靜態圖層時，主資料會保留 VFX 代號並在 App 顯示限制說明。

若 Game Master 同一筆資料同時提供 `locationCard` 與 `imageUrl`，產生器會優先採用這個直接關係，再對剩餘圖片做保守比對。驗證器也會確認 2D 背卡圖片全部被主資料涵蓋，並阻擋同一圖片同時出現正式 ID 與 `ASSET_` 暫存 ID 的重複資料。

裝扮除圖片檔名規則外，也會以 Game Master 的 `formSettings.isCostume` 為權威依據；驗證器會在任何一筆已標示裝扮的型態漏匯時失敗。

若上游只先發布圖片資產或暫時沒有正確填寫 `isCostume`，解析器會再使用
`costume_classification.json` 的通用語意標記判斷，例如服裝、帽子、太空合作、制服、頭盔等。
這不是針對單一 Pokémon 的特例：未來新活動裝扮只要出現在 Game Master 或圖片資產中，
就會依同一套規則轉成裝扮主資料與 Pokémon 相容關係，不會落入「未命名型態」。
其中 `ASTRONAUT`／`ESA` 只代表目前已知的太空合作語意，並不會與 `WCS` 世界賽裝扮合併。

舊版 `pokemon_icon_001_00_11.png` 這類純數字圖示會納入覆蓋率稽核，但不直接建立第二套名稱不明的重複裝扮。驗證器會要求每個含純數字裝扮圖的物種都有具名相容資料，且具名款式數不得少於數字圖示款式數。

背景來源屬於遊戲主資料與圖片資產，不等同已正式公開或已在遊戲中發放。只有活動日期明確晚於手機當日的項目才會標示為「尚未推出／拆包資訊」；已過年份不再使用拆包標籤，只有圖片但日期不明者則標示為「活動日期待確認」。

## 手機端自主更新

應用程式的「設定 → 主資料與圖片 → 自主更新」可讀取一個 HTTPS 靜態網址。該網址必須直接回傳本工具產生的 `master_manifest.json`，可放在 GitHub Release、GitHub Pages 或其他不需要自架伺服器的靜態檔案空間。

手機每 24 小時最多自動檢查一次，也可手動立即檢查；相同 `masterVersion` 不會重複匯入。主資料更新採停用舊列、寫入新列的方式，不會刪除既有收藏引用。圖片仍依序使用手機快取、應用程式內建資產、主資料中的 HTTPS 圖片或 PokeMiners 靜態圖片，缺圖時顯示佔位圖。

上游來源不是單一、可直接貼進 App 的更新網址：

- PokeMiners `game_masters` 提供遊戲主資料。
- PokeMiners `pogo_assets` 提供拆包二維圖片與文字。
- Dittobase 與 Bulbagarden Archives 只用於補足、交叉核對完整靜態背卡預覽。

因此設定頁的更新欄位只能填入「整理完成的 `master_manifest.json` 直接下載網址」，不能填上述網站首頁或原始 Game Master。建議把產生結果放在自己的 GitHub Release 或 GitHub Pages，這不需要維護後端伺服器。

若遠端更新也要使用自己託管的背景圖片，先保持 `app/src/main/assets/images/` 的目錄結構上傳，再執行：

```powershell
python build_remote_update_manifest.py --manifest generated/master_manifest.json --output generated/master_manifest_remote.json --asset-base-url https://你的靜態網站/主資料版本
```

把輸出的 `master_manifest_remote.json` 直接下載網址填入 App。此檔會讓背景圖片指向同一個靜態資產根網址；新背卡只需重新執行 Windows 產生流程並替換靜態檔，不需要修改 Android 程式或重新發 APK。

專案另附 `.github/workflows/update-master-data.yml`。把專案放進自己的 GitHub 儲存庫並將 Pages 的來源設為 GitHub Actions 後，工作流程會每天自動執行同一套下載、產生、背景完整性驗證及靜態網站發佈。App 應填入的網址會是：

`https://你的帳號.github.io/儲存庫名稱/master-data/master_manifest.json`

這種方式不需要自架伺服器。不過上游拆包代號不一定能自動推導正式活動名稱；新資料可以先以可讀代號與靜態底圖出現，活動名稱、日期及完整動態預覽仍須由資料產生規則或可靠公開資料補充，App 不會猜測不存在的關係。

