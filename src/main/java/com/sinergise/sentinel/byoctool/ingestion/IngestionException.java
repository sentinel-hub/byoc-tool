package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import lombok.Getter;

import java.util.Collection;

@Getter
public class IngestionException extends RuntimeException {

  public IngestionException(String message) {
    super(message);
  }

  IngestionException(Tile tile, Collection<String> errors) {
    super(createMessage(tile, errors));
  }

  private static String createMessage(Tile tile, Collection<String> errors) {
    return String.format("Tile with path %s has following errors: %s",
        tile.path(), String.join(" ", errors));
  }
}
