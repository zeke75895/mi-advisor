package com.miadvisor.notes;

import com.miadvisor.common.BadRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Pulls selectable text out of a PDF. Scanned (image-only) PDFs have none and are rejected. */
@Component
public class PdfTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public String extract(byte[] bytes) {
        if (!startsWith(bytes, PDF_MAGIC)) {
            throw new BadRequestException("That file isn't a PDF.");
        }
        String text;
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            text = new PDFTextStripper().getText(doc);
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("That PDF is password-protected. Remove the password and try again.");
        } catch (IOException e) {
            throw new BadRequestException("That PDF couldn't be read. It may be damaged.");
        }
        String cleaned = text.replace('\u0000', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        if (cleaned.isEmpty()) {
            throw new BadRequestException(
                    "No text found in that PDF. It may be a scanned image; try a PDF with selectable text.");
        }
        return cleaned;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
