package com.rag.config;

import com.rag.loader.PdfLoader;
import com.rag.loader.PlainTextLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public PlainTextLoader plainTextLoader(RagProperties props) {
        return new PlainTextLoader(props.toLoaderConfig());
    }

    @Bean
    public PdfLoader pdfLoader(RagProperties props) {
        return new PdfLoader(props.toLoaderConfig());
    }
}
