package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        .path("goodPath")
        .inputFiles(Collections.emptyList())
        .build();

    Tile badTile = Tile.builder()
        .path("badPath")
        .inputFiles(Collections.emptyList())
        .build();

    ByocClientStub byocClient = new ByocClientStub() {

      @Override
      public ByocTile createTile(String collectionId, ByocTile tile) {
        if (tile.getPath().equals(badTile.path())) {
          throw new IngestionException("something happened");
        }

        return super.createTile(collectionId, tile);
      }
    };

    ByocCollection collection = new ByocCollection();
    collection.setId("collectionId");
    byocClient.addCollection(collection);

    List<Tile> ingestedTiles = new LinkedList<>();

    ByocIngestor ingestor = new ByocIngestor(byocClient, new TestStorageClient());
    ingestor.setOnTileIngested(ingestedTiles::add);

    Collection<String> tileIds = ingestor.ingest(collection.getId(), Arrays.asList(badTile, goodTile));

    assertEquals(1, new LinkedList<>(tileIds).size());
    assertEquals(1, ingestedTiles.size());
    assertEquals(goodTile, ingestedTiles.get(0));
  }
}
