
---

# koko
**「目的地まで歩くワクワクを。」 — 散歩を宝探しに変える位置情報SNS**

## 📝 概要
`koko` は、その場所の近く（50m/100m以内）に足を運ばないと、投稿された写真を見ることができない位置情報連動型SNSです。
「ネットで何でも見られる今だからこそ、あえて現地に行く価値を作る」ことをコンセプトに、ユーザーの外出のきっかけをデザインします。

## ✨ 主な機能
* **Distance Lock:** 現在地から指定範囲内に入った投稿のみ、写真の内容を確認可能。
* **Dynamic Pins:** 範囲内に入るとピンが「ポコッ」と大きくなるアニメーションUI。
* **One-Tap Capture:** 画面中央のボタンから、現在地と紐づいた写真を即座に投稿。
* **Photo Gallery:** 自分や他人の投稿をリスト形式で振り返る機能。
* **Radius Switch:** 探索範囲を「50m / 100m」で切り替え。

## 🚀 開発の背景
「外に出るきっかけがなければ、なかなか家を出ない」という自分自身の悩みから着想しました。プログラミングの力で、自分や誰かの行動をポジティブに変える「トリガー」を作りたいという想いで開発しました。
スピード感を重視し、企画から実装までを2日間で完遂。実機テストでのメモリ負荷問題を、画像の動的リサイズ処理によって解決し、屋外でも安定して動作する品質を追求しました。

## 🛠 技術スタック
| カテゴリ | 使用技術 |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM + StateFlow |
| **Maps** | Google Maps SDK for Android |
| **Backend** | Firebase (Auth / Firestore / Storage) |
| **Image Loading** | Coil (with Memory Optimization) |

## 🗄 データベース構造 (Firestore)
`posts` コレクションに、以下の構造でデータを保持しています。

```json
{
  "id": "String (Document ID)",
  "userId": "String (投稿者のUID)",
  "photoUrl": "String (Firebase StorageのURL)",
  "latitude": "Double (緯度)",
  "longitude": "Double (経度)",
  "timestamp": "Timestamp (投稿日時)"
}
```

## ⚙️ こだわったポイント
* **パフォーマンス最適化:** 多数のピンを地図に表示する際、Coilを使用して読み込み時点で画像をリサイズ（40dp〜64dp相当）することで、メモリ不足によるクラッシュを完全に防止しました。
* **ユーザー体験 (UX):** `animateDpAsState` を活用し、範囲内に入った瞬間にピンが滑らかに大きくなる演出を入れることで、発見の喜びを視覚的に表現しました。

---
<img width="1080" height="2400" alt="Screenshot_2026-04-16-11-17-57-429_package com examp lemyapplication" src="https://github.com/user-attachments/assets/b4a21b25-3802-47fa-a782-0faed551d959" />



<img width="1080" height="2400" alt="Screenshot_2026-04-16-11-18-15-105_package com examp lemyapplication" src="https://github.com/user-attachments/assets/17b64e9f-b6f6-45f3-9c7e-cb654981f41c" />



<img width="1080" height="2400" alt="Screenshot_2026-04-16-11-18-20-034_package com examp lemyapplication" src="https://github.com/user-attachments/assets/86d888d8-2bf7-4eeb-92dc-7aaefba36dbd" />
