// copied from https://github.com/killme2008/xmemcached/tree/90dd456f29/src/main/java/net/rubyeye/xmemcached/transcoders
package org.eclipse.jetty.redis.session.transcoders;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Base class for any transcoders that may want to work with serialized or
 * compressed data.
 */
public abstract class BaseSerializingTranscoder {

    /**
     * Default compression threshold value.
     */
    public static final int DEFAULT_COMPRESSION_THRESHOLD = 16384;

    public static final String DEFAULT_CHARSET = "UTF-8";

    protected int compressionThreshold = DEFAULT_COMPRESSION_THRESHOLD;
    protected String charset = DEFAULT_CHARSET;
    protected CompressionMode compressMode = CompressionMode.GZIP;
    protected static final Logger log = Log.getLogger(BaseSerializingTranscoder.class);

    /**
     * Set the compression threshold to the given number of bytes. This
     * transcoder will attempt to compress any data being stored that's larger
     * than this.
     *
     * @param to the number of bytes
     */
    public void setCompressionThreshold(int to) {
        this.compressionThreshold = to;
    }

    public CompressionMode getCompressMode() {
        return compressMode;
    }

    public void setCompressionMode(CompressionMode compressMode) {
        this.compressMode = compressMode;
    }

    /**
     * Set the character set for string value transcoding (defaults to UTF-8).
     */
    public void setCharset(String to) {
        // Validate the character set.
        try {
            new String(new byte[97], to);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        this.charset = to;
    }

    /**
     * Get the bytes representing the given serialized object.
     */
    protected byte[] serialize(Object o) {
        if (o == null) {
            throw new NullPointerException("Can't serialize null");
        }
        byte[] rv = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bos);
            os.writeObject(o);
            os.close();
            bos.close();
            rv = bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Non-serializable object", e);
        }
        return rv;
    }

    /**
     * Get the object represented by the given serialized bytes.
     */
    protected Object deserialize(byte[] in) {
        Object rv = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream is = null;
        try {
            if (in != null) {
                bis = new ByteArrayInputStream(in);
                is = new ClassLoadingObjectInputStream(bis);
                rv = is.readObject();

            }
        } catch (IOException e) {
            log.warn("Caught IOException decoding {} bytes of data", in.length, e);
        } catch (ClassNotFoundException e) {
            log.warn("Caught CNFE decoding {} bytes of data", in.length, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return rv;
    }

    /**
     * Compress the given array of bytes.
     */
    public final byte[] compress(byte[] in) {
        switch (this.compressMode) {
            case GZIP:
                return gzipCompress(in);
            case ZIP:
                return zipCompress(in);
            default:
                return gzipCompress(in);
        }

    }

    private byte[] zipCompress(byte[] in) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(in.length);
        DeflaterOutputStream os = new DeflaterOutputStream(baos);
        try {
            os.write(in);
            os.finish();
            try {
                os.close();
            } catch (IOException e) {
                log.warn("Close DeflaterOutputStream error", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("IO exception compressing data", e);
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                log.warn("Close ByteArrayOutputStream error", e);
            }
        }
        return baos.toByteArray();
    }

    private static byte[] gzipCompress(byte[] in) {
        if (in == null) {
            throw new NullPointerException("Can't compress null");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gz = null;
        try {
            gz = new GZIPOutputStream(bos);
            gz.write(in);
        } catch (IOException e) {
            throw new RuntimeException("IO exception compressing data", e);
        } finally {
            if (gz != null) {
                try {
                    gz.close();
                } catch (IOException e) {
                    log.warn("Close GZIPOutputStream error", e);
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    log.warn("Close ByteArrayOutputStream error", e);
                }
            }
        }
        byte[] rv = bos.toByteArray();
        // log.debug("Compressed %d bytes to %d", in.length, rv.length);
        return rv;
    }

    static int COMPRESS_RATIO = 8;

    /**
     * Decompress the given array of bytes.
     *
     * @return null if the bytes cannot be decompressed
     */
    protected byte[] decompress(byte[] in) {
        switch (this.compressMode) {
            case GZIP:
                return gzipDecompress(in);
            case ZIP:
                return zipDecompress(in);
            default:
                return gzipDecompress(in);
        }
    }

    private byte[] zipDecompress(byte[] in) {
        int size = in.length * COMPRESS_RATIO;
        ByteArrayInputStream bais = new ByteArrayInputStream(in);
        InflaterInputStream is = new InflaterInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
        try {
            byte[] uncompressMessage = new byte[size];
            while (true) {
                int len = is.read(uncompressMessage);
                if (len <= 0) {
                    break;
                }
                baos.write(uncompressMessage, 0, len);
            }
            baos.flush();
            return baos.toByteArray();

        } catch (IOException e) {
            log.warn("Failed to decompress data", e);
            // baos = null;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                log.warn("failed to close InflaterInputStream");
            }
            try {
                bais.close();
            } catch (IOException e) {
                log.warn("failed to close ByteArrayInputStream");
            }
            try {
                baos.close();
            } catch (IOException e) {
                log.warn("failed to close ByteArrayOutputStream");
            }
        }
        return baos == null ? null : baos.toByteArray();
    }

    private byte[] gzipDecompress(byte[] in) {
        ByteArrayOutputStream bos = null;
        if (in != null) {
            ByteArrayInputStream bis = new ByteArrayInputStream(in);
            bos = new ByteArrayOutputStream();
            GZIPInputStream gis = null;
            try {
                gis = new GZIPInputStream(bis);

                byte[] buf = new byte[64 * 1024];
                int r = -1;
                while ((r = gis.read(buf)) > 0) {
                    bos.write(buf, 0, r);
                }
            } catch (IOException e) {
                log.warn("Failed to decompress data", e);
                bos = null;
            } finally {
                if (gis != null) {
                    try {
                        gis.close();
                    } catch (IOException e) {
                        log.warn("Close GZIPInputStream error", e);
                    }
                }
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        log.warn("Close ByteArrayInputStream error", e);
                    }
                }
            }
        }
        return bos == null ? null : bos.toByteArray();
    }

    /**
     * Decode the string with the current character set.
     */
    protected String decodeString(byte[] data) {
        String rv = null;
        try {
            if (data != null) {
                rv = new String(data, this.charset);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return rv;
    }

    /**
     * Encode a string into the current character set.
     */
    protected byte[] encodeString(String in) {
        byte[] rv = null;
        try {
            rv = in.getBytes(this.charset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return rv;
    }

}
