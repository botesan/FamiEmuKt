package jp.mito.famiemukt.frontend

import jp.mito.famiemukt.emurator.NesSystem
import jp.mito.famiemukt.emurator.apu.AudioSampleNotifier
import jp.mito.famiemukt.emurator.cartridge.BackupRAM
import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.util.loadRetron5SavFile
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.swing.JFrame
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val NO_WAIT: Boolean = false

class DisplayFrame(iNesFilePath: String) : JFrame() {
    private val image: BufferedImage = BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB)
    private var system: NesSystem? = null

    private val audioSampleNotifier = object : AudioSampleNotifier {
        val format = AudioFormat(
            AudioFormat.Encoding.PCM_UNSIGNED,
            AUDIO_SAMPLING_RATE.toFloat(),
            Byte.SIZE_BITS,
            1,
            1,
            AUDIO_SAMPLING_RATE.toFloat(),
            ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
        )
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val line = (AudioSystem.getLine(info) as SourceDataLine).also {
            it.open(format, AUDIO_SAMPLING_RATE)
            it.start()
        }

        val writeBuffer: ByteArray = ByteArray(size = AUDIO_SAMPLING_RATE / 60 / 2)
        var writeBufferPosition: Int = 0
        override fun notifySample(value: UByte) {
            // TODO: 音声出力に問題ありっぽい（パフォーマンス）
            //  途切れた後にノイズが出てくるのは放っておくとループで再生されるため？
            //  そもそもループで再生されてしまうのは良い？
            //  ループ再生されるのはデバッグで停止するから？
//            writeBuffer[0] = value.toByte()
//            line.write(writeBuffer, 0, 1)
            writeBuffer[writeBufferPosition] = value.toByte()
            writeBufferPosition++
            if (writeBufferPosition >= writeBuffer.size) {
                @Suppress("KotlinConstantConditions")
                if (NO_WAIT.not()) line.write(writeBuffer, 0, writeBuffer.size)
                writeBufferPosition = 0
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private val thread: Thread = thread(start = false) {
        try {
            val iNesFile = File(iNesFilePath)
            val iNesData = iNesFile.readBytes()
            val backupRAMFile = File(iNesFilePath.removeSuffix(suffix = iNesFile.extension) + "sav")
            val backupRAM = runCatching {
                loadRetron5SavFile(input = backupRAMFile.readBytes())
            }.onFailure { it.printStackTrace() }.getOrNull() ?: BackupRAM()
            val cartridge = Cartridge(backupRAM = backupRAM, iNesData = iNesData)
            val system = NesSystem(cartridge, audioSampleNotifier, AUDIO_SAMPLING_RATE)
            this.system = system
            system.powerOn()
            var frameCount = 1L
            val firstTime = System.currentTimeMillis()
            var nextTime = firstTime + frameCount * 1_000 / 60
            while (true) {
                val drawFrame = system.executeMasterClockStep()
                if (drawFrame) {
                    if (isVisible.not()) break
                    image.raster.setDataElements(0, 0, 256, 240, system.pixelsRGB32)
                    EventQueue.invokeLater { repaint() }
                    // TODO: 音声出力に問題ありっぽい（パフォーマンス）
                    val sleepTime = nextTime - System.currentTimeMillis()
                    @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
                    if (sleepTime > 0 && NO_WAIT.not()) Thread.sleep(sleepTime) // TODO: 要調整？
                    nextTime = firstTime + (++frameCount) * 1_000 / 60
                }
            }
        } catch (th: Throwable) {
            println(system?.debugInfo(nest = 0))
            th.printStackTrace()
            exitProcess(status = -1)
        }
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(256 * 5 + 2 * 20, 240 * 4 + 40 + 20)
        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent?) {
                if (thread.state == Thread.State.NEW) {
                    thread.start()
                }
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                when (e?.keyCode) {
                    KeyEvent.VK_UP -> system?.isPad1Up = true
                    KeyEvent.VK_DOWN -> system?.isPad1Down = true
                    KeyEvent.VK_LEFT -> system?.isPad1Left = true
                    KeyEvent.VK_RIGHT -> system?.isPad1Right = true
                    KeyEvent.VK_Z -> system?.isPad1A = true
                    KeyEvent.VK_X -> system?.isPad1B = true
                    KeyEvent.VK_SPACE -> system?.isPad1Select = true
                    KeyEvent.VK_ENTER -> system?.isPad1Start = true
                }
            }

            override fun keyReleased(e: KeyEvent?) {
                when (e?.keyCode) {
                    KeyEvent.VK_UP -> system?.isPad1Up = false
                    KeyEvent.VK_DOWN -> system?.isPad1Down = false
                    KeyEvent.VK_LEFT -> system?.isPad1Left = false
                    KeyEvent.VK_RIGHT -> system?.isPad1Right = false
                    KeyEvent.VK_Z -> system?.isPad1A = false
                    KeyEvent.VK_X -> system?.isPad1B = false
                    KeyEvent.VK_SPACE -> system?.isPad1Select = false
                    KeyEvent.VK_ENTER -> system?.isPad1Start = false
                    KeyEvent.VK_D -> print(system?.debugInfo(nest = 0))
                    KeyEvent.VK_R -> {
                        val system = system
                        if (system != null) {
                            system.reset()
                        } else if (thread.state == Thread.State.NEW) {
                            thread.start()
                        }
                    }
                }
            }
        })
    }

    // Near 4:3 0.75 -> 0.7
    private val transform = AffineTransform.getTranslateInstance(20.0, 40.0).apply { scale(5.0, 4.0) }

    override fun paint(g: Graphics?) {
        val g2d = (g as Graphics2D)
        g2d.drawImage(image, transform, this)
    }

    companion object {
        const val AUDIO_SAMPLING_RATE: Int = 48_000 // 48_000 44_100 22_050 11_025
    }
}
