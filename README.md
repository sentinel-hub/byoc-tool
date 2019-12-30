# Utility tool for Sentinel Hub BYOC service

The Sentinel Hub BYOC Tool is a utility tool available as a Docker image (in this case) and a Java jar, which can be used to prepare your data for use in Sentinel Hub.

It converts your TIFF and JP2 files to Cloud Optimized GeoTIFFs, uploads them to AWS S3 and registers them in the Sentinel Hub BYOC service. When complete, your data should be visible in Sentinel Hub. The same steps can be done manually and are detailed in our documentation https://docs.sentinel-hub.com/api/latest/#/API/byoc, should you prefer or require more control over the process.

## Prerequisites

- A Sentinel Hub OAuth client -- if you don't have one, create one using our [web application](https://apps.sentinel-hub.com/dashboard). Click [here](https://docs.sentinel-hub.com/api/latest/#/API/authentication) for instructions.

- A BYOC collection -- if you don't have one, create one using our [web application](https://apps.sentinel-hub.com/dashboard/#/byoc) or [API](https://docs.sentinel-hub.com/api/latest/reference/?service=byoc).

- The AWS credentials with access to your bucket -- Get them from the AWS console. These are only used to upload your data and read data that is registered in BYOC service.

- Your bucket configured so that Sentinel Hub can access data from it -- how to do this is documented [here](https://docs.sentinel-hub.com/api/latest/#/API/byoc?id=configuring-the-bucket). This is necessary because this tool and Sentinel Hub are separate.

- Imagery! (Of course)

## Basic setup

Provide the Sentinel Hub OAuth client id and client secret in the environment variables `SH_CLIENT_ID` and `SH_CLIENT_SECRET`.

Provide the AWS client credentials in the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` or by mounting the folder `~/.aws/credentials`.

You can bundle all variables in a text file, e.g. named env.txt:

```
SH_CLIENT_ID=<MySentinelHubClientId>
SH_CLIENT_SECRET=<MySentinelHubClientSecret>
AWS_ACCESS_KEY_ID=<MyAwsAccessKeyId>
AWS_SECRET_ACCESS_KEY=<MyAwsSecretAccessKey>
```

and pass it to Docker: `docker run --env-file env.txt sentinelhub/byoc-tool <Arguments...>`

## Basic Commands

For a list of commands run: `docker run sentinelhub/byoc-tool --help`

For a list of ingestion parameters run: `docker run sentinelhub/byoc-tool ingest --help`

To ingest files, you need to mount the imagery folder inside Docker, for example: `-v <MyFolder>:/folder` to mount the folder `<MyFolder>` to `/folder` inside Docker.

Then you give the tool the BYOC collection id `<MyCollectionId>` you wish to import to and the path to the folder inside the Docker (in this case `/folder`):

The basic import command (see the next chapter for details) is thus: `docker run --env-file env.txt -v <MyFolder>:/folder sentinelhub/byoc-tool ingest <MyCollectionId> /folder`


## The Simple Default Case

The tool offers parameters which will allow tuning for various folder/file structures. The default case, which needs no additional parameters is as follows:
By default, the tool takes the input folder and looks for folders inside which have tiff or jp2 images. In this case, each such folder found represents a tile and each file represents a band. For example, if you have files at the following locations:

- folder/
  - tile_1/
    - B01.tif
    - B02.tif
  - tile_2/
    - B01.tif
    - B02.tif

with `folder/` as the input path, the tool would ingest 2 tiles with names `tile_1` and `tile_2`, and each tile would have two bands named `B01` and `B02`. By default, band names equal the file names without file extensions.

The command will prepare Cloud Optimized GeoTIFFs and upload them to S3 bucket associated with the BYOC collection. Finally, it will register tiles in your BYOC collection. The file `/folder/tile_1/B01.tiff` will be uploaded to`s3://<MyBucket>/tile_1/B01.tiff`.

For more elaborate folder, tile, band structures, see the help of the `--file-pattern` and `--file-map` parameters.

Note that in this case the tile sensing time will not be set and that the tile coverage will not be traced (see the Tracing Coverage chapter).

## Advanced Example

The tool can be quite powerful with the right parameters. This example will attempt to showcase these without being too complicated.

Suppose in this case that the folder structure is as follows:

- folder/
  - tile_1/
    - DATA_and_sensing_time_1.tif
  - tile_2/
    - DATA_and_sensing_time_2.tif

In this case lets assume the DATA tiffs are three bands each, containing R,G,B bands.

To effectively use the tool in this case, the `--file-pattern` and `--file-map` parameters need to be used. The `--file pattern` in this case can look something like this: `(?<tile>.*)\/.*(?<year>[0-9]{4})(?<month>[0-9]{2})(?<day>[0-9]{2})T(?<hour>[0-9]{2})(?<minute>[0-9]{2})(?<second>[0-9]{02})`. This will find files with the defined sensing time structure and use the name of their parent folder as the tile name. This can be modified to support multiple files per folder or even files in different folders which together represent one tile.

The `--file-map` parameter allows all bands from the tiff file to be used. In this case since there is only one file per tile only one is needed and it can look something like this: `.*tif;1:R;2:G;3:B`. In words: From a .tif file extract band 1 and name it R, extract band 2 and name it G, extract band 3 and name it B.

To remember: `--file-pattern` finds files using a regular expression. Files with an equal `tile` capture group value are grouped into that one tile. The `--file-map` pattern is then applied to each file within that tile. You can define as many `--file-map` parameters as are files in a tile so that each file can be mapped.


## Tracing Coverage

Information about what coverage tracing is and why it is important is available [here](https://docs.sentinel-hub.com/api/latest/#/API/byoc?id=a-note-about-cover-geometries).

To enable geometry tracing set the flag `--trace-coverage`. See `--distance-tolerance` and `--negative-buffer` for tuning parameters. If not set, the cover geometry will equal the image bounding box.

To speed up tracing, you can trace coverage from one of image overviews. For example, to trace coverage from the first overview, set the flag `--trace-image-idx 1`.

## Building a docker image

```
./gradlew build
docker build -t byoc-tool .
```

## Building an executable

Download OpenJDK 14.

Set JPACKAGE_HOME and JLINK_HOME to OpenJDK 14 location.

Run `gradlew jpackage`

The executable will be located in the project root.
