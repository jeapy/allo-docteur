package dev.jpzconception.allodocteur.professional.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentServiceTest {

    @Test
    void detectsPdfJpegPngBySignature() {
        assertThat(DocumentService.detectContentType("%PDF-1.7 ...".getBytes())).isEqualTo("application/pdf");
        assertThat(DocumentService.detectContentType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isEqualTo("image/jpeg");
        assertThat(DocumentService.detectContentType(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo("image/png");
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(DocumentService.detectContentType("<html>".getBytes())).isNull();
        assertThat(DocumentService.detectContentType(new byte[0])).isNull();
        assertThat(DocumentService.detectContentType("MZ\u0090\u0000".getBytes())).isNull();
    }

    @Test
    void sanitizesFilenames() {
        assertThat(DocumentService.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitize("C:\\dir\\diplôme.pdf")).isEqualTo("diplôme.pdf");
        assertThat(DocumentService.sanitize("a<b>|c.pdf")).isEqualTo("a_b__c.pdf");
        assertThat(DocumentService.sanitize(null)).isEqualTo("document");
        assertThat(DocumentService.sanitize("   ")).isEqualTo("document");
    }
}
