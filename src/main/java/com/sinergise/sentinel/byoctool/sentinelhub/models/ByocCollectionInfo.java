package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocDeployment;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.regions.Region;

@Data
public class ByocCollectionInfo {

  @JsonProperty("id")
  private String id;

  @JsonProperty("s3Bucket")
  private String s3Bucket;

  @JsonProperty("location")
  private String location;

  public Region getS3Region() {
    final Region region;

    switch (location) {
      case "aws-eu-central-1":
        region = Region.EU_CENTRAL_1;
        break;
      case "aws-us-west-2":
        region = Region.US_WEST_2;
        break;
      default:
        throw new RuntimeException("Unexpected location " + location);
    }

    return region;
  }

  public ByocDeployment getDeployment() {
    switch (location) {
      case "aws-eu-central-1":
        return ByocDeployment.AWS_EU_CENTRAL_1;
      case "aws-us-west-2":
        return ByocDeployment.AWS_US_WEST_2;
      default:
        throw new RuntimeException("Unexpected location " + location);
    }
  }
}
