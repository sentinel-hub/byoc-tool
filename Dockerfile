FROM osgeo/gdal:alpine-small-3.3.0 AS build

RUN apk add --no-cache openjdk8

ADD . .

RUN ./gradlew test

RUN ./gradlew distTar

FROM osgeo/gdal:alpine-small-3.3.0

RUN apk add --no-cache openjdk8-jre

COPY --from=build build/distributions/byoc-tool.tar /byoc-tool.tar
RUN tar -xvf /byoc-tool.tar && rm /byoc-tool.tar

ENTRYPOINT ["/byoc-tool/bin/byoc-tool"]

CMD ["help"]
