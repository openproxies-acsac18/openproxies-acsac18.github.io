## Project Description

This project (a collaboration between researchers at Georgetown University and Northeastern University) conducts a comprehensive study of open proxies, encompassing more than 107,000 listed open proxies and 13M proxy requests over a 50 day period.

We provide a broad study that examines the availability, success rates, diversity, and also (mis)behavior of proxies. Our results show that listed open proxies suffer poor availability — more than 92% of open proxies that appear on aggregator sites are unresponsive to proxy requests. Much more troubling, we find numerous examples of malicious open proxies in which HTML content is manipulated to mine cryptocurrency (that is, cryptojacking). We additionally detect TLS man-in-the-middle (MitM) attacks, and discover numerous instances in which binaries fetched through proxies were modified to include remote access trojans and other forms of malware. As a point of comparison, we conduct and discuss a similar measurement study of the behavior of Tor exit relays. We find no instances in which Tor relays performed TLS MitM or manipulated content, suggesting that Tor offers a far more reliable and safe form of proxied communication.

## Paper

**An Extensive Evaluation of the Internet's Open Proxies**  
_Proceedings of the 34th Annual Computer Security Applications Conference (ACSAC 2018)_<br/>
Akshaya Mani*, [Tavish Vaidya*](https://security.cs.georgetown.edu/~tavish/), [David Dworken](https://daviddworken.com/), and [Micah Sherr](https://security.cs.georgetown.edu/~msherr/) (* co-first authors)<br/>
Full Text: [available here](https://security.cs.georgetown.edu/~msherr/papers/openproxies.pdf)

## Code

This code fetches a list of files from numerous open proxies and every available Tor exit relay. For more details about the list of files retrieved, refer Section 4 in paper.

The open proxies module first populates a list of advertised proxies from a number of proxy aggregator sites and augments this list with the results of running [ProxyBroker](https://github.com/constverum/ProxyBroker), an open source tool for finding open proxies. It then attempts to connect to every proxy in this list and, if successful, classifies the proxy as a HTTP, CONNECT, or SOCKS proxy. Finally, it requests several files (URLs) from the set of proxies that were successfully classified.

The Tor exit module uses [Exitmap](https://github.com/NullHypothesis/), a fast scanner to fetch files through all Tor exit relays.  

## Installation

To test Open Proxies or Tor exits, install all dependencies. Here, directory \<dir\> must be directory proxyTests (for Open Proxies) or exitmap (for Tor exits).

### Initial requirements

For Debian based systems install following packages: (for other Linux based systems and macOS install equivalent packages)

```
   sudo apt install python-pip libffi-dev libxml2-dev libxslt-dev libssl-dev
```

### Install virtual environment
```
    cd code/<dir>
    virtualenv venv
```

### Activate environment
```
    source venv/bin/activate
```

### Install dependancies
```
    pip install -r requirements.txt
```

### Deactivate environment
```
    deactivate
```

## Run

To run, activate the virtual environment (created earlier) and then execute run.sh in directory \<dir\> (proxyTests for Open Proxies and exitmap for Tor exits). 
```
    cd code/<dir>
    source venv/bin/activate
    ./run.sh
```
