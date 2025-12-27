# FamiEmuKt プロジェクトドキュメント (GEMINI.md)

## プロジェクト概要

FamiEmuKtはKotlinで実装されたNES（Nintendo Entertainment
System）エミュレータです。このプロジェクトは個人の学習目的で開発されており、NES互換のゲームROMを実行できます。Kotlin
Multiplatformの活用により、AndroidとDesktopの両プラットフォームで動作する統一されたコードベースを実現しています。

**主な目的:**

- NESエミュレーション技術の学習
- Kotlin Multiplatformの実践的な活用
- CPU・PPU・APUなどのハードウェアエミュレーション実装

**主な機能:**

- iNES形式のROMファイルのサポート
- 6502 CPUの正確なエミュレーション
- PPU（Picture Processing Unit）のエミュレーション
- 複数のカートリッジマッパーのサポート
- テストロム（Blargg）による正確性の検証

## 使用技術

### 核となる技術スタック

| 技術                    | バージョン    | 用途            |
|-----------------------|----------|---------------|
| Kotlin                | 2.3.0    | メイン言語         |
| Gradle                | 8.12.3   | ビルドツール        |
| Android Gradle Plugin | 8.12.3   | Androidビルド    |
| Java                  | JDK 11以上 | ランタイム         |
| Kotlin Multiplatform  | -        | クロスプラットフォーム対応 |

### UIフレームワーク

- **Android:** Jetpack Compose - 最新のAndroid UI宣言型フレームワーク
- **Desktop:** Compose for Desktop - デスクトップ向けJetpack Compose

### ビルドおよび開発ツール

- **Gradle Kotlin DSL** - Kotlin DSLによる型安全なビルド定義
- **Mokkery 3.1.1** - ユニットテスト用モッキングライブラリ
- **Shadow Gradle Plugin 8.1.1** - FAT JARビルド用
- **Versions Plugin** - 依存関係の更新チェック

### 依存ライブラリ

- **Kotlin Coroutines** - 非同期処理とマルチスレッド対応
- **Kotlin Standard Library** - Kotlin標準ライブラリ
- **Google Material Design Components** - AndroidのUIコンポーネント

## プロジェクト構造

このプロジェクトはKotlin Multiplatformプロジェクトとして構成されており、以下の3つの主要モジュールから構成されています。

### ディレクトリツリー

```
FamiEmuKt/
├── emulator/                          # ★ コアエミュレータモジュール
│   ├── src/
│   │   ├── commonMain/kotlin/         # プラットフォーム共通のKotlinコード
│   │   │   └── jp/mito/famiemukt/
│   │   │       └── emurator/          # エミュレータのメインパッケージ
│   │   │           ├── cpu/           # CPU実装
│   │   │           ├── ppu/           # PPU実装
│   │   │           ├── apu/           # APU実装
│   │   │           ├── cartridge/     # カートリッジ・マッパー実装
│   │   │           ├── memory/        # メモリ管理
│   │   │           └── util/          # ユーティリティ
│   │   ├── commonTest/kotlin/         # 共通ユニットテスト
│   │   └── commonTest/resources/      # テストリソース（テストROM等）
│   │       └── nes-test-roms/         # Blargg CPU/APUテストロム
│   └── build.gradle.kts
│
├── androidApp/                        # ★ Androidアプリケーション
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/                # Androidプラットフォーム固有コード
│   │   │   ├── res/                   # リソース（レイアウト、ドローアブル等）
│   │   │   └── AndroidManifest.xml    # アンドロイドマニフェスト
│   │   └── androidTest/               # Androidインストルメンテーションテスト
│   ├── build/                         # ビルド出力
│   └── build.gradle.kts
│
├── desktopApp/                        # ★ デスクトップアプリケーション
│   ├── src/
│   │   ├── main/
│   │   │   └── kotlin/                # Desktopプラットフォーム固有コード
│   │   └── test/
│   ├── build/                         # ビルド出力
│   └── build.gradle.kts
│
├── doc/                               # ドキュメント
│   └── クラス関連図.drawio.svg        # アーキテクチャ図
│
├── gradle/                            # Gradleラッパー関連
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── build.gradle.kts                   # ★ ルートプロジェクト設定
├── settings.gradle.kts                # ★ モジュール定義
├── gradle.properties                  # Gradleプロパティ
├── local.properties                   # ローカル設定（SDKパス等）
├── README.md                          # 一般向けドキュメント
├── GEMINI.md                          # ★ このファイル（開発ガイド）
├── LICENSE                            # ライセンス情報
└── .github/
    └── copilot-instructions.md        # GitHub Copilot用命令

```

