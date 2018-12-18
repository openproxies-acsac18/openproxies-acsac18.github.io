FROM python:3

WORKDIR /usr/src/app
ADD gather.py .
RUN git clone https://github.com/constverum/ProxyBroker.git
ADD requirements.txt .
RUN pip install -r requirements.txt
CMD [ "python", "./gather.py" ]
