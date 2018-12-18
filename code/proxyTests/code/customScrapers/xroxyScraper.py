import requests
from bs4 import BeautifulSoup as bs
from random import random
from time import sleep

url = "http://www.xroxy.com/proxylist.php?port=&type=&ssl=&country=&latency=&reliability=&sort=reliability&desc=true&pnum=%s#table"

cookies = {
    '_ga': 'GA1.2.2013856846.1515702119',
    '_gid': 'GA1.2.1325561073.1515702119',
    'phpbb2mysql_data': 'a%3A2%3A%7Bs%3A11%3A%22autologinid%22%3Bs%3A0%3A%22%22%3Bs%3A6%3A%22userid%22%3Bi%3A-1%3B%7D',
    '__utma': '104024137.2013856846.1515702119.1515702147.1515702147.1',
    '__utmc': '104024137',
    '__utmz': '104024137.1515702147.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)',
    'phpbb2mysql_sid': '879f3d13df0c585a8c6625ea0e86a68d',
    '_gat': '1',
}
headers = {
    'If-None-Match': '78d3ab822f661464aabf8357cda7881d',
    'DNT': '1',
    'Accept-Encoding': 'gzip, deflate',
    'Accept-Language': 'en-US,en;q=0.9',
    'Upgrade-Insecure-Requests': '1',
    'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8',
    'Cache-Control': 'max-age=0',
    'Connection': 'keep-alive',
    'If-Modified-Since': 'Fri, 12 Jan 2018 14:33:01 GMT',
}


def scrape(logger):
    """ Scrape xroxy and return a new line separated list of proxies """
    s = requests.Session()
    retryStrategy = requests.packages.urllib3.util.retry.Retry(total=10, backoff_factor=0.1)
    s.mount('http://', requests.adapters.HTTPAdapter(max_retries=retryStrategy))
    proxies = []
    pageNum = 0
    d = s.get(url % pageNum, headers=headers, cookies=cookies, timeout=1).text
    t1 = bs(d, "lxml").find('table', {'class': 'tbl'})
    if t1 is None:
        print "aborting: data = %s" % d
        return None
    t2 = t1.findAll('small')[1].find('b')
    numProxies = int(t2.text)
    while pageNum < (numProxies / 10) + 1:
        try:
            page = s.get(url % pageNum, headers=headers, cookies=cookies, timeout=1).text
        except requests.exceptions.ConnectionError:
            break
        if not containsProxies(page):
            break
        proxies.extend(extractProxies(page))
        logger.debug("Finished extracting proxies from page %s/%s" % (pageNum, (numProxies / 10) + 1))
        pageNum += 1
        # Sleep so we don't get blocked
        sleep(random() * 2)
    return '\n'.join(proxies)

def extractProxies(page):
    """ Scrape the given xroxy HTML and return a list of the proxies in it """
    proxies = []
    html = bs(page, "lxml")
    proxyTable = html.findAll('table')[4]
    for proxyRow in proxyTable.findAll('tr'):
        if "View this Proxy details" not in str(proxyRow):
            # Skip the headers
            continue
        ipElem = proxyRow.findAll('a', {'title': "View this Proxy details"})[0]
        ip = ipElem.text.strip()
        port = ipElem.parent.fetchNextSiblings()[0].text
        proxy = ip + ":" + port
        proxies.append(str(proxy))
    return proxies


def containsProxies(page):
    """ Whether the current HTML contains any proxies (once it doesn't we hit the end of the pages)"""
    html = bs(page, "lxml")
    try:
        proxyTable = html.findAll('table')[4]
        return len(proxyTable) != 5
    except IndexError:
        return False
