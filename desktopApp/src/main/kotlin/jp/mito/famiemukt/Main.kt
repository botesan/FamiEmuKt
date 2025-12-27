package jp.mito.famiemukt

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import jp.mito.famiemukt.frontend.DisplayFrame
import java.awt.EventQueue
import javax.swing.UIManager

fun main(args: Array<String>) {
    Logger.setLogWriters(CommonWriter())
    Logger.setMinSeverity(Severity.Info)
    EventQueue.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val frame = DisplayFrame(iNesFilePath = args.first())
        frame.isVisible = true
    }
}
