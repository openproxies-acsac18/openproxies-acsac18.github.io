import requests
from bs4 import BeautifulSoup as bs
from random import random
from time import sleep
import base64

url = "http://free-proxy.cz/en/proxylist/main/%s"
user_agent = 'Mozilla/5.0 (Windows NT 6.1; Win64; x64)'

# free-proxy.cz seems to be picky and very easy to get blocked by, so use these headers
# and cookies (the exact ones sent by Chrome on my computer)
cookies = {
    'fp': '2959894ef118357996d9a11ede3aee42',
    '__utmc': '104525399',
    '__utmz': '104525399.1515708639.1.1.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=(not%20provided)',
    '__utma': '104525399.835556068.1515708638.1515708638.1515725591.2',
    '__utmt': '1',
    '__utmb': '104525399.12.10.1515725591',
}
headers = {
    'DNT': '1',
    'Accept-Encoding': 'gzip, deflate',
    'Accept-Language': 'en-US,en;q=0.9',
    'Upgrade-Insecure-Requests': '1',
    'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8',
    'Cache-Control': 'max-age=0',
    'Connection': 'keep-alive',
}

def scrape(logger):
    """ Scrape free-proxy.cz and return a new line separated list of proxies """
    # Note: WHile this is slow, any attempt to parallelize it or speed it up just triggers
    # their antibotting block
    proxies = []
    pageNum = 1
    numPages = getNumPages()
    while pageNum < numPages + 1:
        try:
            page = requests.get(url % pageNum, headers=headers, cookies=cookies, timeout=1).text
        except requests.exceptions.ConnectionError:
            break
        proxies.extend(extractProxies(page))
        pageNum += 1
        # Sleep (aggresively) so we don't get blocked
        sleep(random() * 2 + 1)
        logger.debug("Finished extracting proxies from page %s/%s" % (pageNum, numPages + 1))
    return '\n'.join(proxies)

def getNumPages():
    page = requests.get(url % '1', headers=headers, cookies=cookies).text
    html = bs(page, "lxml")
    pageNumbers = [intish(x.text) for x in html.find('div', {'class': 'paginator'}).findAll('a')]
    return max(pageNumbers)

def intish(inputk):
    try:
        return int(inputk)
    except ValueError:
        return 0

def extractProxies(page):
    html = bs(page, "lxml")
    proxyTable = html.find('table', {'id': 'proxy_list'})
    proxyTableBody = proxyTable.find('tbody')
    proxies = []
    for row in proxyTableBody.findAll('tr'):
        try:
            b64blob = row.find('td', {'style': "text-align:center", 'class': "left"}).find('script').text.split("\"")[1]
            ip = base64.b64decode(b64blob)
            port = row.find('span', {'class': 'fport'}).text
            proxy = ip + ":" + port
            proxies.append(proxy)
        except AttributeError:
            # Skip over the rows with junk data
            pass
    return proxies