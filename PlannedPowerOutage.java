//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS info.picocli:picocli:4.7.6
//DEPS org.jsoup:jsoup:1.18.1

package com.gmail.nickorfas;

import java.io.IOException;
import java.text.Normalizer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import picocli.CommandLine;

@CommandLine.Command
public class PlannedPowerOutage implements Runnable {
    private static final List<String> KEYWORDS = List.of("Βασιλειές", "Σύλαμο", "Ορφανουδάκη");
    private static final int PREFECTURE_ID = 21;
    private static final int MUNICIPALITY_ID = 413;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PlannedPowerOutage()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        List<String> normalizedUpperCasedKeywords = KEYWORDS.stream().map(this::normalizeAndUpperCaseText).toList();

        boolean hasMorePages;
        var pageNumber = 1;
        List<AbstractMap.SimpleEntry<String, Element>> totalItems = new ArrayList<>();
        do {
            var document = retrieveNormalizedInfoFromDeddie(pageNumber);

            var items = document.stream()
                    .filter(element -> element.nameIs("td"))
                    .map(element -> new AbstractMap.SimpleEntry<>(normalizeAndUpperCaseText(element), element))
                    .filter(element -> normalizedUpperCasedKeywords.stream().anyMatch(keyword -> element.getKey().contains(keyword)))
                    .toList();

            totalItems.addAll(items);

            pageNumber++;
            hasMorePages = document.select("ul.pagination li a").stream().anyMatch(nextPageExists(pageNumber));
        } while (hasMorePages);

        if (totalItems.isEmpty()) {
            System.out.println(("You are lucky this time. There are no power outages planned for your area."));
        } else {
            System.out.println(("Here we go again. Planned power outage(s):"));
            totalItems.forEach(item -> {
                Elements tds = item.getValue().parent().select("td");
                String plannedPowerOutageItemMessage = "* %s - %s: %s".formatted(normalizeAndUpperCaseText(tds.get(0)), normalizeAndUpperCaseText(tds.get(1)), item.getKey());
                System.out.println(plannedPowerOutageItemMessage);
            });
        }
    }

    private Predicate<Element> nextPageExists(int pageNumber) {
        return page -> page.text().equals(String.valueOf(pageNumber));
    }

    private Document retrieveNormalizedInfoFromDeddie(int page) {
        try {
            return Jsoup.connect("https://siteapps.deddie.gr/Outages2Public/Home/OutagesPartial?prefectureID=%d&MunicipalityID=%d&page=%d"
                            .formatted(PREFECTURE_ID, MUNICIPALITY_ID, page))
                    .get();
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