### モジュールの役割

#### emulator モジュール

**責務:**

- NESハードウェアのエミュレーション
- 6502 CPU、PPU、APUの実装
- カートリッジ形式の解析とマッパー対応
- メモリバスの管理
- 割り込み・DMA処理

**主要パッケージ:**

| パッケージ       | 説明                            |
|-------------|-------------------------------|
| `cpu`       | 6502 CPU エミュレーション、命令実行、レジスタ管理 |
| `ppu`       | Picture Processing Unit（画面出力） |
| `apu`       | Audio Processing Unit（音声処理）   |
| `cartridge` | iNES形式解析、マッパー実装（Mapper0-19など） |
| `memory`    | RAM、VRAM、カートリッジメモリの管理         |
| `util`      | ビット操作、データ変換ユーティリティ            |

**プラットフォーム対応:**

- `commonMain`: 全プラットフォーム共有コード
- `commonTest`: ユニットテスト、テストロム

#### androidApp モジュール

**責務:**

- AndroidOS上でのUI実装
- Jetpack Composeを使用したUIレイアウト
- ROMファイルの選択・読み込み
- タッチ入力の処理
- Androidシステムリソースの統合

**特徴:**

- Kotlin Compose による最新のUI
- エミュレータモジュールの再利用
- AndroidManifest.xmlによる権限・アクティビティ定義

#### desktopApp モジュール

**責務:**

- Windows/Mac/Linux上でのUI実装
- Compose for Desktopを使用したUIレイアウト
- キーボード入力の処理
- ファイルダイアログ
- デスクトップシステムリソースの統合

**特徴:**

- クロスプラットフォーム対応（Windows/Mac/Linux）
- Compose for Desktopによる統一UI
- ネイティブ実行形式への変換対応

## エミュレータ実装の詳細

### サポートされるマッパー

| マッパー                  | 説明                    |
|-----------------------|-----------------------|
| Mapper 0 (NROM)       | 基本的な非切り替え型カートリッジ      |
| Mapper 1 (MMC1)       | ゼルダの伝説など、一般的なマッパー     |
| Mapper 2 (UxROM)      | メガマン、キャスルヴァニアシリーズ     |
| Mapper 3 (CNROM)      | 複数CHRバンク対応            |
| Mapper 4 (MMC3)       | スーパーマリオブラザーズ3、など高度な機能 |
| Mapper 19 (Namco 163) | 拡張音声処理                |
| その他複数                 | 段階的に対応予定              |

### テスト検証

プロジェクトにはBlarggのテストロムが含まれており、以下の検証を実施できます：

```
nes-test-roms/
├── cpu_exec_space/     # CPU実行空間テスト
├── cpu_dummy_reads/    # CPU ダミー読み込みテスト
├── cpu_dummy_writes/   # CPU ダミー書き込みテスト
├── cpu_interrupts_v2/  # 割り込み処理テスト
├── apu_mixer/          # APU ミキサーテスト
├── instr_test-v3/      # 命令実装テスト v3
├── instr_test-v5/      # 命令実装テスト v5
└── ... その他複数
```

## テスト実行結果

### jvmTest実行結果

**実行日時:** 2026-02-17

