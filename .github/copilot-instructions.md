# GitHub Copilot 用 プロジェクト指示書

## プロジェクト概要

**FamiEmuKt** は Kotlin で実装された NES（Nintendo Entertainment System）エミュレータです。Kotlin Multiplatform
を活用し、Android と Desktop の両プラットフォームで動作します。

## 重要な指針

### 言語・コミュニケーション

- **すべてのやり取りは日本語で実施してください**
- コード内のコメント、ドキュメント、コミットメッセージは日本語を使用
- ユーザーへの提案・質問は常に日本語で

### アーキテクチャ・設計原則

#### Kotlin Multiplatform の活用

```
┌──────────────────────────────────────────────┐
│         emulator (共有コード)                  │
│  - CPU/PPU/APUエミュレーション                │
│  - カートリッジマッパー                       │
│  - メモリ管理                                 │
└──────────────────────────────────────────────┘
         ↙                                   ↘
┌──────────────────┐          ┌──────────────────┐
│   androidApp     │          │   desktopApp     │
│  Jetpack Compose │          │ Compose Desktop  │
└──────────────────┘          └──────────────────┘
```

- **commonMain**: プラットフォーム共通コード（エミュレータロジック）
- **platformSpecific**: プラットフォーム固有のUI・入出力実装
- **共有化**: ビジネスロジック（CPU、PPU）は絶対に共有化

#### モジュール構成

```
emulator/
├── cartridge/         # iNES形式、マッパー（Mapper 0-19）
├── cpu/              # 6502 CPU エミュレーション
├── ppu/              # Picture Processing Unit
├── apu/              # Audio Processing Unit
├── memory/           # メモリバス、RAM/VRAM管理
└── util/             # ビット操作、ユーティリティ
```

### コーディング規約

#### Kotlin スタイル

```kotlin
// ✅ 推奨パターン

// 1. クラス・パッケージ名
package jp.mito.famiemukt.emurator.cpu
class Cpu6502 {
    // ...
}

// 2. プロパティはprivateを基本
private val registers = UByteArray(8)
val state: State
get() = State(registers.toList())

// 3. KDocコメント必須（public API）
/**
 * 6502 CPUのエミュレーション実行
 * @param cycles 実行するサイクル数
 * @return 実行したサイクル数
 */
fun execute(cycles: Int): Int {}

// 4. sealed class でステート管理
sealed class MapperState {
    data class Nrom(val prgRom: ByteArray) : MapperState()
    data class Mmc1(val banks: IntArray, val mode: Int) : MapperState()
}

// 5. Scope functions の活用（also, apply）
buffer.apply {
    put(header)
    putInt(size)
    flip()
}
```

```kotlin
// ❌ 避けるべき

// - グローバル変数の多用
// - 深くネストされた関数
// - 大きなクラス（単一責任の違反）
// - public なミュータブル状態
```

#### パフォーマンス重視

NES エミュレーションは**毎フレーム大量の処理**が発生：

```kotlin
// ✅ 推奨：アロケーション最小化
class Cpu6502 {
    // クラス初期化時の確保
    private val registers = UByteArray(8)
    private val memory = UByteArray(0x10000)

    // 実行時は既存配列を再利用
    fun execute(cycles: Int) {
        for (i in 0 until cycles) {
            val opcode = memory[pc.toInt()].toInt()
            // 処理...
        }
    }
}

// ❌ 避ける：毎フレーム新規作成
fun execute() {
    val state = mutableMapOf<String, Int>()  // メモリリスク
    val newArray = UByteArray(256)            // 毎フレーム確保
}
```

### テスト駆動開発（TDD）

#### テスト構造

```
emulator/src/commonTest/
├── kotlin/
│   └── jp/mito/famiemukt/emurator/
│       ├── cpu/
│       │   └── Cpu6502Test.kt
│       ├── ppu/
│       │   └── PpuTest.kt
│       └── cartridge/
│           ├── CartridgeTest.kt
│           └── mappers/
│               ├── Mapper000Test.kt
│               └── Mapper001Test.kt
└── resources/
    └── nes-test-roms/
        ├── cpu_exec_space/
        └── instr_test-v5/
```

#### テスト作成時のガイドライン

