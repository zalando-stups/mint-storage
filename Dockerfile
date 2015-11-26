FROM zalando/openjdk:8u66-b17-1-2

MAINTAINER Zalando SE

COPY target/mint-storage.jar /

EXPOSE 8080
ENV HTTP_PORT=8080

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) $(newrelic-agent) $(appdynamics-agent) -jar /mint-storage.jar

ADD target/scm-source.json /scm-source.json
