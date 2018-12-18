A tool that Micah wrote to automatically fetch and merge lists of proxies.

That's called: fetcher.py

It uses the proxies listed in sources.py.


There's a separate tool, classify.py, for automagically classifying
the proxies.

There are some steps to initialize the IP-to-ASN mapping files:

* pyasn_util_download.py --latest
* pyasn_util_convert.py --single <Downloaded RIB File> <ipasn_db_file_name>
* gzip <ipasn_db_file_name>
* ln -s <ipasn_db_file_name> ipasn_current.gz


NOTE: all of the above is done automatically in run.sh
