package com.zntsns.awardtrace.ingest.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/** Lists contract files in the USAspending award data archive, which is a public S3 bucket listing. */
@Component
@Profile("ingest")
class ArchiveListing {

    enum Kind { Full, Delta }

    record ArchiveFile(String name, URI url, String agency, LocalDate fileDate) {
    }

    // "FY2026_012_Contracts_Full_20260906.zip" or "FY(All)_012_Contracts_Delta_20260906.zip".
    private static final Pattern CONTRACT_FILE =
            Pattern.compile("^FY(?:\\d{4}|\\(All\\))_([0-9A-Za-z]+)_Contracts_(Full|Delta)_(\\d{8})\\.zip$");

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final URI archiveUrl;

    ArchiveListing(IngestProperties properties) {
        this.archiveUrl = properties.archiveUrl();
    }

    /**
     * Contract files of one kind whose names start with {@code prefix}, limited to {@code agencies} unless it is
     * empty. The all-agency file is left out because it repeats the per-agency files.
     */
    List<ArchiveFile> contractFiles(String prefix, Kind kind, Set<String> agencies)
            throws IOException, InterruptedException {
        return keys(prefix).stream()
                .map(CONTRACT_FILE::matcher)
                .filter(Matcher::matches)
                .filter(file -> file.group(2).equals(kind.name()) && !file.group(1).equals("All"))
                .filter(file -> agencies.isEmpty() || agencies.contains(file.group(1)))
                .map(file -> new ArchiveFile(file.group(), archiveUrl.resolve(file.group()), file.group(1),
                        LocalDate.parse(file.group(3), DateTimeFormatter.BASIC_ISO_DATE)))
                .toList();
    }

    private List<String> keys(String prefix) throws IOException, InterruptedException {
        var keys = new ArrayList<String>();
        String marker = "";
        while (true) {
            Document page = fetch(URI.create(archiveUrl + "?prefix=" + encode(prefix) + "&marker=" + encode(marker)));
            var pageKeys = page.getElementsByTagName("Key");
            for (int i = 0; i < pageKeys.getLength(); i++) {
                keys.add(pageKeys.item(i).getTextContent());
            }
            // S3 returns at most 1,000 keys a page; the next page starts after the last key returned.
            boolean truncated = "true".equals(page.getElementsByTagName("IsTruncated").item(0).getTextContent());
            if (!truncated || pageKeys.getLength() == 0) {
                return keys;
            }
            marker = keys.getLast();
        }
    }

    private Document fetch(URI uri) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).header("User-Agent", SourceFileStore.USER_AGENT).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("GET " + uri + " returned " + response.statusCode());
            }
            var factory = DocumentBuilderFactory.newInstance();
            // The listing comes from outside, so refuse DTDs and with them external entities.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(body);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Unreadable archive listing at " + uri, e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
