package jp.mito.famiemukt.emurator.pad

/*
https://www.nesdev.org/wiki/Input_devices

http://hp.vector.co.jp/authors/VA042397/nes/joypad.html
ジョイパッドには次のような手順でアクセスを行います。
    I/O【$4016】のbit0をセットする。
    I/O【$4016】のbit0をクリアする。
    入力をチェックしたいボタンになるまで1P側はI/O【$4016】、2P側はI/O【$4017】を連続で読み込む。
    読み込んだ値のbit0が0なら入力無し、1なら入力ありと言う事になる。
    $4016 (ジョイパッド1レジスタ)
        1P側のジョイパッドの状態を取得、または拡張パッドの設定を行います。
        読み込み
            位置	内容	クリア時	セット時
            7-5	未使用
            4	Zapperスプライト	スプライト未検出	スプライト検出
            3	Zapperトリガ	入力なし	入力あり
            2-1	拡張ポートから読み込んだデータ	データ値
            0	ボタン入力情報	入力なし	入力あり
        書き込み
            位置	内容	クリア時	セット時
            7-3	未使用
            2-1	拡張ポートへ書き込むデータ	データ値
            0	入力情報のセット	クリア	リセット
    $4017 (ジョイパッド2レジスタ)
        2P側のジョイパッドの状態を取得、または拡張パッドの設定を行います。
        読み込み
            位置	内容	クリア時	セット時
            7-5	未使用
            4	Zapperスプライト	スプライト未検出	スプライト検出
            3	Zapperトリガ	入力なし	入力あり
            2-1	拡張ポートから読み込んだデータ	データ値
            0	ボタン入力情報	入力なし	入力あり
        書き込み
            位置	内容	クリア時	セット時
            7-3	未使用
            2-1	拡張ポートへ書き込むデータ	データ値
            0	入力情報のセット	クリア	リセット
*/
class Pad {
    private var value: UByte = 0U
    private var isInputPad: Boolean = false
    private var inputPad1Count: Int = 0

    var isPad1Up: Boolean = false
    var isPad1Down: Boolean = false
    var isPad1Left: Boolean = false
    var isPad1Right: Boolean = false
    var isPad1Select: Boolean = false
    var isPad1Start: Boolean = false
    var isPad1A: Boolean = false
    var isPad1B: Boolean = false

    /* 読み込み回数とボタン関係 : A / B / セレクトボタン / スタートボタン / 上 / 下 / 左 / 右 */
    fun read(address: Int): UByte {
        when (address) {
            0x4016 -> {
                if (isInputPad) {
                    when (inputPad1Count++) {
                        0 -> return if (isPad1A) 1U else 0U
                        1 -> return if (isPad1B) 1U else 0U
                        2 -> return if (isPad1Select) 1U else 0U
                        3 -> return if (isPad1Start) 1U else 0U
                        4 -> return if (isPad1Up) 1U else 0U
                        5 -> return if (isPad1Down) 1U else 0U
                        6 -> return if (isPad1Left) 1U else 0U
                        7 -> return if (isPad1Right) 1U else 0U
                    }
                }
            }

            0x4017 -> Unit // TODO: パッド２
            else -> error("Illegal read address. $address(${address.toString(radix = 16)}")
        }
        return 0U
    }

    fun write(address: Int, value: UByte) {
        when (address) {
            0x4016 -> {
                if ((this.value.toInt() xor value.toInt()) and 0x01 != 0x00) {
                    if (value.toInt() and 0x01 != 0x00) {
                        isInputPad = false
                    } else {
                        isInputPad = true
                        inputPad1Count = 0
                    }
                }
                this.value = value
            }

            else -> error("Illegal read address. $address(${address.toString(radix = 16)}")
        }
    }
}

