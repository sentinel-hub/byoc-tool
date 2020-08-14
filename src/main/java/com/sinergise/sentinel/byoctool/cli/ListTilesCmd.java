package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Iterator;

@Command(name = "ls", description = "List collection tiles")
public class ListTilesCmd implements Runnable {

  @Parameters(index = "0", description = "Collection id")
  private String collectionId;

  @ParentCommand private ByocTool parent;

  @Override
  public void run() {
    ByocClient byocClient = parent.newByocClient(collectionId);

    Iterator<ByocTile> it = byocClient.getTileIterator(collectionId);
    while (it.hasNext()) {
      ByocTile tile = it.next();

      System.out.println(tile.idWithPath());
    }
  }
}
