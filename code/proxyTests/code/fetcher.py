""" Micah's open proxy fetching tool """

import json
import sources
import argparse
import logging
import urllib2
import socket
import httplib
import subprocess
import gzip
import re
from StringIO import StringIO
from customScrapers import xroxyScraper2, nordvpnScraper, freeproxydotczScraper, proxyprober


proxy_defs = dict()


def add_to_proxy_dict( source, longstring ):
    global logger
    global proxy_defs
    for proxy in longstring.split("\n"):
        proxy = proxy.rstrip()
        if proxy == "" : continue
        if re.search("\d+\.\d+\.\d+\.\d+:\d+",proxy) is None:
            logger.warn("Invalid proxy: '%s'" % proxy)
        else:
            logger.debug("Considering proxy: '%s'" % proxy)
            if proxy not in proxy_defs:
                proxy_defs[proxy] = { 'name' : proxy, 'sources' : [], 'type' : None }
            if source not in proxy_defs[proxy]['sources']:
                proxy_defs[proxy]['sources'].append(source)

                
def scrape_page( s ):
    myregex = "\d+\.\d+\.\d+\.\d+:\d+"
    matches = re.findall(myregex,s)
    res = ""
    for match in matches:
        res += match + "\n"
    return res


def main():
    global proxy_defs
    global logger

    parser = argparse.ArgumentParser(description='Proxy list parser')
    parser.add_argument('--import', dest='importjson', help='JSON file to import')
    parser.add_argument('--output', dest='output', help='output file (in JSON)', required=True)    
    parser.add_argument('--source', dest='source', help='test only a given source')
    parser.add_argument('--debug', dest='debug', help='turn on debug logging', action='store_true')
    args = parser.parse_args()
    
    if args.importjson is not None:
        with open(args.importjson,'r') as f:
            s = f.read()
            proxy_defs = json.loads(s)
    
    FORMAT = '%(asctime)-15s|%(levelname)s|%(message)s|'
    logging.basicConfig(format=FORMAT,level=logging.INFO)
    logger = logging.getLogger('fetcher')
    if args.debug is True:
        logger.setLevel(logging.DEBUG)

    user_agent = 'Mozilla/5.0 (Windows NT 6.1; Win64; x64)'
    headers = {'User-Agent': user_agent}
    scrapers = {#'xroxy': xroxyScraper2.scrape,
                'nordvpn': nordvpnScraper.scrape,
                'proxyprober' : proxyprober.scrape,
                'freeproxycz': freeproxydotczScraper.scrape}

    for source in sources.sources:
        if args.source is not None and source['name'] != args.source: continue
        logger.info("fetching proxy list: '%s'" % source['name'])
        if 'url' in source:
            logger.debug("fetching by URL")
            try:
                req = urllib2.Request(source['url'], headers=headers )
                data = urllib2.urlopen(req).read()
                if 'compression' in source:
                    if source['compression'] == 'gz':
                        logger.debug( "uncompressing page" )
                        data = gzip.GzipFile(fileobj=StringIO(data)).read()
                    else:
                        logger.warn("unknown compression type: %s'" % source['compression'])
                        data = ""
                if 'scrape' in source:
                    logger.debug( "scraping page" )
                    data = scrape_page(data)
                add_to_proxy_dict(source['name'], data)
            except urllib2.URLError as e:
                logger.warn("cannot retrieve URL: '%s'" % source['url'])
                logger.warn("reason given: %s" % e.reason)
            except httplib.BadStatusLine as e:
                logger.warn("cannot retrieve URL: '%s'" % source['url'])
                logger.warn("reason (httplib.BadStatusLine): %s" % e)
            except socket.error as e:
                logger.warn("cannot retrieve URL: '%s'" % source['url'])
                logger.warn("reason (socket.error): %s" % e)                
            except:
                logger.warn("cannot retrieve URL: '%s' (for reasons unknown)" % source['url'])                
        elif 'retrieve' in source:
            logger.debug("a special retrieval method!")
            try:
                data = subprocess.check_output(source['retrieve'],shell=True)
                add_to_proxy_dict(source['name'], data)
            except subprocess.CalledProcessError as e:
                logger.warn("retrieval method failed: %s'" % e.output)
        elif 'customScrape' in source:
            scraper = scrapers.get(source['name'], None)
            if scraper:
                data = scraper(logger)
                if data is not None:
                    logger.info("grabbed %s proxies from %s" % (len(data.split('\n')), source['name']))
                    add_to_proxy_dict(source['name'], data)
                else:
                    logger.info("could not grab any proxies from %s" % source['name'])
            else:
                logger.warn("No matching scraper found for %s" % source['name'])

    logger.info("saving JSON output to %s" % args.output)
    with gzip.open(args.output,"wb") as f:
        f.write(json.dumps(proxy_defs, sort_keys=True, indent=4, separators=(',', ': ')))


if __name__ == "__main__":
    main()
