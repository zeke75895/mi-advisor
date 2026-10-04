package com.miadvisor.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miadvisor.common.BadRequestException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsSelectableText() throws Exception {
        String text = extractor.extract(TestPdfs.withLines("BIO 181 Syllabus", "Week 3: Photosynthesis and the Calvin cycle"));

        assertThat(text).contains("BIO 181 Syllabus").contains("Calvin cycle");
    }

    @Test
    void rejectsNonPdfAndTextlessPdf() throws Exception {
        assertThatThrownBy(() -> extractor.extract("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("isn't a PDF");
        assertThatThrownBy(() -> extractor.extract(TestPdfs.withLines()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("scanned");
    }
}
