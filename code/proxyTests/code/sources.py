""" a dictionary, describing the sources of proxy lists """

sources = [
    #    {
    #        MBS: NO LONGER AVAILABLE
    #        'name' : 'xroxy',
    #        'url' : 'http://madison.xroxy.com/download.php?filepath=downloadFLP2Bw&filename=XROXY-Alltypes-Anyport-All_countries',
    #        'compression' : 'gz'
    #    },
    {
        'name' : 'clarketm',
        'retrieve' : 'curl -s "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list.txt" | sed \'1,2d; $d; s/\s.*//; /^$/d\''
    },
    {
        'name' : 'freeproxy.ru',
        'url' : 'http://www.freeproxy.ru/download/lists/goodproxy.txt'
    },
    {
        'name' : 'multiproxy1',
        'url' : 'http://multiproxy.org/txt_all/proxy.txt'
    },
    {
        'name' : 'multiproxy2',
        'url' : 'http://multiproxy.org/txt_anon/proxy.txt'
    },
    {
        'name' : 'workingproxies',
        'url' : 'http://www.workingproxies.org/plain-text'
    },
    {
        'name' : 'proxydaily',
        'url' : 'http://proxy-daily.com/2017/10/05-10-2017-proxy-list-4/',
        'scrape' : True
    },
    {
        'name' : 'xroxy',
        'customScrape' : True
    },
    {
         'name' : 'nordvpn',
         'customScrape' : True
    },
    {
        'name' : 'proxyprober',
        'customScrape' : True
    },
#    {
#        'name' : 'freeproxycz',
#        'customScrape' : True
#    }
]

