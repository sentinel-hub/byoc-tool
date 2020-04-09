package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
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
    Iterator<ByocTile> iter = parent.newByocClient().getTileIterator(collectionId);
    while (iter.hasNext()) {
      ByocTile tile = iter.next();

      System.out.println(tile.idWithPath());
    }
  }
}