**テスト実行コマンド:**
```bash
./gradlew :emulator:jvmTest
```

**総合結果:**

| 項目         | 数値    |
|-----------|-------|
| **総テスト数**   | 169   |
| **成功**      | 153   |
| **失敗**      | 16    |
| **スキップ**    | 0     |
| **成功率**     | 90%   |
| **実行時間**    | 32.7秒 |

### 失敗しているテスト（16個）

#### APU関連のテスト（4個）

- `testExecSpaceApu` - **NotImplementedError**
  - 原因: APUのEXEC_SPACE対応が未実装
  - 状態: 要実装

- `testAPUReset_2` - AssertionError
  - APUリセット処理の不正

- `testAPUReset_3` - AssertionError
  - APUリセット処理の不正

- `testAPUTest_8` - AssertionError
  - APUの一般的なテスト失敗

#### PPU関連のテスト（8個）

- `testPPUOpenBus` - AssertionError
  - PPUのオープンバス動作の不正

- `testExecSpacePpuIo` - AssertionError
  - PPU I/Oアドレス空間の不正

- `testPPUReadBuffer` - AssertionError (Line 317)
  - PPU読み取りバッファの不正な実装

- `testPPUVBLNMI_10` - AssertionError
  - PPUの垂直ブランク割り込み（NMI）タイミング問題

- `testSpriteOverflowTests_3` - AssertionError (Line 431)
  - スプライトオーバーフロー検出の不正

- `testSpriteOverflowTests_4` - AssertionError (Line 431)
  - スプライトオーバーフロー検出の不正

- `testOAMStress` - AssertionError
  - OAM（Object Attribute Memory）ストレステスト失敗

- `testFor10EvenOddTiming_3` - AssertionError (PPUTest.kt:659)
  - PPUの偶数/奇数タイミング処理の問題

- `testFor10EvenOddTiming_5` - AssertionError (PPUTest.kt:676)
  - PPUの偶数/奇数タイミング処理の問題

#### CPU命令関連のテスト（2個）

- `testInstrMisc_3` - AssertionError
  - ダミー読み取り命令の不正 (03-dummy_reads.nes)

- `testInstrMisc_4` - AssertionError
  - ダミー読み取りAPU命令の不正 (04-dummy_reads_apu.nes)

#### マッパー関連のテスト（2個）

- `testMM3Test_6` - AssertionError
  - Mapper 4 (MMC3) テストの不正

### 失敗原因の分類

| カテゴリ        | 失敗数 | 主な原因                           |
|------------|-----|--------------------------------|
| **PPU関連**   | 8   | タイミング、オープンバス、バッファ実装などの精密実装 |
| **APU関連**   | 4   | APU実装の不完全性、リセット処理            |
| **CPU命令**   | 2   | ダミー読み取り、I/O副作用の正確性          |
| **マッパー**    | 2   | Mapper 4 実装の問題                |

### 成功しているテスト（153個）

以下のテストは正常に動作しています：

#### CPU命令テスト（100%成功）

- Implied addressing
- Immediate addressing
- Zero page addressing
- Zero page X/Y addressing
- Absolute addressing
- Absolute X/Y addressing
- Indirect X addressing
- Indirect Y addressing
- Official-only instruction set
- BRK命令
- 特殊命令
- 命令タイミング
- 分岐タイミング
- 絶対X折り返し
- 分岐折り返し

#### その他のテスト（成功）

- CPU割り込みテスト
- PPUクリッピング関連
- パレット関連
- 背景描画関連（複数テスト）
- CPU実行空間テスト
- CPU ダミー書き込みテスト

### 次のアクション

1. **高優先度:** PPUタイミング精密実装の修正
   - オープンバス動作
   - 読み取りバッファの正確な実装
   - VBLNMIタイミング

2. **中優先度:** APUの完全実装
   - ExecSpace対応
   - リセット処理の修正
   - サンプリング精度

3. **低優先度:** ダミー読み取り命令の精密化

