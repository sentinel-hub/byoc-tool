package com.sinergise.sentinel.byoctool.ingestion;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;

public class FileFinder extends SimpleFileVisitor<Path> {

  private final Path start;
  private final Pattern pattern;

  private final Collection<Match> matches = new LinkedList<>();

  private FileFinder(Path start, Pattern pattern) {
    this.start = start;
    this.pattern = pattern;
  }

  static Collection<Match> find(Path start, Pattern pattern) throws IOException {
    FileFinder visitor = new FileFinder(start, pattern);
    Files.walkFileTree(start, visitor);

    return visitor.matches;
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    Matcher matcher = pattern.matcher(start.relativize(file).toString());
    if (matcher.find()) {
      matches.add(new Match(file, matcher));
    }
    return FileVisitResult.CONTINUE;
  }

  @Value
  @Accessors(fluent = true)
  static class Match {

    private final Path file;
    private final Matcher matcher;
  }
}
