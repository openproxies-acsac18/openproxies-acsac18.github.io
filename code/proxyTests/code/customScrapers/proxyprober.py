import docker
import os
import sys

def get_script_path():
    return os.path.dirname(os.path.realpath(sys.argv[0]))

def scrape(logger):
    client = docker.from_env()

    path = "%s/%s" % ( get_script_path(), "customScrapers/proxyprober/docker/")
    logger.info('building ProxyProber docker container (yup, seriously)')
    client.images.build(path=path,tag="proxyprober")

    logger.info('running proxyprober container (this can take up to 10 minutes, and won\'t show any output)')
    res = client.containers.run("proxyprober",remove=True)
    return res