## ビルドおよび実行手順

### 前提条件

| 要件                   | 最小バージョン      | 推奨バージョン        |
|----------------------|--------------|----------------|
| Java Development Kit | JDK 11       | JDK 17 LTS以上   |
| Android SDK          | API Level 24 | API Level 34+  |
| Gradle               | -            | 8.1以上（ラッパーに同梱） |
| Git                  | -            | 最新版            |

**環境変数設定:**

```bash
# JAVA_HOME（JDKインストールパス）
JAVA_HOME=/path/to/jdk

# ANDROID_HOME（Android SDKルートパス）
ANDROID_HOME=/path/to/android/sdk
```

### プロジェクトのビルド

**全モジュール・全タスク:**

```bash
./gradlew build
```

**リリースビルド:**

```bash
./gradlew build -x test
```

**キャッシュをクリアしてビルド:**

```bash
./gradlew clean build
```

### 特定のアプリケーションの実行

#### Androidアプリケーション

**デバイスへのインストール:**

```bash
./gradlew :androidApp:installDebug
```

**実行：**

```bash
./gradlew :androidApp:runDebug
```

#### デスクトップアプリケーション

**実行：**

```bash
./gradlew :desktopApp:run
```

**JAR生成:**

```bash
./gradlew :desktopApp:packageUberJarForCurrentOS
```

生成されたJARは以下の場所にあります：

```
desktopApp/build/libs/desktopApp-all.jar
```

実行：

```bash
java -jar desktopApp/build/libs/desktopApp-all.jar
```

## テスト

### テスト戦略

このプロジェクトでは以下のテスト階層を採用しています：

1. **ユニットテスト** - 個別コンポーネントの動作確認
2. **統合テスト** - エミュレータ全体の動作確認
3. **テストロム検証** - Blargg CPU/APUテストロムによる正確性検証

### テスト実行

**すべてのテスト:**

```bash
./gradlew check
```

**特定モジュール:**

```bash
./gradlew :emulator:test
./gradlew :androidApp:test
./gradlew :desktopApp:test
```

**特定テストクラス:**

```bash
./gradlew :emulator:test --tests "jp.mito.famiemukt.emurator.cpu*"
```

**テストロムのみ:**

```bash
./gradlew :emulator:test --tests "*TestRom*"
```

**テスト結果レポート:**

```
emulator/build/reports/tests/test/index.html
```

## 開発ガイドライン

### コーディング規約

- **言語:** Kotlin 2.3.0以上
- **スタイル:** Google Kotlin Style Guide に準拠
- **命名規則:** PascalCase（クラス）、camelCase（変数・関数）
- **ドキュメント:** KDocコメント必須

### パッケージ構成

```
jp.mito.famiemukt.emurator
├── cartridge/      # カートリッジ形式・マッパー
├── cpu/            # 6502 CPU実装
├── ppu/            # PPU実装
├── apu/            # APU実装
├── memory/         # メモリバス
└── util/           # ユーティリティ
```

### Git ワークフロー

```
main (リリース)
  └─ develop (統合開発)
      ├─ feature/cpu-改善
      ├─ feature/mapper-追加
      └─ bugfix/メモリリーク
```

### Pull Request プロセス

1. 機能ブランチで開発（`feature/機能名`）
2. ローカルで全テスト実行（`./gradlew check`）
3. Pull Requestを作成
4. CI（自動テスト）がパスするか確認
5. コードレビュー後にマージ

### 新機能追加時のチェックリスト

- [ ] ユニットテストを作成
- [ ] ドキュメント（KDoc）を記載
- [ ] 既存テストが全てパスしている
- [ ] テストカバレッジが低下していない
- [ ] Kotlin警告がない

## 既知の制限事項

### 未実装機能

