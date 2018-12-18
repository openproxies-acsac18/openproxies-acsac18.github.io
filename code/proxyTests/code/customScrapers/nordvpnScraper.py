import requests

def scrape(logger):
    try:
        j = requests.get('https://nordvpn.com/wp-admin/admin-ajax.php?searchParameters%5B0%5D%5Bname%5D=proxy-country&searchParameters%5B0%5D%5Bvalue%5D=&searchParameters%5B1%5D%5Bname%5D=proxy-ports&searchParameters%5B1%5D%5Bvalue%5D=&offset=0&limit=100000&action=getProxies').json()
    except requests.exceptions.ConnectionError:
        return ''
    proxies = []
    for proxy in j:
        proxies.append(proxy['ip'] + ":" + proxy['port'])
    return '\n'.join(proxies)