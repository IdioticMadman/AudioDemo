package com.robert.audiodemo.plugin;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;

/**
 * 阻塞可读性InputStream，当不可读时将阻塞到有数据可读
 */
public class BlockingAvailableInputStream extends InputStream {

    private final InputStream mInputStream;

    public BlockingAvailableInputStream(InputStream inputStream) {
        mInputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return mInputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return mInputStream.read(b);
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        return mInputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return mInputStream.skip(n);
    }

    @Override
    public void close() throws IOException {
        mInputStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        mInputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return mInputStream.markSupported();
    }

    @Override
    public int available() throws IOException {
        do {
            int available = mInputStream.available();
            if (available == 0) {
                //可读数据为零的时候，让出cpu执行
                Thread.yield();
            } else {
                //返回有数据可读，或者-1停止
                return available;
            }
        } while (true);
    }
}
