# SnapToMD 📱✨

「撮る ➔ 共有する ➔ 待つ」だけで、整理されたMarkdownノートが手に入る爆速ナレッジ化ツール

## 💡 Why SnapToMD?

「あとでまとめよう」と思ったスクリーンショットや写真、カメラロールに埋もれていませんか？
気になるWeb記事、ECサイトの商品情報、イベントのチラシ……。スマホで手軽にキャプチャできる反面、それで満足して忘れちゃいますよね。
**SnapToMD** は、スマホからの「インプットの摩擦」をゼロにするために生まれました。
Androidの「共有（Share）」メニューから画像を放り込むだけで、あとはバックグラウンドでGemini AIが勝手に情報を読み取り、あなた好みのスタイルにMarkdownノートへと変換します。

## 🎬 How it Works

アプリを立ち上げて文字を打ち込む必要すらありません。

### 📸 撮る

カメラで写真を撮るか、気になる画面のスクリーンショットを撮ります。

## 📤 送る

ギャラリーやブラウザの「共有」メニューから SnapToMD を選んでタップします。

### ⏳ 待つ

バックグラウンドでAIが解析し、数秒後には整理されたMarkdownファイルがあなたの端末に保存されます。

## 💜 Made for Obsidian

SnapToMDは、Obsidian をはじめとするローカル完結型のツールと最高の相性を発揮するように設計されています。

* 📂 Vaultの指定フォルダへ直行（SAF連携）
  生成されたMarkdown（.md）と元画像（.jpg）を、端末内の指定したローカルフォルダへ直接自動保存します。ObsidianのVault内フォルダを指定しておけば、アプリを開いた瞬間にはもうノートが追加されています。
* 📝 カスタムテンプレート & 画像リンク自動埋め込み
  AIが出力するMarkdownのフォーマットを自由にカスタマイズ可能。テンプレート内に {{image}} プレースホルダーを入れておけば、`![[image.jpg]]` や `![](image.jpg)` といった形式で元画像へのリンクも自動で埋め込まれます。
* 📋 ワンタップでソースをコピー
  アプリ内の履歴画面では、生成されたMarkdown原文をプレビューでき、ワンタップで全文コピーも可能です。

## ✨ Key Features

* 🔄 画面を閉じても止まらないバックグラウンド処理
  Androidの WorkManager を採用。共有メニューから画像を渡したあとは、別のアプリを触っていても通知バーで進行状況を知らせながら確実に処理を完了させます。
* 🔑 安心の BYOK (Bring Your Own Key) モデル
  あなたの Google Gemini API Key を設定して利用。ワンタップでの接続テストも可能です。
* 🧪 APIキーなしで今すぐ試せる「モックモード」搭載
  「まずはどんな動きをするか触ってみたい」という方のために、APIキーの代わりに `MOCK_KEY` と入力するだけで動作確認ができるモックモードを用意しています（通信は行わず、サンプルのMarkdownが生成されます）。
* 🏷️ 画像内容に応じた自動カテゴリ分類 & カスタムテンプレート
  AIが画像の内容（本・家電・チラシ・Web記事など）を識別し、カテゴリごとに設定した最適なMarkdownテンプレートを自動選択します。
* 📦 カテゴリ設定のYAMLインポート / エクスポート
  こだわりのカテゴリ設定やプロンプトテンプレートはYAMLファイルとして簡単に共有・バックアップが可能です。
* 📴 完全ローカル保存の履歴管理
  生成されたノート履歴はすべて端末内のデータベースに保存されるため、オフライン環境でもいつでも見返し・検索・削除が可能です。

## 🚀 Getting Started

### 動作要件 (Requirements)

* **対応OS**: Android 7.0 (API Level 24) 以上
* **APIキー**: Google Gemini API キー（モックモード `MOCK_KEY` での動作確認時は不要）
* **ネットワーク**: インターネット接続（AI解析および Web 検索利用時）

### 1. アプリのインストール

Releases ページから最新のAPKファイルをダウンロードし、Android端末にインストールしてください。

### 2. 初期設定

アプリを開き、右上の 歯車アイコン（設定） をタップします。

* APIキーの設定：
   * すぐ試したい場合 ➔ 「API Key」欄に `MOCK_KEY` と入力し、右上の 保存アイコン をタップします（モックモードになります）。
   * 実際にAI解析を使う場合 ➔ Google AI Studio で無料のAPIキーを取得し、「API Key」欄に入力して右上の 保存アイコン をタップします。
* 保存先フォルダの指定：
   * Markdownファイルと画像の保存先フォルダをそれぞれ指定します（Obsidianをお使いの方はVault内のフォルダを選択してください）。
   * モックモードで試す場合も、保存先フォルダの指定は必須です。

あとは普段通りスマホを使いながら、気になった画像を「共有」からSnapToMDへ送るだけです！

## 🧑‍💻 For Developers

### 採用技術 (Tech Stack)

| 区分         | 採用技術                                                                     |
| ---------- | ------------------------------------------------------------------------ |
| 言語         | Kotlin                                                                   |
| UI         | Jetpack Compose (Material Design 3)                                      |
| アーキテクチャ    | MVVM / Single Activity Architecture                                      |
| バックグラウンド処理 | Android Jetpack WorkManager (CoroutineWorker)                            |
| ローカルデータベース | Room Database                                                            |
| 外部ストレージ保存  | Storage Access Framework (SAF) / DocumentFile                            |
| 設定管理       | SharedPreferences (ApiKeyManager)                                        |
| AI通信     | Gemini REST API (Google Search Grounding対応)                            |
| 設定シリアライズ | SnakeYAML Engine (YAML形式でのカテゴリ設定入出力)                         |

### ビルド・テスト環境 (Build & Test)

* **JDK**: Java 21

```bash
# ユニットテストの実行
./gradlew testDebugUnitTest

# Debug APK のビルド
./gradlew assembleDebug
```

## 🔒 Privacy & Disclaimer（プライバシーと免責事項）

* **データの送信先**: 共有された画像データおよび設定した API キーは、ユーザーの端末から Google Gemini API (`generativelanguage.googleapis.com`) へ直接送信されます。開発者側のサーバーへ画像やAPIキー、生成結果が送信・保存されることはありません。
* **APIキーの管理**: APIキーは端末内のアプリ専用領域に保存され、Androidの自動バックアップおよび端末間データ転送の対象から除外されています。
* **API利用条件**: Gemini API の利用に伴う料金、レート制限、および送信データの取り扱いは、ユーザーご自身の Google アカウントおよび Google の利用規約・プライバシーポリシーに準拠します。
* **免責事項**: 本アプリは現状有姿（AS IS）で提供されます。AIによる画像認識結果や Web 検索（Google Search Grounding）により生成された Markdown の内容・正確性について、開発者は一切の責任を負いません。

## 📄 License

[MIT License](LICENSE) © 2026 tatuya911
