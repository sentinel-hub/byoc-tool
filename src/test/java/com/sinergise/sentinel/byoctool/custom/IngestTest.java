package com.sinergise.sentinel.byoctool.custom;

import com.sinergise.sentinel.byoctool.ByocTool;

class IngestTest {

  public static void main(String[] args) {
    ByocTool.main(
        "ingest",
        "--processing-folder=C:\\Users\\tslijepcevic\\data\\byoc workshop\\tmp",
        "--file-pattern=(?<tile>.*?)\\\\.*_(?<year>[0-9]{4})(?<month>[0-9]{2})(?<day>[0-9]{2})T(?<hour>[0-9]{2})(?<minute>[0-9]{2})(?<second>[0-9]{02}).*10m\\.jp2",
        "--file-map=B02;1:B02",
        //                "--file-map=B03;1:B03",
        //                "--file-map=B04;1:B04",
        //        "--trace-coverage",
        "--no-data=0",
        //        "--distance-tolerance=10",
        "--num-threads=4",
        "e0f19e2f-e7fe-4c11-92c6-bd6fc957643d",
        "C:\\Users\\tslijepcevic\\data\\byoc workshop\\sentinel2");
  }
}
