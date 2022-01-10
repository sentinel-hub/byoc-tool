package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.IngestionResult;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IngestorTest {

  @Test
  void triggerCallbacks() {
    ByocClientStub byocClient = new ByocClientStub();

    ByocCollection collection = new ByocCollection();
    collection.setId("collectionId");
    byocClient.addCollection(collection);

    ByocIngestor ingestor = new ByocIngestor(byocClient, new TestStorageClient());

    Tile tile = Tile.builder()
        .path("tilePath")
        .inputFiles(Collections.emptyList())
        .build();

    AtomicReference<Tile> tileOnIngestionStart = new AtomicReference<>();
    AtomicReference<Tile> tileOnIngestionEnd = new AtomicReference<>();
    AtomicReference<Tile> tileOnIngestionSuccess = new AtomicReference<>();
    AtomicReference<String> step = new AtomicReference<>();

    ingestor.setOnTileIngestionStarted(t -> {
      tileOnIngestionStart.set(t);
      step.compareAndSet(null, "started");
    });
    ingestor.setOnTileIngested(t -> {
      tileOnIngestionSuccess.set(t);
      step.compareAndSet("started", "ingested");
    });
    ingestor.setOnTileIngestionEnded(t -> {
      tileOnIngestionEnd.set(t);
      step.compareAndSet("ingested", "ended");
    });

    ingestor.ingest(collection.getId(), Collections.singletonList(tile));

    assertEquals("ended", step.get());
    assertEquals(tile, tileOnIngestionStart.get());
    assertEquals(tile, tileOnIngestionEnd.get());
    assertEquals(tile, tileOnIngestionSuccess.get());
  }

  @Test
  void onIngestionException() {
    Tile goodTile = Tile.builder()
        .path("path1")
        .inputFiles(Collections.emptyList())
        .build();

    Tile presentTile = Tile.builder()
        .path("path2")
        .inputFiles(Collections.emptyList())
        .build();

    Tile badTile = Tile.builder()
        .path("path3")
        .inputFiles(Collections.emptyList())
        .build();

    ByocClientStub byocClient = new ByocClientStub() {

      @Override
      public ByocTile createTile(String collectionId, ByocTile tile) {
        if (tile.getPath().equals(badTile.path())) {
          throw new IngestionException("some error");
        }

        return super.createTile(collectionId, tile);
      }
    };

    ByocTile presentByocTile = new ByocTile();
    presentByocTile.setPath("path2/(BAND).tiff");
    byocClient.addTile(presentByocTile);

    ByocCollection collection = new ByocCollection();
    collection.setId("collectionId");
    byocClient.addCollection(collection);

    List<Tile> ingestedTiles = new LinkedList<>();

    ByocIngestor ingestor = new ByocIngestor(byocClient, new TestStorageClient());
    ingestor.setOnTileIngested(ingestedTiles::add);

    List<IngestionResult> results = ingestor.ingest(collection.getId(),
        Arrays.asList(badTile, goodTile, presentTile));

    assertEquals(1, ingestedTiles.size());
    assertEquals(goodTile, ingestedTiles.get(0));
    assertEquals(3, results.size());

    IngestionResult result = findTileWithPath(results, goodTile).get();
    assertTrue(result.isTileCreated());
    assertNotNull(result.getTileId());
    assertNull(result.getErrors());
    assertNull(result.getWarnings());

    result = findTileWithPath(results, badTile).get();
    assertNull(result.getTileId());
    assertEquals("some error", result.getErrors());
    assertNull(result.getWarnings());

    result = findTileWithPath(results, presentTile).get();
    assertFalse(result.isTileCreated());
    assertNull(result.getTileId());
    assertNull(result.getErrors());
    assertTrue(result.getWarnings().contains("already exist"));
  }

  private Optional<IngestionResult> findTileWithPath(List<IngestionResult> results, Tile tile) {
    return results.stream()
        .filter(result -> result.getTile().equals(tile))
        .findFirst();
  }
}
