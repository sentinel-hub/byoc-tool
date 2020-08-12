package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.sentinelhub.AuthClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocInfoClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import java.util.Iterator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "ls", description = "List collection tiles")
public class ListTilesCmd implements Runnable {

  @Parameters(index = "0", description = "Collection id")
  private String collectionId;

  @ParentCommand private ByocTool parent;

  @Override
  public void run() {
    AuthClient authClient = parent.newAuthClient();

    ByocCollectionInfo collectionInfo =
        new ByocInfoClient(authClient)
            .getCollectionInfo(collectionId)
            .orElseThrow(() -> new RuntimeException("Collection not found."));

    ByocClient byocClient = new ByocClient(authClient, collectionInfo.getDeployment());

    Iterator<ByocTile> it = byocClient.getTileIterator(collectionId);
    while (it.hasNext()) {
      ByocTile tile = it.next();

      System.out.println(tile.idWithPath());
    }
  }
}