1. **マッパー:** Mapper 5, 7, 9, 10などの実装は未完了
2. **拡張オーディオ:** 一部のチップ（Namco 163除く）の音声合成未実装
3. **セーブ機能:** SRAM永続化機能が限定的
4. **Netplay:** ネットワークマルチプレイ未実装

### パフォーマンス

- 一部の高速動作ゲームで速度低下の可能性
- 複数マッパー同時切り替え時のメモリ効率改善検討中

## 依存関係の管理

### 依存関係の確認

```bash
./gradlew dependencies
```

### 依存関係の更新確認

```bash
./gradlew dependencyUpdates
```

### 依存関係の更新

```bash
# 特定ライブラリの更新
./gradlew dependencyUpdates -Drevision=release
```

## トラブルシューティング

### よくある問題と解決方法

| 問題             | 原因          | 解決方法                                                 |
|----------------|-------------|------------------------------------------------------|
| Gradleビルド失敗    | JDKバージョン不正  | `java -version` で確認、JDK 11以上に更新                      |
| Android SDKエラー | SDK未インストール  | `local.properties` に `sdk.dir` を設定                   |
| テスト失敗          | Koin DI設定不正 | `./gradlew clean test` を実行                           |
| メモリ不足          | ヒープサイズ不足    | `gradle.properties` で `org.gradle.jvmargs=-Xmx4g` 設定 |

## Gemini CLI の詳細

このセクションでは、Gemini CLIをこのプロジェクトで効果的に使用する方法について説明します。

### Gemini CLI が支援できるタスク

- **コード生成:** 新しいマッパー実装、テストケースのボイラープレートコード生成
- **バグ修正:** エミュレーション不具合の診断と修正
- **リファクタリング:** メモリ効率化、パフォーマンス最適化
- **コード説明:** 複雑なCPU・PPU実装の説明
- **ドキュメント作成:** APIドキュメント、使用例の生成
- **テスト自動化:** テストケース設計、テストロム結果分析
- **依存関係管理:** ライブラリのアップグレード対応

### プロジェクト固有の設定

**言語:** 日本語  
**回答形式:** マークダウン、コードブロック併用  
**ドメイン知識:** NES仕様、6502 CPU、Kotlin/Kotlin Multiplatform

### 効果的な使用例

```
"エミュレータモジュールのMapperクラスを説明してください"
"新しいMapperを追加するための手順を教えてください"
"CPU実装のテストロム失敗を修正してください"
"APUのメモリリークを改善できますか?"
```

### プロジェクト固有の Gemini CLI 設定/注記

- **ユーザーへの回答:** 日本語で提供
- **質問内容:** 日本語対応
- **テスト駆動開発:** テストファースト開発を推奨
- **ドキュメント優先:** コード変更時はドキュメント更新を重視
- **パフォーマンス重視:** エミュレーション精度とパフォーマンスのバランスを考慮

## 参考資料

### NES技術仕様

- [NESdev Wiki](https://www.nesdev.org/wiki/Nesdev_Wiki) - NES開発の総合リソース
- [6502命令セット](https://www.nesdev.org/wiki/CPU) - CPU命令詳細
- [PPU仕様](https://www.nesdev.org/wiki/PPU) - 画面処理詳細
- [APU仕様](https://www.nesdev.org/wiki/APU) - 音声処理詳細
- [iNES形式](https://www.nesdev.org/wiki/INES) - ROMファイル形式

### Kotlin・Android開発

- [Kotlin公式ドキュメント](https://kotlinlang.org/docs/)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Compose for Desktop](https://www.jetbrains.com/help/compose/desktop-overview.html)

### Gradle・ビルドツール

- [Gradle公式ドキュメント](https://docs.gradle.org/)
- [Kotlin DSL Guide](https://docs.gradle.org/current/userguide/kotlin_dsl.html)

---

**最終更新:** 2026-02-17  
**ドキュメント版:** 1.1  
**対応Kotlinバージョン:** 2.3.0以上  
**テスト実行状況:** jvmTest 169テスト中153成功（90%成功率）

