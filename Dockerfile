FROM tomcat:9.0

# extract the contents of labbcat.war into /labbcat
RUN mkdir /labbcat
COPY bin/labbcat.war /labbcat
WORKDIR /labbcat
RUN jar -xf labbcat.war
RUN rm labbcat.war
# link Tomcat root app to /labbcat
RUN ln -s /labbcat /usr/local/tomcat/webapps/ROOT

# for docker installations, WEB-INF/install.jsp should never be executed
# however we make compatible changes to it anyway, just in case the unsupervised
# installer fails for some superficial reason
#
# modify mysql labbcat user definition so that it can connect from any host
# because mysql will run in a different container
RUN sed -i "s/'localhost'/'%'/" WEB-INF/install.jsp

# password-protect the webapp
RUN cp WEB-INF/lib/mysql-connector-java-latest.jar /usr/local/tomcat/lib/
RUN sed -i '/USER-SECURITY/d' WEB-INF/web_install.xml

# install Praat
RUN mkdir /opt/praat
WORKDIR /opt/praat
RUN wget https://www.fon.hum.uva.nl/praat/praat6116_linux64barren.tar.gz
RUN tar xvf praat6116_linux64barren.tar.gz
RUN rm praat6116_linux64barren.tar.gz
RUN cp praat_barren /usr/bin/praat

WORKDIR /labbcat

# Tomcat runs on port 8080
EXPOSE 8080