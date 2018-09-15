import org.sikuli.script.SikulixForJython
from sikuli import *
import subprocess


class BaseTestCase:

    def setup_method(self, method):
        outcome = subprocess.check_output(['hdiutil', 'attach', 'nightly.dmg'])
        print(outcome)
        outcome = subprocess.check_output(['cp', '-rf', '/Volumes/Status/Status.app', '/Applications/'])
        print(outcome)
        outcome = subprocess.check_output(['hdiutil', 'detach', '/Volumes/Status/'])
        print(outcome)
        import time
        time.sleep(10)
        openApp('Status.app')

    def teardown_method(self, method):
        closeApp('Status.app')
        # for dir in '/Applications/Status.app', '/Library/Application\ Support/StatusIm', \
        #            '/Users/yberdnyk/Library/Caches/StatusIm':
        #     outcome = subprocess.check_output(['echo', <password>, '|', 'sudo', '-S', 'rm', '-rf', dir])
        #     print(outcome)
