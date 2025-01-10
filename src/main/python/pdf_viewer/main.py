import sys
import os
from PyQt6.QtWidgets import QApplication
from ui.main_window import MainWindow

def main():
    app = QApplication(sys.argv)
    project_root = os.path.dirname(os.path.abspath(__file__))
    window = MainWindow(project_root)
    window.show()
    app.exec()

if __name__ == "__main__":
    main()