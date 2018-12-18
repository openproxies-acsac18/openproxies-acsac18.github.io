#!/usr/bin/env python2

# Copyright 2014-2016 Philipp Winter <phw@nymity.ch>
# Copyright 2014 Josh Pitts <josh.pitts@leviathansecurity.com>
#
# This file is part of exitmap.
#
# exitmap is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# exitmap is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with exitmap.  If not, see <http://www.gnu.org/licenses/>.

"""
patchingCheck.py
by Joshua Pitts josh.pitts@leviathansecurity.com
twitter: @midnite_runr

Module to detect binary patching.

-USAGE-
Make appropriate changes in the EDIT ME SECTION

Then run:
./bin/exitmap -d 5 patchingCheck

"""

import binascii
import requests
import sys
import time
import os
import urllib3
#try:
#    import urllib2
#except ImportError:
#    import urllib.request as urllib2
import tempfile
import logging
import decimal
import pickle
import hashlib
import json
import git
import gzip
import util
import ssl
from OpenSSL import crypto
from cryptography import x509
from cryptography.hazmat.backends import default_backend
import stem.descriptor.server_descriptor as descriptor
from cryptography.hazmat.primitives import hashes
from datetime import date, datetime
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Timestamp
TIMESTAMP = time.time()

# Log
log = logging.getLogger(__name__)

HTTPResponse = urllib3.response.HTTPResponse
orig_HTTPResponse__init__ = HTTPResponse.__init__

def new_HTTPResponse__init__(self, *args, **kwargs):
    orig_HTTPResponse__init__(self, *args, **kwargs)
    try:
        self.peercert = self._connection.sock.getpeercert(binary_form=True)
        log.debug( 'retrieved certificate' )
    except AttributeError as e:
        log.debug( 'could not retrieve certificate: %s' % e )
        self.peercert = None

HTTPResponse.__init__ = new_HTTPResponse__init__
HTTPAdapter = requests.adapters.HTTPAdapter
orig_HTTPAdapter_build_response = HTTPAdapter.build_response

def new_HTTPAdapter_build_response(self, request, resp):
    response = orig_HTTPAdapter_build_response(self, request, resp)
    try:
        response.peercert = resp.peercert
    except AttributeError as e:
        log.error( 'could not find peercert structure (programming error?): %s' % e )
    return response

HTTPAdapter.build_response = new_HTTPAdapter_build_response

#######################
# EDIT ME SECTION START
#######################

# EDIT ME: exitmap needs this variable to figure out which
# relays can exit to the given destination(s).

#destinations = [("live.sysinternals.com", 80)]
destinations = [("spider.cs-georgetown.net", 80),
                ("revoked.badssl.com", 443),
                ("self-signed.badssl.com", 443),
                ("capricorn.cs-georgetown.net", 443),
                ("download.winzip.com", 80)]

# Only test one binary at a time
# Must provide a Download link
check_files = {
    "http://spider.cs-georgetown.net/headers.php": [None, None, None],
    "http://spider.cs-georgetown.net/mypage.html": [None, None, None],
    "http://spider.cs-georgetown.net/css/main.css": [None, None, None],
    "http://spider.cs-georgetown.net/my.bat": [None, None, None],
    "http://spider.cs-georgetown.net/jpg2png.sh": [None, None, None],
    "http://spider.cs-georgetown.net/zeroes.txt": [None, None, None],
    "https://revoked.badssl.com/": [None, None, None],
    "https://self-signed.badssl.com/": [None, None, None],
    "https://capricorn.cs-georgetown.net/gu-gate.jpg": [None, None, None],
    "http://download.winzip.com/gl/nkln/winzip22_downwz.exe": [None, None, None],
    "http://spider.cs-georgetown.net/words.zip": [None, None, None],
    "http://spider.cs-georgetown.net/simpleflash.swf": [None, None, None],
    "http://spider.cs-georgetown.net/maxmind-db-1.2.2.jar": [None, None, None],
    #"http://live.sysinternals.com/psexec.exe": [None, None],
    #"http://www.ntcore.com/files/ExplorerSuite.exe": [None, None],
}

