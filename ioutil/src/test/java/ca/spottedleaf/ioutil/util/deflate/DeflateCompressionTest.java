package ca.spottedleaf.ioutil.util.deflate;

import ca.spottedleaf.ioutil.util.CompressionTestSupport;
import java.util.zip.DataFormatException;
import org.junit.jupiter.api.Test;

public final class DeflateCompressionTest {

    @Test
    public void testDeflateNonNative() throws Exception {
        CompressionTestSupport.runTests(false, false,
                DeflateCompressionUtil::compress, DeflateCompressionUtil::decompress, DataFormatException.class);
    }

    @Test
    public void testDeflateSrcNative() throws Exception {
        CompressionTestSupport.runTests(true, false,
                DeflateCompressionUtil::compress, DeflateCompressionUtil::decompress, DataFormatException.class);
    }

    @Test
    public void testDeflateDstNative() throws Exception {
        CompressionTestSupport.runTests(false, true,
                DeflateCompressionUtil::compress, DeflateCompressionUtil::decompress, DataFormatException.class);
    }

    @Test
    public void testDeflateNative() throws Exception {
        CompressionTestSupport.runTests(true, true,
                DeflateCompressionUtil::compress, DeflateCompressionUtil::decompress, DataFormatException.class);
    }
}
