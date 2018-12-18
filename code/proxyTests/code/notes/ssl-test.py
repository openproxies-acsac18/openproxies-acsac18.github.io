import binascii
import ssl
import urllib3
import requests
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes


HTTPResponse = urllib3.response.HTTPResponse

orig_HTTPResponse__init__ = HTTPResponse.__init__

def new_HTTPResponse__init__(self, *args, **kwargs):
    orig_HTTPResponse__init__(self, *args, **kwargs)
    try:
        self.peercert = self._connection.sock.getpeercert(binary_form=True)
        print "retrieved cert"
    except AttributeError as e:
        print "Crap!", e
        self.peercert = None

HTTPResponse.__init__ = new_HTTPResponse__init__

HTTPAdapter = requests.adapters.HTTPAdapter
orig_HTTPAdapter_build_response = HTTPAdapter.build_response

def new_HTTPAdapter_build_response(self, request, resp):
    response = orig_HTTPAdapter_build_response(self, request, resp)
    try:
        response.peercert = resp.peercert
    except AttributeError as e:
        print "crud", e
        pass
    return response

HTTPAdapter.build_response = new_HTTPAdapter_build_response

#p = '109.195.66.153:3128'
p = '118.114.77.47:8080'
proxies = {
    'http': 'http://%s' % p,
    'https': 'http://%s' % p,
}
proxies2 = {
    'http': 'socks4://222.188.10.1:1080',
    'https': 'socks4://222.188.10.1:1080',
    }

timeouts = (10,12)                 # connect and read

response = requests.get('https://security.cs.georgetown.edu/assets/gu-gate.jpg', proxies=proxies, timeout=timeouts)
if response.peercert is not None:
    encoded = ssl.DER_cert_to_PEM_cert(response.peercert)
    print encoded
    cert = x509.load_pem_x509_certificate(bytes(encoded), default_backend())
    print cert.serial_number
    print binascii.hexlify(cert.fingerprint(hashes.SHA256()))

