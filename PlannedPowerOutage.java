//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS info.picocli:picocli:4.7.6
//DEPS org.jsoup:jsoup:1.18.1

package com.gmail.nickorfas;

import static java.util.function.Predicate.not;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import picocli.CommandLine;

@CommandLine.Command
public class PlannedPowerOutage implements Runnable {
    @CommandLine.Option(required = true, names = {"-p", "--prefecture-id"}, description = "Prefecture ID used by Deddie website")
    int prefectureId;
    @CommandLine.Option(required = true, names = {"-m", "--municipality-id"}, description = "Municipality ID used by Deddie website")
    int municipalityId;
    @CommandLine.Parameters(arity = "0..*", description = "greek keywords that Deddie is using to specify your area/region/street. If no keywords are provided, all planned power outages in your municipality will be returned")
    List<String> keywords = new ArrayList<>();
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, hidden = true)
    boolean help;
    private static final String DEDDIE_URL = "https://siteapps.deddie.gr/Outages2Public/Home/OutagesPartial?prefectureID=%d&MunicipalityID=%d&page=%d";

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PlannedPowerOutage()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        Set<String> normalizedUpperCasedKeywords = keywords.stream()
                .map(String::trim)
                .filter(not(String::isBlank))
                .map(this::normalizeAndUpperCaseText)
                .collect(Collectors.toUnmodifiableSet());
        boolean noKeywordsProvided = normalizedUpperCasedKeywords.isEmpty();

        boolean hasMorePages;
        var pageNumber = 1;
        Set<Element> totalItems = new HashSet<>();
        do {
            var document = retrieveNormalizedInfoFromDeddie(pageNumber);

            var items = document.stream()
                    .filter(element -> element.nameIs("td"))
                    .filter(element -> noKeywordsProvided || normalizedUpperCasedKeywords.stream().anyMatch(keyword -> normalizeAndUpperCaseText(element).contains(keyword)))
                    .map(Element::parent)
                    .collect(Collectors.toUnmodifiableSet());

            totalItems.addAll(items);

            pageNumber++;
            hasMorePages = document.select("ul.pagination li a").stream().anyMatch(nextPageExists(pageNumber));
        } while (hasMorePages);

        if (totalItems.isEmpty()) {
            System.out.println(("You are lucky this time. There are no power outages planned for your area."));
        } else {
            System.out.println(("Here we go again. Planned power outage(s):"));
            totalItems.forEach(item -> {
                Elements tds = item.select("td");
                String plannedPowerOutageItemMessage = "* %s - %s: %s".formatted(tds.get(0).text(), tds.get(1).text(), tds.get(3).text());
                System.out.println(plannedPowerOutageItemMessage);
            });
        }
    }

    private Predicate<Element> nextPageExists(int pageNumber) {
        return page -> page.text().equals(String.valueOf(pageNumber));
    }

    private Document retrieveNormalizedInfoFromDeddie(int page) {
        try {
            return Jsoup.connect(DEDDIE_URL.formatted(prefectureId, municipalityId, page)).get();
        } catch (IOException e) {
            throw new RuntimeException("Error while retrieving info from Deddie website. Details: " + e);
        }
    }

    private String normalizeAndUpperCaseText(Element element) {
        return normalizeAndUpperCaseText(element.text());
    }

    private String normalizeAndUpperCaseText(String text) {
        return Normalizer.normalize(text.toUpperCase(), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    }
}