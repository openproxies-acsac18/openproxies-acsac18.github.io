from requests import session
import xml.etree.ElementTree as ET


def scrape(logger):
    """ Scrape xroxy and return a new line separated list of proxies """

    lst = ""
    
    with session() as c:
        c.get('http://www.xroxy.com/')
        c.get('http://www.xroxy.com/proxylist.htm')
        c.get('http://www.xroxy.com/obtain.php?port=&type=&ssl=&country=&latency=&reliability=&sort=reliability&desc=true')
        response = c.get('http://www.xroxy.com/proxyrss.xml')

        logger.debug( 'got xroxy response: %s' % response.headers )

        root = ET.fromstring(response.text)

        for i in root.findall('./channel/item/{http://www.proxyrss.com/content}proxy'):
            ip = i.findall('./{http://www.proxyrss.com/content}ip')[0].text
            port = i.findall('./{http://www.proxyrss.com/content}port')[0].text
            lst += "%s:%s\n" % (ip,port)

    return lst

    
    
