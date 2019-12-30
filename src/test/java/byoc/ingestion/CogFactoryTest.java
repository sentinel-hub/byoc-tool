package byoc.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import byoc.tiff.TiffDirectory.SampleFormat;
import org.junit.jupiter.api.Test;

class CogFactoryTest {

  @Test
  void predictor() {
    assertEquals(2, CogFactory.getPredictor(SampleFormat.INT));
    assertEquals(2, CogFactory.getPredictor(SampleFormat.UINT));
    assertEquals(3, CogFactory.getPredictor(SampleFormat.IEEEFP));
  }
}
