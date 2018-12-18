LIBDIR=../lib
LIBS=.:$(LIBDIR)/argparse4j-0.8.1.jar:$(LIBDIR)/commons-codec-1.10.jar:$(LIBDIR)/commons-compress-1.16.1.jar:$(LIBDIR)/commons-io-2.6.jar:$(LIBDIR)/commons-logging-1.2.jar:$(LIBDIR)/geoip2-2.11.0.jar:$(LIBDIR)/geoip2-2.11.0-javadoc.jar:$(LIBDIR)/geoip2-2.11.0-sources.jar:$(LIBDIR)/gson-2.6.2.jar:$(LIBDIR)/httpclient-4.5.4.jar:$(LIBDIR)/httpcore-4.4.7.jar:$(LIBDIR)/jackson-annotations-2.9.3.jar:$(LIBDIR)/jackson-core-2.9.3.jar:$(LIBDIR)/jackson-databind-2.9.3.jar:$(LIBDIR)/maxmind-db-1.2.2.jar
TEMPBIN=tmp/bin/
DEST=../jar/
CWD=$(shell pwd)


all:	makemegajar


compile:
	echo "Compiling!"
	mkdir -p $(TEMPBIN)
	javac -cp $(LIBS) -d $(TEMPBIN) edu/georgetown/cs/seclab/*.java


makemegajar:	compile
	echo "Making a mega JAR file!"
	cd $(TEMPBIN) ; find $(CWD)/$(LIBDIR) -name \*.jar -exec jar xvf {} \;
	cd $(TEMPBIN) ; find . -name \*.java -exec rm {} \;
	cd $(TEMPBIN) ; find . -name \*.html -exec rm {} \;
	cd $(TEMPBIN) ; find . -name \*.js -exec rm {} \;
	cd $(TEMPBIN) ; find . -name \*.css -exec rm {} \;
	cd $(TEMPBIN) ; rm -rf META-INF
	mkdir -p $(DEST)
	cd $(TEMPBIN) ;	jar cvf $(CWD)/$(DEST)/package.jar .

clean:
	mkdir -p $(DEST)
	rm -f $(DEST)/*.jar
	rm -rf $(TEMPBIN)/

