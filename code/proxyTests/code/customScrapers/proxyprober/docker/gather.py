"""Gather proxies from the providers without
   checking and save them to a file."""

""" from https://proxybroker.readthedocs.io/en/latest/examples.html """

import asyncio
import signal
import os
from proxybroker import Broker




def handler(signum, frame):
    exit(0)

        

async def save(proxies):
    """prints proxies"""
    while True:
        proxy = await proxies.get()
        if proxy is None:
            break
        print('%s:%d' % (proxy.host, proxy.port))


def main():

    # Set the signal handler and an alarm
    signal.signal(signal.SIGALRM, handler)
    signal.alarm(5 * 60)
    
    
    proxies = asyncio.Queue()
    broker = Broker(proxies)
    tasks = asyncio.gather(broker.grab(),
                           save(proxies))
    loop = asyncio.get_event_loop()
    loop.run_until_complete(tasks)


if __name__ == '__main__':
    main()
