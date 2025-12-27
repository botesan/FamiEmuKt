# FamiEmuKt - Kotlin製NES（ファミコン）エミュレータ

自分の勉強用に作成したKotlin製ファミコンエミュレータです。
Kotlin Multiplatformの勉強も兼ねています。

AndroidとDesktopなどで実行できることを一応目標としていますが、 Androidでの動作はパフォーマンス的に非現実的な状態です。

各ドキュメント（本ドキュメント含む）は、GeminiやGitHub CopilotなどのAIツールを活用して作成していますが、
精査が追いついて無く、誤りがある可能性があります。

## 📋 プロジェクト概要

FamiEmuKtは、Nintendo Entertainment System (NES)の機能を完全にエミュレートするKotlinプロジェクトです。
学習目的で開発されており、NES互換のゲームROMを実行できます。

## ✨ 主な機能

- **マルチプラットフォーム対応**: Kotlin Multiplatformにより、Android と Desktop 両プラットフォームで動作
- **NES互換性**: iNES形式のROMファイルをサポート
- **CPU エミュレーション**: MOS Technology 6502互換CPUのフル実装
- **PPU エミュレーション**: PixelProcessingUnitの正確なエミュレーション
- **複数マッパー対応**: NROM、UNROM、MMC1、MMC3、Namco 163など多数のカートリッジマッパーに対応
- **テストロムサポート**: Blarggのテストロムを通じた正確性の検証
- **APU サポート**: オーディオプロセッシングユニットの実装

## 🛠 使用技術

| 技術                    | バージョン    |
|-----------------------|----------|
| Kotlin                | 2.3.0    |
| Gradle                | 8.12.3   |
| Android Gradle Plugin | 8.12.3   |
| Java                  | JDK 11以上 |
| Kotlin Multiplatform  | -        |

### UIフレームワーク

- **Android**: Jetpack Compose
- **Desktop**: Compose for Desktop

### その他の依存関係

- Kotlin Coroutines
- Mokkery (テスト用モッキングライブラリ)
- その他のKotlin標準ライブラリ

## 📁 プロジェクト構成

```
FamiEmuKt/
├── emulator/                  # コアエミュレータロジック（共有モジュール）
│   ├── src/
│   │   ├── commonMain/        # プラットフォーム共通コード
│   │   └── commonTest/        # ユニットテスト
│   └── build.gradle.kts
├── androidApp/                # Androidアプリケーション
│   ├── src/
│   │   └── main/
│   └── build.gradle.kts
├── desktopApp/                # デスクトップアプリケーション
│   ├── src/
│   │   └── main/
│   └── build.gradle.kts
├── build.gradle.kts           # プロジェクト全体の設定
├── settings.gradle.kts        # モジュール定義
└── gradle.properties          # Gradle設定
```

## 🚀 セットアップ方法

### 前提条件

- **Java Development Kit (JDK) 11 以上**
    - OpenJDK または Oracle JDK
- **Android Development Kit (Android SDK)**
    - Android API Level 24以上推奨
    - Android NDK（ネイティブ機能使用時）
- **Git**

### セットアップ手順

1. **リポジトリをクローン**

```bash
git clone <repository-url>
cd FamiEmuKt
```

2. **Gradleラッパーの確認**

```bash
./gradlew --version
```

3. **依存関係をダウンロード**

```bash
./gradlew build
```

## 🏗️ ビルド方法

### プロジェクト全体のビルド

```bash
./gradlew build
```

### 特定のモジュールのビルド

**Androidアプリ**:

```bash
./gradlew :androidApp:build
```

**デスクトップアプリ**:

```bash
./gradlew :desktopApp:build
```

**コアエミュレータ**:

```bash
./gradlew :emulator:build
```

## ▶️ 実行方法

### Androidアプリの実行

接続されたデバイスまたはエミュレータ上で実行：

```bash
# デバッグビルドをインストール
./gradlew :androidApp:installDebug

# または、デバイスから手動で起動
./gradlew :androidApp:runDebug
```

### デスクトップアプリの実行

```bash
./gradlew :desktopApp:run
```

実行可能なJARまたはネイティブイメージは以下に生成されます：

- `desktopApp/build/install/desktopApp/`

## 🧪 テスト実行

### 全テスト実行

```bash
./gradlew check
```

### 特定モジュールのテスト実行

```bash
# エミュレータモジュールのテスト
./gradlew :emulator:test

# Androidアプリテスト
./gradlew :androidApp:test

# デスクトップアプリテスト
./gradlew :desktopApp:test
```

### テストロムの実行

プロジェクトにはBlarggのNES CPUテストロムが含まれています：

```bash
./gradlew :emulator:test --tests "*cpu*"
```

## 📝 利用可能なコマンド

| コマンド                                 | 説明                |
|--------------------------------------|-------------------|
| `./gradlew build`                    | 全モジュールをビルド        |
| `./gradlew clean`                    | ビルド成果物を削除         |
| `./gradlew check`                    | 全テストを実行           |
| `./gradlew dependencyUpdates`        | 依存関係の更新を確認        |
| `./gradlew :androidApp:installDebug` | Androidアプリをインストール |
| `./gradlew :desktopApp:run`          | デスクトップアプリを実行      |

## 🔧 IDE サポート

### IntelliJ IDEA

1. IDE を開く
2. **File** → **Open** を選択
3. `FamiEmuKt` フォルダを選択
4. IDEが自動的にGradleプロジェクトを認識します

### Android Studio

1. Android Studio を開く
2. **File** → **Open** を選択
3. `FamiEmuKt` フォルダを選択
4. プロジェクトが自動的に読み込まれます

## 🐛 トラブルシューティング

### ビルドエラー

**JDKバージョン確認**:

```bash
java -version
```

**Gradleキャッシュをクリア**:

```bash
./gradlew clean
```

### Androidビルド失敗

**Android SDKパスの設定**:

- `local.properties` に以下を追加：

```properties
sdk.dir=/path/to/android/sdk
```

## 📚 参考資料

### プロジェクトドキュメント

- **[01_PERFORMANCE_IMPROVEMENTS_SUMMARY.md](doc/performance/01_PERFORMANCE_IMPROVEMENTS_SUMMARY.md)** - パフォーマンス改善の統合レポート（課題 12 件、期待改善 23-45%）
- **[02_SOURCE_CODE_PERFORMANCE_ANALYSIS.md](doc/performance/02_SOURCE_CODE_PERFORMANCE_ANALYSIS.md)** - ソースコード詳細分析（新規課題の技術詳述）
- **[05_KMP_INLINE_ANALYSIS.md](doc/performance/05_KMP_INLINE_ANALYSIS.md)** - Kotlin Multiplatform における inline キーワードの効果分析
- **[GEMINI.md](GEMINI.md)** - 技術説明書（CPU, PPU, APU 実装詳細）

### 外部参考資料

- [NESdev Wiki - NES技術仕様](https://www.nesdev.org/wiki/Nesdev_Wiki)
- [Kotlin公式ドキュメント](https://kotlinlang.org/docs/)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Compose for Desktop](https://www.jetbrains.com/help/compose/desktop-overview.html)

## 📄 ライセンス

詳細は [LICENSE](LICENSE) ファイルを参照してください。

## 👤 作成者

個人学習プロジェクト

---

**注記**: このプロジェクトは教育目的で作成されています。商用利用やゲーム配布は著作権法に従ってください。