# Test names
test_names = {
    "http://spider.cs-georgetown.net/headers.php": "headertest",
    "http://spider.cs-georgetown.net/mypage.html": "plain-html",
    "http://spider.cs-georgetown.net/css/main.css": "plain-css",
    "http://spider.cs-georgetown.net/my.bat": "batch-script",
    "http://spider.cs-georgetown.net/jpg2png.sh": "sh-script",
    "http://spider.cs-georgetown.net/zeroes.txt": "zeros",
    "https://revoked.badssl.com/": "badssl-revoked",
    "https://self-signed.badssl.com/": "badssl-selfsigned",
    "https://capricorn.cs-georgetown.net/gu-gate.jpg": "seclab-photo",
    "http://download.winzip.com/gl/nkln/winzip22_downwz.exe": "winzip",
    "http://spider.cs-georgetown.net/words.zip": "words.zipped",
    "http://spider.cs-georgetown.net/simpleflash.swf": "simple-flash",
    "http://spider.cs-georgetown.net/maxmind-db-1.2.2.jar": "maxmind-jar"
}

# Set UserAgent
# Reference: http://www.useragentstring.com/pages/Internet%20Explorer/
#test_agent = 'Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; Trident/6.0)'
test_agent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.94 Safari/5OB37.36'

#######################
# EDIT ME SECTION END
#######################


def setup():
    """
    Perform one-off setup tasks, i.e., download reference files.
    """

    log.info("Creating temporary reference files.")

    for url, _ in check_files.iteritems():

        log.debug("Attempting to download <%s>." % url)

#        request = urllib3.Request(url)
#        request.add_header('User-Agent', test_agent)

        try:
            response = requests.get(url, headers={'user-agent': test_agent}, verify=False)
            log.debug( 'response is %s' % response )

            cert_digest = ""
            if url.split(":")[0] == 'https':         # grab the certificate
                if response.peercert is None:
                    log.info('%s: could not retrieve certificate' % url)
                else:
                    log.debug('%s: parsed certificate' % url)
                    encoded = ssl.DER_cert_to_PEM_cert(response.peercert)
                    cert = x509.load_pem_x509_certificate(bytes(encoded), default_backend())
                    cert_digest = binascii.hexlify(cert.fingerprint(hashes.SHA256()))

            if response.ok == True:
                data = response.content

        except requests.exceptions.ProxyError as e:
            log.debug( 'exception: %s' % e )
        except requests.exceptions.ConnectTimeout as e:
            log.debug( 'exception: %s' % e )
        except requests.exceptions.ConnectionError as e:
            log.debug( 'exception: %s' % e )

#        try:
#            data = urllib3.urlopen(request).read()
#        except Exception as err:
#            log.warning("urlopen() failed: %s %s" % (url, err))

#        file_name = url.split("/")[-1]

        file_name = test_names[url]
        _, tmp_file = tempfile.mkstemp(prefix="temp/exitmap_%s_" % file_name,dir="./")

        with open(tmp_file, "wb") as fd:
            fd.write(data)

        log.debug("Wrote file to \"%s\"." % tmp_file)

        if url == "http://spider.cs-georgetown.net/headers.php":
            check_files[url] = [tmp_file, "CAPTURE-EVERYTHING!!!", cert_digest]
        else:
            check_files[url] = [tmp_file, sha512_file(tmp_file), cert_digest]


def getCurrentGitCommit():
    repo = git.Repo(search_parent_directories=True)
    sha = repo.head.object.hexsha
    return sha


def json_serial(obj):
    """
    JSON serializer for objects not serializable by default json code
    adapted from https://stackoverflow.com/questions/11875770/how-to-overcome-datetime-datetime-not-json-serializable
    """

    if isinstance(obj, (datetime, date)):
        return "%.4d%.2d%.2d" %  (obj.year,obj.month,obj.day)
    elif isinstance(obj,decimal.Decimal):
        return "%f" % obj
    else:
        log.debug( 'unserializable type: %s (will use string print representation)' % type(obj))
        return "%s" % obj


