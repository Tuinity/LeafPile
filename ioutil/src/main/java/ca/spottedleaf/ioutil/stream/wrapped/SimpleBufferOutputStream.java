package ca.spottedleaf.ioutil.stream.wrapped;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;

public final class SimpleBufferOutputStream extends AbstractBufferOutputStream {

    public SimpleBufferOutputStream(final Buffer writeBuffer) {
        super(writeBuffer);
    }

    @Override
    protected boolean tryFlushWriteBuffer() {
        return false;
    }
}
