FROM ubuntu:trusty

# Update the APT cache
RUN apt-get   update

# Install and setup project dependencies
RUN apt-get install -y curl git wget unzip

# prepare for Java download
RUN apt-get install -y software-properties-common
RUN apt-get -y install openjdk-7-jre-headless
RUN apt-get install -y mongodb-clients
ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64

# fix wget
RUN export HTTP_CLIENT="wget --no-check-certificate -O"

# grab leiningen
RUN wget https://raw.github.com/technomancy/leiningen/stable/bin/lein -O /usr/local/bin/lein
RUN chmod +x /usr/local/bin/lein
ENV LEIN_ROOT yes
RUN lein

# add scripts
ADD ./opt /opt

# grab project
RUN git clone https://github.com/kordano/lesezeichen.git /opt/lesezeichen

# define port
EXPOSE 8087

# create uberjar with leiningen
RUN /opt/build-it

CMD ["/opt/start-it"]
