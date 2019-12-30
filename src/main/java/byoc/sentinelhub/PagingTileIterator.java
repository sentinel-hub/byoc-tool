package byoc.sentinelhub;

import byoc.sentinelhub.models.ByocTile;
import byoc.sentinelhub.models.ByocTilesPage;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.core.Response;

class PagingTileIterator implements Iterator<ByocTile> {

  private final ByocService byocService;
  private final String collectionId;

  private List<ByocTile> tiles;
  private URI nextUri;

  PagingTileIterator(ByocService byocService, String collectionId) {
    this.byocService = byocService;
    this.collectionId = collectionId;
    fetchPage(getFirstPageUri());
  }

  private URI getFirstPageUri() {
    return byocService.getWebTarget().path("collections").path(collectionId).path("tiles").getUri();
  }

  private void fetchPage(URI uri) {
    Response response = byocService.getHttpClient().target(uri).request().get();
    ByocTilesPage page = response.readEntity(ByocTilesPage.class);
    tiles = page.getData();
    nextUri = page.getLinks().getNext();
  }

  @Override
  public boolean hasNext() {
    return !tiles.isEmpty() || nextUri != null;
  }

  @Override
  public ByocTile next() {
    if (tiles.isEmpty()) {
      fetchPage(nextUri);
    }
    return tiles.remove(0);
  }
}
