package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;

import java.util.*;

public class ByocClientStub implements ByocClient {

  private final Map<String, ByocCollection> collections = new HashMap<>();
  private final Map<String, ByocTile> tiles = new HashMap<>();

  public void addCollection(ByocCollection collection) {
    collections.put(collection.getId(), collection);
  }

  public void addTile(ByocTile tile) {
    tiles.put(tile.getId(), tile);
  }

  @Override
  public Optional<ByocCollection> getCollection(String collectionId) {
    return Optional.ofNullable(collections.get(collectionId));
  }

  @Override
  public Optional<ByocTile> getTile(String collectionId, String tileId) {
    return Optional.ofNullable(tiles.get(tileId));
  }

  @Override
  public Optional<ByocTile> searchTile(String collectionId, String path) {
    return tiles.values().stream()
        .filter(entry -> entry.getPath().equals(path))
        .findFirst();
  }

  @Override
  public Iterator<ByocTile> getTileIterator(String collectionId) {
    return tiles.values().iterator();
  }

  @Override
  public ByocCollection createCollection(ByocCollection collection) {
    collection.setId(UUID.randomUUID().toString());
    addCollection(collection);
    return collection;
  }

  @Override
  public ByocTile createTile(String collectionId, ByocTile tile) {
    tile.setId(UUID.randomUUID().toString());
    addTile(tile);
    return tile;
  }

  @Override
  public void updateTile(String collectionId, ByocTile tile) {
  }
}
