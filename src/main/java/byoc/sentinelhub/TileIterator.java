package byoc.sentinelhub;

import byoc.sentinelhub.models.ByocTile;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class TileIterator implements Iterator<ByocTile> {

  private final ByocService byocService;
  private final String collectionId;
  private final List<String> tileIds;

  TileIterator(ByocService byocService, String collectionId, List<String> tileIds) {
    this.byocService = byocService;
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
    return byocService.getTile(collectionId, tileId);
  }
}
