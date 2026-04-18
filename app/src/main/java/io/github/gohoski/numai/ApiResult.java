package io.github.gohoski.numai;

import java.io.InputStream;

/**
 * Created by Gleb on 11.10.2025.
 * class to get the selected model + InputStream
 * it is not recommended to display the model from the response json since
 * it usually contains unnecessary prefixes, which is why this exists !
 */

class ApiResult {
    private final String model;
    private final InputStream result;
    private final boolean streaming;

    ApiResult(String model, InputStream result, boolean streaming) {
        this.model = model;
        this.result = result;
        this.streaming = streaming;
    }

    String getModel() {
        return model;
    }

    InputStream getResult() {
        return result;
    }

    boolean isStreaming() {
        return streaming;
    }
}
