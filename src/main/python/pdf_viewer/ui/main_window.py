from PyQt6.QtWidgets import QMainWindow, QApplication, QWidget

import sys

app = QApplication(sys.argv)

window = QWidget()
window.show()

app.exec()