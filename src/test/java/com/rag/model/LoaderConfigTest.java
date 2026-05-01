package com.rag.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class LoaderConfigTest {

    @Test
    void defaults_areValid() {
        LoaderConfig cfg = LoaderConfig.defaults();
        assertThat(cfg.chunkSize()).isEqualTo(500);
        assertThat(cfg.chunkOverlap()).isEqualTo(50);
        assertThat(cfg.minChunkSize()).isEqualTo(20);
    }

    @Test
    void valid_config_constructsSuccessfully() {
        assertThatNoException().isThrownBy(() -> new LoaderConfig(100, 20, 10));
    }

    @Test
    void zero_chunkSize_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoaderConfig(0, 0, 0))
                .withMessageContaining("chunkSize");
    }

    @Test
    void negative_chunkSize_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoaderConfig(-1, 0, 0))
                .withMessageContaining("chunkSize");
    }

    @Test
    void negative_overlap_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoaderConfig(100, -1, 0))
                .withMessageContaining("chunkOverlap");
    }

    @Test
    void overlap_equal_to_chunkSize_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoaderConfig(100, 100, 0))
                .withMessageContaining("chunkOverlap");
    }

    @Test
    void overlap_greater_than_chunkSize_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoaderConfig(100, 150, 0))
                .withMessageContaining("chunkOverlap");
    }

    @Test
    void negative_minChunkSize_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoaderConfig(100, 10, -1))
                .withMessageContaining("minChunkSize");
    }

    @Test
    void zero_overlap_isAllowed() {
        assertThatNoException().isThrownBy(() -> new LoaderConfig(100, 0, 0));
    }

    @Test
    void zero_minChunkSize_isAllowed() {
        assertThatNoException().isThrownBy(() -> new LoaderConfig(100, 10, 0));
    }
}
