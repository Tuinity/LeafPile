package ca.spottedleaf.ioutil.util.zstd;

import ca.spottedleaf.ioutil.util.CompressionTestSupport;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;

public final class ZstdCompressionTest {

    @Test
    public void testZSTDNonNative() throws Exception {
        CompressionTestSupport.runTests(false, false,
                ZstdCompressionUtil::compress, ZstdCompressionUtil::decompress, ZstdException.class);
    }

    @Test
    public void testZSTDSrcNative() throws Exception {
        CompressionTestSupport.runTests(true, false,
                ZstdCompressionUtil::compress, ZstdCompressionUtil::decompress, ZstdException.class);
    }

    @Test
    public void testZSTDDstNative() throws Exception {
        CompressionTestSupport.runTests(false, true,
                ZstdCompressionUtil::compress, ZstdCompressionUtil::decompress, ZstdException.class);
    }

    @Test
    public void testZSTDNative() throws Exception {
        CompressionTestSupport.runTests(true, true,
                ZstdCompressionUtil::compress, ZstdCompressionUtil::decompress, ZstdException.class);
    }
}