def teardown():
    """
    Perform one-off teardown tasks, i.e., remove reference files.
    """

    log.info("Removing reference files.")

    for _, file_info in check_files.iteritems():

        orig_file, _, _ = file_info
        log.info("Removing file \"%s\"." % orig_file)
        os.remove(orig_file)

    results = []

    for filename in os.listdir("../../exitmap/jsons/tmp"):
        with open("../../exitmap/jsons/tmp/"+filename,'rb') as f:
            res = pickle.load(f)
            f.close()
        results += res
        os.remove("../../exitmap/jsons/tmp/"+filename)

    res_file_name = "fetched-%d-%s" % (int(TIMESTAMP),os.environ['HOSTNAME'])
    _, tmp_file = tempfile.mkstemp(prefix="jsons/%s" % res_file_name,dir="./",suffix=".json.gz")   
    log.info("saving JSON output to %s" % tmp_file.split("/")[-1])
    with gzip.open(tmp_file,"wb") as f:
        contents = {
            'arguments' : "",
            'commit' : getCurrentGitCommit(),
            'timestamp' : TIMESTAMP,
            'hostname' : os.environ['HOSTNAME'],
            'results' : results,
        }
        f.write(json.dumps(contents, default=json_serial, sort_keys=True, indent=4, separators=(',', ': ')))


def sha512_file(file_name):
    """
    Calculate SHA512 over the given file.
    """

    hash_func = hashlib.sha256()

    with open(file_name, "rb") as fd:
        hash_func.update(fd.read())

    return hash_func.hexdigest()


def files_identical(observed_file, original_file):
    """
    Return True if the files are identical and False otherwise.

    This check is necessary because sometimes file transfers are terminated
    before they are finished and we are left with an incomplete file.
    """

    observed_length = os.path.getsize(observed_file)
    original_length = os.path.getsize(original_file)

    if observed_length >= original_length:
        return False

    with open(original_file) as fd:
        original_data = fd.read(observed_length)

    with open(observed_file) as fd:
        observed_data = fd.read()

    return original_data == observed_data


def process_cert(response,exit_desc,fetch_desc,url):
    """
    Examines a certificate and returns a dictionary of information about the certificate.
    if the certificate is not what we expect, then we save the certificate to disk (as PEM)
    """

    result = {}

    if response.peercert is None:
        result['cert_completed'] = False
        result['cert_correct'] = False
        log.warning('%s: could not retrieve certificate' % fetch_desc)
        return result
    else:
        log.debug( '%s: parsed certificate' % fetch_desc )
        result['cert_completed'] = True                
        result['cert_fetchtime'] = None # this is deprecated
        encoded = ssl.DER_cert_to_PEM_cert(response.peercert)
        cert = x509.load_pem_x509_certificate(bytes(encoded), default_backend())
        digest = binascii.hexlify(cert.fingerprint(hashes.SHA256()))
        _, _, cert_digest = check_files[url]
        if digest == cert_digest:
            result['cert_correct'] = True
            log.info( '%s: certificate is as expected' % fetch_desc )
            log.info( 'cert '+test_names[url]+' %s %s' %(digest, cert_digest))
        else:
            result['cert_correct'] = False                    
            thisHost = os.environ['HOSTNAME']
            file_name = "%d_cert_%s_%s_%s" % (int(TIMESTAMP),thisHost,exit_desc.fingerprint,test_names[url])
            _, tmp_file = tempfile.mkstemp(prefix="files/%s" % file_name,dir="./",suffix=".gz")
            result['cert_filename'] = file_name
            log.info( '%s: certificate is not as expected; saving cert (in PEM) to %s' % (fetch_desc,tmp_file.split("/")[-1]) )
            with gzip.open(tmp_file, 'wb') as f:
                f.write(crypto.dump_certificate(crypto.FILETYPE_PEM,cert))

        return result


def run_check(exit_desc):
    """
    Download file and check if its checksum is as expected.
    """

    results = []

    exiturl = util.exiturl(exit_desc.fingerprint)

    for url, file_info in check_files.iteritems():

        orig_file, orig_digest, _ = file_info

        log.debug("Attempting to download <%s> over %s." % (url, exiturl))

        data = None

        fetch_desc = '(exit %s/item %s)' % (exit_desc.fingerprint, test_names[url])

        result = {
            'proxy' : exit_desc.fingerprint,
            'proxy_protocol' : "SOCKS",
            'url' : url,
            'completed' : None,
            'http_status' : None,
            'expected' : None,
            'reason' : None,
            'headers' : None,
            'filename' : None,
            'fetchtime' : None,
            'cert_correct' : None,
            'cert_completed': None,
            'cert_filename' : None,
            'cert_fetchtime' : None,
        }

