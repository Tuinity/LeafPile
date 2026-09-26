package ca.spottedleaf.ioutil.stream.wrapped;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferInputStream;

public final class SimpleBufferInputStream extends AbstractBufferInputStream {

    public SimpleBufferInputStream(final Buffer readBuffer) {
        super(readBuffer);
    }

    @Override
    protected boolean tryFillReadBuffer() {
        return false;
    }
}