```kotlin
class Cpu6502Test {
    private lateinit var cpu: Cpu6502

    @Before
    fun setUp() {
        cpu = Cpu6502()
    }

    @Test
    fun testLdaImmediate() {
        // Arrange
        cpu.memory[0x0000] = 0xA9  // LDA immediate
        cpu.memory[0x0001] = 0x42

        // Act
        cpu.execute(2)

        // Assert
        assertEquals(0x42.toByte(), cpu.a)
        assertTrue(cpu.flags.z == false)
        assertTrue(cpu.flags.n == false)
    }
}
```

#### Blargg テストロムの活用

```kotlin
@Test
fun testCpuExecSpace() {
    val rom = loadTestRom("nes-test-roms/cpu_exec_space/01-implied.nes")
    val emulator = Emulator()

    // テストロムを実行
    repeat(1000000) {
        emulator.step()
    }

    // テストロム内の判定フラグを確認
    val result = emulator.readMemory(0x0300)
    assertEquals(0x00, result)  // 0x00 = success
}
```

### NES技術知識の保持

#### CPU（6502）特性

- **サイクル単位の正確性**: 命令実行はサイクル単位で管理
- **ダミー読み取り/書き込み**: 実装時に無視できない副作用
- **割り込み**: NMI（垂直ブランク）、IRQ（カートリッジ割り込み）を正確に実装

```kotlin
// NMI（垂直ブランク割り込み）の実装例
fun handleNmi() {
    // スタックにPCを保存（2サイクル）
    pushWord(pc)  // 3サイクル
    pushByte(flags)  // 3サイクル

    // NMIベクトルにジャンプ
    pc = readWord(NMI_VECTOR)  // 4サイクル
    // 合計：8サイクル
}
```

#### PPU（画像処理）特性

- **スキャンライン単位の処理**: PPUはスキャンライン単位で動作
- **タイミング依存**: ゲームのグラフィックはPPUタイミングに依存
- **バンク切り替え**: キャラクターROMの動的な切り替え

```kotlin
// PPU タイミング管理
fun renderScanline(scanline: Int) {
    // スキャンライン 0-239: 表示領域
    // スキャンライン 240: ポストレンダー
    // スキャンライン 241-260: VBlank（NMI発火）
    // スキャンライン 261: 前レンダー

    when (scanline) {
        in 0..239 -> renderVisibleLine(scanline)
        241 -> triggerNmi()
        261 -> preparePpuState()
    }
}
```

#### マッパー（カートリッジ）実装

新しいマッパーを追加する際のチェックリスト：

```kotlin
sealed class Mapper {
    abstract fun readCpu(address: Int): UByte
    abstract fun writeCpu(address: Int, value: UByte)
    abstract fun readPpu(address: Int): UByte
    abstract fun writePpu(address: Int, value: UByte)
    abstract fun getMirroringMode(): MirroringMode
}

// 実装例
class Mapper001(prgRom: ByteArray, chrRom: ByteArray) : Mapper() {
    private var shiftRegister = 0
    private var bankMode = 3  // 32KB バンク

    override fun writeCpu(address: Int, value: UByte) {
        when {
            address < 0x8000 -> {
                // 制御レジスタ
                shiftRegister = (shiftRegister shr 1) or ((value.toInt() and 1) shl 4)
                if ((shiftRegister and 0x01) != 0) {
                    // レジスタ更新
                    updateBanks()
                    shiftRegister = 0
                }
            }
        }
    }
}
```

### コミット・プルリクエスト

#### コミットメッセージ

```
タイプ(スコープ): 説明

[本文（オプション）]

[フッター（オプション）]

例：
fix(cpu): LDA命令の負フラグ設定を修正

負フラグがイミディエイト値に対して正しく設定されていなかった。
テストロム cpu_exec_space で確認。

Fixes: #42
```

**タイプ:**

- `feat`: 新機能
- `fix`: バグ修正
- `refactor`: リファクタリング
- `test`: テスト追加・修正
- `docs`: ドキュメント
- `perf`: パフォーマンス改善
- `chore`: その他

#### Pull Request テンプレート

```markdown
## 説明

このPRの目的を簡潔に説明してください。

## 関連 Issue

Fixes #123

## 変更内容

- [ ] CPU命令X を実装
- [ ] テストロムY をパス
- [ ] ドキュメント更新

## テスト結果

```

./gradlew check

# テスト結果のコピー

```

## パフォーマンス影響

[ ] パフォーマンス低下あり
[x] パフォーマンス向上 / 影響なし
```

### よくある間違いと正しい対応

#### ❌ 間違い 1: `public` な mutable state

