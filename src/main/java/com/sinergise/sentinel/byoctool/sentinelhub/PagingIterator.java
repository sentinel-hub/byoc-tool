package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocPage;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.sinergise.sentinel.byoctool.sentinelhub.ByocClient.getTilesPage;
import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.executeWithRetry;

class PagingIterator implements Iterator<ByocTile> {

  private final Client httpClient;
  private List<ByocTile> tiles;
  private URI nextUrl;

  PagingIterator(URI firstPageUrl, Client httpClient) {
    this.httpClient = httpClient;
    fetchPage(firstPageUrl);
  }

  private void fetchPage(URI uri) {
    Response response = executeWithRetry(
        "Fetching a page of tiles",
        () -> httpClient.target(uri).request().get());

    Optional<ByocPage<ByocTile>> page = getTilesPage(response);

    if (page.isPresent()) {
      tiles = page.get().getData();
      nextUrl = page.get().getLinks().getNext();
    } else {
      tiles = Collections.emptyList();
      nextUrl = null;
    }
  }

  @Override
  public boolean hasNext() {
    return !tiles.isEmpty() || nextUrl != null;
  }

  @Override
  public ByocTile next() {
    if (tiles.isEmpty()) {
      fetchPage(nextUrl);
    }
    return tiles.remove(0);
  }
}
