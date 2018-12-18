""" 
This program looks at a classified JSON and figures out what 
are the most popular proxy ports

author:  Micah Sherr <msherr@cs.georgetown.edu>

"""


import json
import argparse
import logging
import re
import time
import calendar
import time


TIMESTAMP = time.time()


def main():
    global proxy_defs
    global logger
    global num_proxies
    global asndb
    global prober
    
    parser = argparse.ArgumentParser(description='find-proxies-by-port')
    parser.add_argument('-l', '--log', dest='logfile', help='log file to write to', required=True)
    parser.add_argument('-i', '--import', dest='importjson', help='JSON file to import', required=True)
    parser.add_argument('-e', '--exclude', dest='excludes', help='comma-separated list of ports to exclude', default="80,443")    
    parser.add_argument('-n', '--num', dest='number', help='top-N most popular ports', default=5, type=int)
    args = parser.parse_args()
    
    # set up logging
    FORMAT = '%(asctime)-15s|%(levelname)s|%(message)s|'
    logging.Formatter.converter = time.gmtime
    if args.logfile == '-':
        logging.basicConfig(format=FORMAT, level=logging.INFO)
    else:
        logging.basicConfig(filename=args.logfile,
                            filemode='w',
                            format=FORMAT,
                            level=logging.INFO)
    logger = logging.getLogger('fetcher')
    print( "Logging to %s" % args.logfile )
    logger.info('program called with arguments: %s' % args)
    logger.info('logging times are in UTC/GMT; local clock offset from GMT (in secs): %d' %
                (calendar.timegm(time.gmtime()) - calendar.timegm(time.localtime())))
    logger.info('lookup timestamps will be set to %s' % TIMESTAMP )

    logger.info( 'reading in proxy file: %s' % args.importjson )
    with open(args.importjson,'r') as f:
        s = f.read()
        proxy_defs = json.loads(s)

    exclude_list = args.excludes.split(',')
    logger.info( 'excluding ports: %s' % exclude_list )

    potentially_good_proxies = 0
    counters = {}
    
    for proxy_name in proxy_defs.keys():
        if "type" not in proxy_defs[proxy_name]:
            continue
        potentially_good_proxies += 1
        (host,port) = proxy_name.split(':')
        if port in exclude_list:
            continue
        
        if port not in counters:
            counters[port] = 0
        counters[port] += 1

    for p in counters.keys():
        logger.info( 'port %s has count %d' % (p,counters[p]) )

    l = []
    for (k,v) in counters.iteritems():
        l.append( (v,k) )

    l_s = sorted(l, key=lambda tup: tup[0], reverse=True)

    for i in range(0,min(len(l_s),args.number)):
        (count,port) = l_s[i]
        frac = 100.0 * float(count) / potentially_good_proxies
        logger.info( 'port %s has count %d (%f%% of all potentially good proxies)' % (port,count,frac) )
    
if __name__ == "__main__":
    main()
