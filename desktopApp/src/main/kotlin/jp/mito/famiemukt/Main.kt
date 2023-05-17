package jp.mito.famiemukt

import jp.mito.famiemukt.frontend.DisplayFrame
import java.awt.EventQueue
import javax.swing.UIManager

fun main(args: Array<String>) {
    EventQueue.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val frame = DisplayFrame(iNesFilePath = args.first())
        frame.isVisible = true
    }
}
