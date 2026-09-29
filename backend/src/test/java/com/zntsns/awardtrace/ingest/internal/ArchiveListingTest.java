package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.ingest.internal.ArchiveListing.ArchiveFile;
import com.zntsns.awardtrace.ingest.internal.ArchiveListing.Kind;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ArchiveListingTest {

    private final FakeArchive archive = new FakeArchive()
            .put("FY(All)_012_Contracts_Delta_20260906.zip", new byte[0])
            .put("FY(All)_097_Contracts_Delta_20260906.zip", new byte[0])
            .put("FY2026_012_Assistance_Full_20260906.zip", new byte[0])
            .put("FY2026_012_Contracts_Full_20260906.zip", new byte[0])
            .put("FY2026_097_Contracts_Full_20260906.zip", new byte[0])
            .put("FY2026_All_Contracts_Full_20260906.zip", new byte[0]);

    private final ArchiveListing listing = new ArchiveListing(new IngestProperties(archive.url(), Set.of(), null));

    @AfterEach
    void stopArchive() {
        archive.stop();
    }

    @Test
    void listsFullFilesPerAgencyAcrossPages() throws Exception {
        archive.pageSize(1);

        var files = listing.contractFiles("FY2026_", Kind.Full, Set.of());

        assertThat(files).extracting(ArchiveFile::name)
                .containsExactly("FY2026_012_Contracts_Full_20260906.zip", "FY2026_097_Contracts_Full_20260906.zip");
        assertThat(files.getFirst().agency()).isEqualTo("012");
        assertThat(files.getFirst().fileDate()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(files.getFirst().url()).isEqualTo(archive.url().resolve("FY2026_012_Contracts_Full_20260906.zip"));
    }

    @Test
    void keepsOnlyTheConfiguredAgencies() throws Exception {
        var files = listing.contractFiles("FY(All)_", Kind.Delta, Set.of("012"));

        assertThat(files).extracting(ArchiveFile::name).containsExactly("FY(All)_012_Contracts_Delta_20260906.zip");
    }
}
