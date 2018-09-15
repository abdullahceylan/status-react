import requests
import re
from urllib import urlretrieve


def pytest_addoption(parser):
    parser.addoption("--nightly",
                     action="store",
                     default=True)
    parser.addoption('--dmg',
                     action='store',
                     default=None,
                     help='Url or local path to dmg')


def pytest_configure(config):
    import logging
    logging.basicConfig(level=logging.INFO)
    if config.getoption('nightly'):
        raw_data = requests.request('GET', 'https://status-im.github.io/nightly/').text
        dmg_url = re.findall('href="(.*dmg)', raw_data)[0]
        urlretrieve(dmg_url, 'nightly.dmg')

