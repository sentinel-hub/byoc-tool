package com.sinergise.sentinel.byoctool.sentinelhub;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ByocDeployment {
  AWS_EU_CENTRAL_1("https://services.sentinel-hub.com/api/v1/byoc"),
  AWS_US_WEST_2("https://services-uswest2.sentinel-hub.com/api/v1/byoc");

  @Getter private final String serviceUrl;
}
