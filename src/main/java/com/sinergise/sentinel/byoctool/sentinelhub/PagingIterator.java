package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocPage;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

class PagingIterator implements Iterator<ByocTile> {

  private final Client httpClient;
  private List<ByocTile> tiles;
  private URI nextUrl;

  PagingIterator(URI firstPageUrl, Client httpClient) {
    this.httpClient = httpClient;
    fetchPage(firstPageUrl);
  }

  private void fetchPage(URI uri) {
    Response response = httpClient.target(uri).request().get();
    ByocPage<ByocTile> page = response.readEntity(new GenericType<ByocPage<ByocTile>>() {});
    tiles = page.getData();
    nextUrl = page.getLinks().getNext();
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
