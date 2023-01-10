# Tool for Sentinel Hub BYOC service

The Sentinel Hub BYOC Tool is software which can be used to prepare your data for use in Sentinel Hub in a few simple steps. It is simple to use for simple cases but is also highly configurable allowing for more complex requirements.

The tool converts your TIFF and JP2 files to supported Cloud Optimized GeoTIFFs, uploads them to you AWS S3 bucket and registers them in the Sentinel Hub BYOC service. When complete, your data becomes visible in Sentinel Hub. The same steps can be done manually and are detailed in our [documentation](https://docs.sentinel-hub.com/api/latest/#/API/byoc).

The tool can be run either in Docker or as Java JAR. Instructions on how to install the tool are [here](#installation-instructions).

The tool works only with AWS S3 buckets in EU-Central-1 and US-West-2 region.

## Prerequisites

Before using the tool make sure you have the following:

- A Sentinel Hub OAuth client -- for communicating with Sentinel Hub. If you don't have one, create one in your [dashboard](https://apps.sentinel-hub.com/dashboard/#/account/settings). Click [here](https://docs.sentinel-hub.com/api/latest/api/overview/authentication/#registering-oauth-client) for detailed instructions.

- A BYOC collection -- the collection data will be added to. If you don't have one, create one using your [dashboard](https://apps.sentinel-hub.com/dashboard/#/collections) or use the [API](https://docs.sentinel-hub.com/api/latest/reference/?service=byoc#tag/byoc_collection).

- An AWS bucket which is configured so that Sentinel Hub can access data from it -- how to do this is documented [here](https://docs.sentinel-hub.com/api/latest/api/byoc/#aws-bucket-settings). While this is not strictly speaking necessary for the BYOC tool to function, for the data to work on Sentinel Hub it needs read access to the data.

- The AWS credentials with access to your bucket -- Get them from your AWS console. These are only used to upload to or read your data from your bucket (reading may happen during more advanced usage of the tool). These credentials are not shared with Sentinel Hub or any other service and never leave your computer.

- Imagery (of course)

Depending on the installation type, the following are also required:

Java install:

- Java 8 or newer installed on your computer
- GDAL (https://gdal.org/) installed, at least v2.3.0, but it is highly recommend that you install a newer version of GDAL (v3.1 or newer), as these versions contain important fixes. Additionally, the `GDAL` and `GDAL_DATA` system environment variables must be set. 

Docker install:

- Docker installed on your computer

## Installation instructions

Clone/download the contents of this repository. Open a command prompt and navigate to the byoc-tool folder which contains the downloaded repository. Depending on your chosen installation type, run one of the following:

### Building a Docker image

Run `docker build --tag byoctool .`

### Building a Java JAR

Run gradle task "shadowJar":
- on Windows: `gradlew.bat shadowJar`
- on Linux/Unix: `./gradlew shadowJar`

The jar will be located in the folder `build/libs`. To run commands, make sure that the command prompt is navigated to this folder, alternatively provide the absolute path for the -jar parameter value.

Provide the Sentinel Hub OAuth client id and client secret in the environment variables `SH_CLIENT_ID` and `SH_CLIENT_SECRET`. How to set the environment variables depends on the operating system you have.

The AWS client credentials will be read from `~/.aws/credentials`, if present. If not, or you wish to override them, set the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` to the values required.

## Basic Commands

The following part of the readme is targeted towards the Java JAR install.

For a list of commands run: `java -jar byoc-tool.jar --help`

For a list of ingestion parameters run: `java -jar byoc-tool.jar ingest --help`

Give the tool the BYOC collection id `<MyCollectionId>` you wish to import to and the path to the folder containing imagery:

The basic import command (see the next chapter for details) is thus: `java -jar byoc-tool.jar ingest <MyCollectionId> <MyFolder>`

## The Simple Default Case

The tool offers parameters which will allow tuning for various folder/file structures. The default case, which needs no additional parameters is as follows:
By default, the tool takes the input folder and looks for folders inside which have tiff or jp2 images. In this case, each such folder found represents a tile and each file represents a band. For example, if you have files at the following locations:

- `<MyFolder>`/
  - tile_1/
    - B01.tif
    - B02.tif
  - tile_2/
    - B01.tif
    - B02.tif

with `folder/` as the input path, the tool would ingest 2 tiles with names `tile_1` and `tile_2`, and each tile would have two bands named `B01` and `B02`. By default, band names equal the file names without file extensions.

The command will prepare Cloud Optimized GeoTIFFs and upload them to the S3 bucket associated with the BYOC collection. Finally, it will register the tiles in your BYOC collection. The file `<MyFolder>/tile_1/B01.tiff` will be uploaded to`s3://<MyBucket>/tile_1/B01.tiff`.

For more elaborate folder, tile, band structures, see the help of the `--file-pattern` and `--file-map` parameters.

Note that in this case neither the tile sensing time nor the tile coverage will be set (see [Tracing Coverage](#tracing-coverage)).

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

Information about what coverage tracing is and why it is important is available [here](https://docs.sentinel-hub.com/api/latest/api/byoc/#a-note-about-cover-geometries).

To enable geometry tracing add the flag `--trace-coverage`. See `--distance-tolerance` and `--negative-buffer` for tuning parameters. If not set, the cover geometry will equal the image bounding box.

To speed up tracing, you can trace coverage from one of image overviews. For example, to trace coverage from the first overview, set the flag `--trace-image-idx 1`.

## S3 Multipart upload

You can enable multipart upload with the flag: `--multipart-upload`. This is recommended if your files are larger than 100MB or if you have an unstable internet connection.

To learn about it, check this page https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html, and if you decided to use it, it is highly recommended setting the bucket lifecycle policy for stopping incomplete multipart uploads https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuoverview.html#mpu-stop-incomplete-mpu-lifecycle-config. byoc-tool tries to stop incomplete uploads, if it has time to clean up, otherwise uploads remain active.  

