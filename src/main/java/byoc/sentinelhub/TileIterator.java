package byoc.sentinelhub;

import byoc.sentinelhub.models.ByocTile;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class TileIterator implements Iterator<ByocTile> {

  private final ByocClient byocClient;
  private final String collectionId;
  private final List<String> tileIds;

  TileIterator(ByocClient byocClient, String collectionId, List<String> tileIds) {
    this.byocClient = byocClient;
    this.collectionId = collectionId;
    this.tileIds = new LinkedList<>(tileIds);
  }

  @Override
  public boolean hasNext() {
    return !tileIds.isEmpty();
  }

  @Override
  public ByocTile next() {
    return fetchTile(tileIds.remove(0));
  }

  private ByocTile fetchTile(String tileId) {
    return byocClient.getTile(collectionId, tileId);
  }
}
