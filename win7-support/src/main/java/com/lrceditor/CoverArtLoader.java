package com.lrceditor;

import javafx.scene.image.Image;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 封面读取工具：
 *  1. 从 MP3 / FLAC 中提取内嵌封面
 *  2. 从音频同目录搜索常见图片命名（同名 / cover / folder / album / front / artwork）
 */
public class CoverArtLoader {

    /** 同目录搜索的候选基础名（大小写不敏感） */
    private static final String[] CANDIDATE_BASES = {
            "cover", "folder", "album", "front", "artwork", "albumart"
    };

    /** JavaFX 支持的图片扩展名（不含 webp） */
    private static final String[] IMAGE_EXTS = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp"
    };

    // ============================================================
    // 对外入口：依次尝试音频内嵌 → 目录图片
    // ============================================================
    public static Image loadFor(File audioFile, File reference) {
        // 1) 从音频文件提取内嵌封面
        if (audioFile != null) {
            Image emb = readEmbeddedCover(audioFile);
            if (emb != null) return emb;
        }
        // 2) 若参考文件不是音频，也试一下
        if (reference != null && !reference.equals(audioFile)) {
            Image emb = readEmbeddedCover(reference);
            if (emb != null) return emb;
        }
        // 3) 从参考文件目录找图片
        if (reference != null) {
            Image dirImg = readFolderCover(reference);
            if (dirImg != null) return dirImg;
        }
        // 4) 从音频目录找图片
        if (audioFile != null && !audioFile.equals(reference)) {
            Image dirImg = readFolderCover(audioFile);
            if (dirImg != null) return dirImg;
        }
        return null;
    }

    // ============================================================
    // 内嵌封面提取
    // ============================================================
    public static Image readEmbeddedCover(File f) {
        if (f == null || !f.exists()) return null;
        String name = f.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".mp3"))  return readMp3Cover(f);
            if (name.endsWith(".flac")) return readFlacCover(f);
        } catch (Exception ignored) {}
        return null;
    }

    /** 读取 MP3 ID3v2 APIC（v2.3/2.4）或 PIC（v2.2）封面 */
    private static Image readMp3Cover(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[10];
            if (raf.read(h) != 10) return null;
            if (h[0] != 'I' || h[1] != 'D' || h[2] != '3') return null;

            int major = h[3] & 0xFF;
            if (major < 2 || major > 4) return null;

            int headerSize = ((h[6] & 0x7F) << 21) | ((h[7] & 0x7F) << 14)
                    | ((h[8] & 0x7F) << 7)  | (h[9] & 0x7F);
            long end = 10L + headerSize;

            // 跳过 v2.3/2.4 扩展头
            boolean extended = (h[5] & 0x40) != 0;
            if (extended) {
                byte[] ext = new byte[4];
                if (raf.read(ext) != 4) return null;
                int extSize;
                if (major == 4) {
                    extSize = ((ext[0] & 0x7F) << 21) | ((ext[1] & 0x7F) << 14)
                            | ((ext[2] & 0x7F) << 7)  | (ext[3] & 0x7F);
                } else {
                    extSize = ((ext[0] & 0xFF) << 24) | ((ext[1] & 0xFF) << 16)
                            | ((ext[2] & 0xFF) << 8)  | (ext[3] & 0xFF);
                }
                raf.seek(raf.getFilePointer() + extSize);
            }

            int frameHeaderSize = (major == 2) ? 6 : 10;

            while (raf.getFilePointer() + frameHeaderSize <= end) {
                byte[] fh = new byte[frameHeaderSize];
                if (raf.read(fh) != frameHeaderSize) break;
                if (fh[0] == 0) break; // padding

                String frameId = new String(fh, 0, (major == 2) ? 3 : 4,
                                            StandardCharsets.ISO_8859_1);

                int frameSize;
                if (major == 2) {
                    frameSize = ((fh[3] & 0xFF) << 16) | ((fh[4] & 0xFF) << 8) | (fh[5] & 0xFF);
                } else if (major == 4) {
                    frameSize = ((fh[4] & 0x7F) << 21) | ((fh[5] & 0x7F) << 14)
                            | ((fh[6] & 0x7F) << 7)  | (fh[7] & 0x7F);
                } else {
                    frameSize = ((fh[4] & 0xFF) << 24) | ((fh[5] & 0xFF) << 16)
                            | ((fh[6] & 0xFF) << 8)  | (fh[7] & 0xFF);
                }
                if (frameSize <= 0) break;

                boolean isApic = "APIC".equals(frameId) || "PIC".equals(frameId);

                if (isApic) {
                    byte[] data = new byte[frameSize];
                    raf.readFully(data);
                    Image img = parseApic(data, major == 2);
                    if (img != null) return img;
                } else {
                    raf.seek(raf.getFilePointer() + frameSize);
                }
            }
        }
        return null;
    }

    private static Image parseApic(byte[] data, boolean v22) {
        try {
            int pos = 0;
            int encoding = data[pos++] & 0xFF;

            // MIME 串
            while (pos < data.length && data[pos] != 0) pos++;
            pos++;
            pos++; // picture type

            // description
            if (encoding == 1 || encoding == 2) {
                while (pos + 1 < data.length && !(data[pos] == 0 && data[pos + 1] == 0)) pos += 2;
                pos += 2;
            } else {
                while (pos < data.length && data[pos] != 0) pos++;
                pos++;
            }

            if (pos >= data.length) return null;
            byte[] imgBytes = new byte[data.length - pos];
            System.arraycopy(data, pos, imgBytes, 0, imgBytes.length);

            Image img = new Image(new ByteArrayInputStream(imgBytes));
            return (img.isError() || img.getWidth() <= 0) ? null : img;
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取 FLAC METADATA_BLOCK_PICTURE */
    private static Image readFlacCover(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] magic = new byte[4];
            if (raf.read(magic) != 4) return null;
            if (magic[0] != 'f' || magic[1] != 'L' || magic[2] != 'a' || magic[3] != 'C') return null;

            while (true) {
                int blockHeader = raf.read();
                if (blockHeader < 0) break;
                boolean last = (blockHeader & 0x80) != 0;
                int type = blockHeader & 0x7F;

                byte[] lenBytes = new byte[3];
                if (raf.read(lenBytes) != 3) return null;
                int blockLen = ((lenBytes[0] & 0xFF) << 16)
                        | ((lenBytes[1] & 0xFF) << 8)
                        | (lenBytes[2] & 0xFF);

                if (type == 6) {
                    byte[] block = new byte[blockLen];
                    raf.readFully(block);
                    Image img = parseFlacPicture(block);
                    if (img != null) return img;
                } else {
                    raf.seek(raf.getFilePointer() + blockLen);
                }

                if (last) break;
            }
        }
        return null;
    }

    private static Image parseFlacPicture(byte[] block) {
        try {
            int pos = 0;
            pos += 4;
            int mimeLen = readIntBE(block, pos); pos += 4;
            pos += mimeLen;
            int descLen = readIntBE(block, pos); pos += 4;
            pos += descLen;
            pos += 16; // width, height, depth, colors
            int dataLen = readIntBE(block, pos); pos += 4;

            if (pos + dataLen > block.length) return null;
            byte[] imgBytes = new byte[dataLen];
            System.arraycopy(block, pos, imgBytes, 0, dataLen);

            Image img = new Image(new ByteArrayInputStream(imgBytes));
            return (img.isError() || img.getWidth() <= 0) ? null : img;
        } catch (Exception e) {
            return null;
        }
    }

    private static int readIntBE(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24) | ((b[p+1] & 0xFF) << 16)
                | ((b[p+2] & 0xFF) << 8) | (b[p+3] & 0xFF);
    }

    // ============================================================
    // 目录图片搜索
    // ============================================================
    public static Image readFolderCover(File reference) {
        if (reference == null) return null;
        File dir = reference.getParentFile();
        if (dir == null || !dir.isDirectory()) return null;

        String base = reference.getName().replaceAll("\\.[^.]+$", "");

        // 1) 与参考文件同名的图片
        for (String ext : IMAGE_EXTS) {
            File img = findCaseInsensitive(dir, base + ext);
            if (img != null) {
                Image i = tryLoad(img);
                if (i != null) return i;
            }
        }

        // 2) cover / folder / album / front / artwork / albumart
        for (String candBase : CANDIDATE_BASES) {
            for (String ext : IMAGE_EXTS) {
                File img = findCaseInsensitive(dir, candBase + ext);
                if (img != null) {
                    Image i = tryLoad(img);
                    if (i != null) return i;
                }
            }
        }
        return null;
    }

    private static File findCaseInsensitive(File dir, String filename) {
        File exact = new File(dir, filename);
        if (exact.exists()) return exact;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(filename)) return f;
        }
        return null;
    }

    private static Image tryLoad(File img) {
        try {
            Image i = new Image(img.toURI().toString());
            if (i.isError() || i.getWidth() <= 0) return null;
            return i;
        } catch (Exception e) {
            return null;
        }
    }
}