#        request = urllib3.Request(url)
#        request.add_header('User-Agent', test_agent)
#        response = ""

        try:
            response = requests.get(url, headers={'user-agent': test_agent}, verify=False)
            result['fetchtime'] = response.elapsed.total_seconds()

            if response.elapsed is not None:
                log.debug('%s fetch took %d seconds' % (fetch_desc,result['fetchtime']))

            log.debug('%s fetch did not fail outright' % fetch_desc)

            if url.split(":")[0] == 'https':         # grab the certificate
                cert_res = process_cert(response,exit_desc,fetch_desc,url)
                for k,v in cert_res.items():
                    result[k] = v

            result['headers'] = response.headers
            result['http_status'] = response.status_code
            result['reason'] = response.reason     
           
            if response.ok == True:
                result['completed'] = True
                log.info( '%s successfully retrieved' % fetch_desc )
                data = response.content
            else:
                log.info('%s fetch failed: status code: %d; reason %s' % (fetch_desc, response.status_code, response.reason))
                result['expected'] = False
                result['completed'] = True
                results += [result]
                continue

        except requests.exceptions.ProxyError as e:
            log.debug( 'exception: %s' % e )
            result['completed'] = False
            result['expected'] = False
            result['reason'] = 'connection-failed: proxy-error'
            results += [result]
            continue
        except requests.exceptions.ConnectTimeout as e:
            log.debug( 'exception: %s' % e )
            result['completed'] = False
            result['expected'] = False
            result['reason'] = 'connection-failed: connect-timeout'
            results += [result]
            continue
        except requests.exceptions.ConnectionError as e:
            log.debug( 'exception: %s' % e )
            result['completed'] = False
            result['expected'] = False
            result['reason'] = 'connection-failed: connect-error'
            results += [result]
            continue

#        try:
#            data = urllib3.urlopen(request, timeout=20).read()
#        except Exception as err:
#            log.warning("urlopen() failed for %s: %s" % (exiturl, err))
#            continue

#        if not data:
#            log.warning("No data received from <%s> over %s." % (url, exiturl))
#            continue

#        file_name = url.split("/")[-1]

        file_name = test_names[url]
        _, tmp_file = tempfile.mkstemp(prefix="temp/exitmap_%s_%s_" %
                                       (exit_desc.fingerprint, file_name),dir="./")

        with open(tmp_file, "wb") as fd:
            fd.write(data)

        observed_digest = sha512_file(tmp_file)

        if (observed_digest != orig_digest) and \
           (not files_identical(tmp_file, orig_file)):

#            log.critical("File \"%s\" differs from reference file \"%s\".  "
#                         "Downloaded over exit relay %s." %
#                         (tmp_file, orig_file, exiturl))

            os.remove(tmp_file)

            thisHost = os.environ['HOSTNAME']
            file_name = "%d_%d_%s_%s_%s" % (int(TIMESTAMP),response.status_code,thisHost,exit_desc.fingerprint,test_names[url])
            _, tmp_file = tempfile.mkstemp(prefix="files/%s" % file_name,dir="./",suffix=".gz")
            result['expected'] = False
            result['filename'] = file_name
            log.info('%s: unexpected hash (%s vs expected %s); storing result (compressed) in %s' % (fetch_desc,observed_digest,orig_digest,tmp_file.split("/")[-1]))
                
            with gzip.open(tmp_file, 'wb') as f:
                f.write(data)

        else:
#           log.debug("File \"%s\" fetched over %s as expected." %
#                      (tmp_file, exiturl))

            log.info( '%s --> downloaded expected file' % fetch_desc )
            log.info( 'file '+test_names[url]+' %s %s' %(observed_digest, orig_digest))
            result['expected'] = True

            os.remove(tmp_file)

        results += [result]

    file_name = "fetched-%d-%s" % (int(TIMESTAMP),os.environ['HOSTNAME'])
    _, tmp_file = tempfile.mkstemp(prefix="jsons/tmp/%s" % file_name,dir="./")
    with open(tmp_file,'wb') as f:
        pickle.dump(results, f)
        f.close()


def probe(exit_desc, run_python_over_tor, run_cmd_over_tor, **kwargs):
    """
    Probe the given exit relay and look for modified binaries.
    """

    run_python_over_tor(run_check, exit_desc)


def main():
    """
    Entry point when invoked over the command line.
    """

    setup()

    desc = descriptor.ServerDescriptor("")
    desc.fingerprint = "bogus"
    run_check(desc)

    teardown()

    return 0


if __name__ == "__main__":
    sys.exit(main())
