import sys
import os

from PyQt6.QtCore import QSize, Qt
from PyQt6.QtGui import QIcon, QAction
from PyQt6.QtWidgets import QApplication, QMainWindow, QToolBar

class MainWindow(QMainWindow):
    def __init__(self, project_root):
        super().__init__()

        self.project_root = project_root
        self.setWindowTitle("SystmOne PDF Manager")
        self.setMinimumSize(910, 540)
        self.showMaximized()

        self.setup_ui()

    def setup_ui(self):
        self.setup_toolbar()
        self.setup_thumbnail_area()
        self.setup_preview_pane()
    
    def setup_toolbar(self):
        icon_path = os.path.join(self.project_root, 'assets', 'smile.png')
        toolbar = QToolBar('Main Toolbar')
        self.addToolBar(toolbar)
        toolbar.setIconSize(QSize(16, 16))
        my_action = QAction(QIcon(icon_path), 'Smile...', self)
        toolbar.addAction(my_action)
        toolbar.addSeparator()

    def setup_thumbnail_area(self):
        pass

    def setup_preview_pane(self):
        pass