FROM openjdk:8-jdk AS build

ADD . .

RUN ./gradlew build



FROM osgeo/gdal:alpine-small-3.0.2

ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk/jre

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/java-1.8-openjdk/jre/bin:/usr/lib/jvm/java-1.8-openjdk/bin:/byoc/bin

ENV JAVA_VERSION=8u222

ENV JAVA_ALPINE_VERSION=8.222.10-r0

RUN /bin/sh -c set -x && apk add --no-cache openjdk8-jre="$JAVA_ALPINE_VERSION"

COPY --from=build build/distributions/byoc-tool.tar /byoc-tool.tar
RUN tar -xvf /byoc-tool.tar && rm /byoc-tool.tar

ENTRYPOINT ["/byoc-tool/bin/byoc-tool"]
CMD ["help"]
