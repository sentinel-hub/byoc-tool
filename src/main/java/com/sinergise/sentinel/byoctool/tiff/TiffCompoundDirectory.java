package com.sinergise.sentinel.byoctool.tiff;

import com.twelvemonkeys.imageio.metadata.CompoundDirectory;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import java.io.IOException;
import javax.imageio.stream.ImageInputStream;

public class TiffCompoundDirectory extends TiffDirectory {

  private final CompoundDirectory compoundDirectory;

  public TiffCompoundDirectory(ImageInputStream iis) throws IOException {
    this((CompoundDirectory) new TIFFReader().read(iis));
  }

  private TiffCompoundDirectory(CompoundDirectory compoundDirectory) {
    super(compoundDirectory);
    this.compoundDirectory = compoundDirectory;
  }

  public TiffDirectory directory(int index) {
    return new TiffDirectory(compoundDirectory.getDirectory(index));
  }

  public int directoryCount() {
    return compoundDirectory.directoryCount();
  }
}
