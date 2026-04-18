package io.github.levlandon.numai_plus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

class ChatAttachment {
    private final String type;
    private final String name;
    private final String mimeType;
    private final String uri;
    private final String data;

    ChatAttachment(String type, String name, String mimeType, String uri, String data) {
        this.type = type;
        this.name = name;
        this.mimeType = mimeType;
        this.uri = uri;
        this.data = data;
    }

    String getType() {
        return type;
    }

    String getName() {
        return name;
    }

    String getMimeType() {
        return mimeType;
    }

    String getUri() {
        return uri;
    }

    String getData() {
        return data;
    }

    boolean isImage() {
        return "image".equals(type);
    }

    String getPreviewSource() {
        if (uri != null && uri.length() != 0) {
            return uri;
        }
        return data;
    }

    String toDataUrl() {
        if (data != null && data.length() != 0) {
            return data;
        }
        if (uri == null || uri.length() == 0) {
            return null;
        }
        InputStream inputStream = null;
        try {
            File file = new File(uri);
            if (!file.exists()) {
                return null;
            }
            inputStream = new FileInputStream(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            String resolvedMimeType = mimeType != null && mimeType.length() != 0 ? mimeType : "image/jpeg";
            return "data:" + resolvedMimeType + ";base64," + Base64.encode(outputStream.toByteArray());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