```kotlin
// ❌ 危険
class Memory {
    val data = UByteArray(0x10000)
}

val memory = Memory()
memory.data[0x0000] = 0xFF  // 外部から直接操作可能
```

#### ✅ 正しい方法

```kotlin
class Memory {
    private val data = UByteArray(0x10000)

    fun read(address: Int): UByte = data[address]
    fun write(address: Int, value: UByte) {
        data[address] = value
        // 必要に応じて副作用を処理
    }
}
```

#### ❌ 間違い 2: 毎フレーム大量アロケーション

```kotlin
// ❌ パフォーマンス悪化
class Renderer {
    fun renderFrame() {
        val pixels = IntArray(320 * 240)  // 毎フレーム確保！
        val tiles = Array(256) { ByteArray(256) }  // 毎フレーム確保！
    }
}
```

#### ✅ 正しい方法

```kotlin
// ✅ 効率的
class Renderer {
    private val pixels = IntArray(320 * 240)  // 初期化時のみ
    private val tiles = Array(256) { ByteArray(256) }

    fun renderFrame() {
        pixels.fill(0)  // リセット
        drawTiles()  // 既存配列を再利用
    }
}
```

#### ❌ 間違い 3: アドレス空間の混同

```kotlin
// ❌ バグのもと
// CPU アドレス空間 $0000-$FFFF
// PPU アドレス空間 $0000-$3FFF
val cpuMemory = UByteArray(0x10000)  // OK
val ppuMemory = UByteArray(0x10000)  // ❌ 0x4000 で充分

class Memory {
    // アドレス空間を明確にすること
    fun readCpu(address: Int) {}
    fun writePpu(address: Int) {}  // PPU用の別メソッド
}
```

## IDE セットアップ

### IntelliJ IDEA / Android Studio

#### 推奨プラグイン

- Kotlin
- Gradle (デフォルト同梱)
- Git (デフォルト同梱)
- Mokkery (テスト用)

#### Code Style 設定

```
File → Settings → Editor → Code Style → Kotlin
- Line length: 120
- Indentation: 4 spaces
- Spaces in imports: true
```

#### Run Configuration

**デスクトップアプリ実行:**

```
Run → Edit Configurations
+ Gradle
- Task: :desktopApp:run
- Gradle project: FamiEmuKt
- Run as: Gradle Run
```

**テスト実行:**

```
Run → Edit Configurations
+ Gradle
- Task: :emulator:test
- Gradle project: FamiEmuKt
```

## パフォーマンス・プロファイリング

### メモリプロファイル

```kotlin
// メモリリーク検出
@Test
fun testMemoryLeak() {
    repeat(1000) {
        val emulator = Emulator()
        emulator.loadRom(testRomBytes)
        // ~60000 サイクル実行
    }
    // メモリが増加し続けていないか確認
}
```

### CPU プロファイル

```bash
# JVM フラグでプロファイリング
./gradlew :desktopApp:run -Dorg.gradle.jvmargs=-agentpath:...
```

## ドキュメント要件

### 新機能追加時

1. **KDoc コメント**を API に追加
2. **README.md** に機能説明を追加
3. **GEMINI.md** に技術詳細を追加
4. テストロム検証結果を記録

### 例：新しい CPU 命令の追加

```kotlin
/**
 * LDA (Load Accumulator) 命令を実行
 * 
 * イミディエイト アドレッシングモードで、値をアキュムレータに読み込む。
 * 負フラグとゼロフラグが更新される。
 * 
 * サイクル: 2
 * フラグ: N, Z が影響
 * 
 * @param value アキュムレータに読み込む値
 * 
 * @see https://www.nesdev.org/wiki/CPU
 */
fun ldaImmediate(value: UByte) {
    a = value
    updateZeroFlag(a)
    updateNegativeFlag(a)
}
```

## 質問・困ったときは

以下の情報をまとめてご質問ください：

1. **発生している問題**
    - テストの失敗メッセージ
    - エラースタックトレース

2. **期待する動作**
    - NES仕様における期待値
    - テストロムの検証結果

3. **環境情報**
    - Kotlin バージョン: 2.3.0
    - Java バージョン: JDK 11 以上
    - OS: Windows / macOS / Linux

---

**ドキュメント更新:** 2026-02-13  
**対応バージョン:** FamiEmuKt 1.0+  
**言語:** Kotlin 2.3.0+

