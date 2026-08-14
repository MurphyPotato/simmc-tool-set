package com.murphypotato.simmctoolset.internal.map.parse;

import com.murphypotato.simmctoolset.internal.map.model.LandDetails;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Label-driven, bounded parser for the public Chinese SIMMC Lands popup. */
public final class LandDetailsParser {
    static final int MAX_MEMBERS = 256;
    static final int MAX_TERRITORIES = 512;
    static final int MAX_UNKNOWN_LINES = 256;
    static final int MAX_DOM_DEPTH = HtmlTextSanitizer.MAX_DOM_DEPTH;
    private static final Pattern OWNER_FALLBACK = Pattern.compile(
            "^(?:.*[,，。;；]\\s*)?所有者\\s*[:：]\\s*([^,，。.;；\\s]+)(?:[,，。.;；].*)?$");
    private static final Pattern NATION = Pattern.compile("这块地属于国家\\s*(.+?)[：:]?$");
    private static final Pattern PLAYERS = Pattern.compile("玩家(?:数量)?\\s*[（(]\\s*(\\d+)\\s*[)）]\\s*[:：]\\s*(.*)");
    private static final Pattern TERRITORIES = Pattern.compile(
            "领土\\s*[（(]\\s*数量\\s*[:：]\\s*(\\d+)\\s*[,，]\\s*玩家(?:数量)?\\s*[:：]\\s*(\\d+)\\s*[)）]\\s*[:：]\\s*(.*)");

    public Optional<LandDetails> parse(String html) {
        return parse(HtmlTextSanitizer.parse(html));
    }

    Optional<LandDetails> parse(HtmlTextSanitizer.SanitizedDocument sanitized) {
        if (sanitized.text().isBlank()) {
            return Optional.empty();
        }
        Header header = parseHeader(sanitized.elements());
        Builder values = new Builder(sanitized.text(), header);
        values.recognizeHeader();

        boolean nationSection = false;
        for (Element element : sanitized.elements()) {
            String tag = element.normalName();
            if ((tag.equals("strong") && !hasAncestorTag(element, "strong")) || tag.equals("p")) {
                String line = tag.equals("p")
                        ? HtmlTextSanitizer.normalizePlainText(element.ownText())
                        : HtmlTextSanitizer.textOf(element);
                Matcher nation = NATION.matcher(line);
                if (nation.matches()) {
                    values.nationName = optional(nation.group(1));
                    values.recognized.add(line);
                    nationSection = true;
                }
            }
            if (tag.equals("li") && !hasAncestorTag(element, "li")) {
                String line = HtmlTextSanitizer.textOf(element);
                if (values.acceptListItem(line, nationSection)) {
                    values.recognized.add(line);
                }
            }
        }

        values.findOwnerFallback();
        values.classifyRemainingLines();
        return Optional.of(values.build());
    }

    private static Header parseHeader(List<Element> elements) {
        Element header = null;
        for (Element element : elements) {
            if (element.normalName().equals("div") &&
                    (element.hasClass("infowindow") || element.className().contains("infowindow"))) {
                header = element;
                break;
            }
        }
        if (header == null) {
            for (Element element : elements) {
                if (element.normalName().equals("br")) {
                    Element ancestor = element.parent();
                    while (ancestor != null) {
                        if (ancestor.normalName().equals("div")) {
                            header = ancestor;
                            break;
                        }
                        ancestor = ancestor.parent();
                    }
                    if (header != null) {
                        break;
                    }
                }
            }
        }
        if (header == null) {
            return new Header(Optional.empty(), Optional.empty(), List.of());
        }

        String headerText = HtmlTextSanitizer.textOf(header);
        List<String> lines = headerText.lines().filter(line -> !line.isBlank()).toList();
        Element titleElement = null;
        for (Element element : elements) {
            if (element.normalName().equals("span") && element.attr("style").contains("font-size")
                    && isDescendantOf(element, header)) {
                titleElement = element;
                break;
            }
        }
        String title = titleElement == null ? (lines.isEmpty() ? "" : lines.getFirst())
                : HtmlTextSanitizer.textOf(titleElement).replace('\n', ' ').trim();
        Optional<String> name = optional(title);
        List<String> descriptionLines = new ArrayList<>(lines);
        if (!descriptionLines.isEmpty() && name.isPresent() && descriptionLines.getFirst().equals(name.get())) {
            descriptionLines.removeFirst();
        }
        Optional<String> description = optional(String.join("\n", descriptionLines));
        return new Header(name, description, lines);
    }

    private static boolean hasAncestorTag(Element element, String tag) {
        Element ancestor = element.parent();
        while (ancestor != null) {
            if (ancestor.normalName().equals(tag)) {
                return true;
            }
            ancestor = ancestor.parent();
        }
        return false;
    }

    private static boolean isDescendantOf(Element element, Element ancestor) {
        Element current = element.parent();
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }

    private static Optional<String> optional(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().replaceFirst("[：:]$", "").trim();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

    private static List<String> names(String value, int limit) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(limit, 32));
        for (String item : value.split("[,，]")) {
            String name = item.trim();
            if (!name.isEmpty()) {
                result.add(name);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private record Header(Optional<String> name, Optional<String> description, List<String> lines) {
        private Header {
            lines = List.copyOf(lines);
        }
    }

    private static final class Builder {
        private final String rawText;
        private final Header header;
        private final Set<String> recognized = new HashSet<>();
        private Optional<String> level = Optional.empty();
        private Optional<String> owner = Optional.empty();
        private Optional<String> balance = Optional.empty();
        private OptionalInt chunks = OptionalInt.empty();
        private OptionalInt playerCount = OptionalInt.empty();
        private List<String> members = List.of();
        private Optional<String> nationName = Optional.empty();
        private Optional<String> nationLevel = Optional.empty();
        private Optional<String> nationCapital = Optional.empty();
        private OptionalInt territoryCount = OptionalInt.empty();
        private OptionalInt nationPlayerCount = OptionalInt.empty();
        private List<String> territories = List.of();
        private final List<String> unknown = new ArrayList<>();

        private Builder(String rawText, Header header) {
            this.rawText = rawText;
            this.header = header;
        }

        private void recognizeHeader() {
            recognized.addAll(header.lines());
        }

        private boolean acceptListItem(String line, boolean nationSection) {
            Matcher players = PLAYERS.matcher(line);
            Matcher territoriesMatcher = TERRITORIES.matcher(line);
            if (players.matches() && !nationSection) {
                playerCount = integer(players.group(1));
                members = names(players.group(2), MAX_MEMBERS);
            } else if (territoriesMatcher.matches() && nationSection) {
                territoryCount = integer(territoriesMatcher.group(1));
                nationPlayerCount = integer(territoriesMatcher.group(2));
                territories = names(territoriesMatcher.group(3), MAX_TERRITORIES);
            } else if (hasLabel(line, "等级")) {
                if (nationSection) {
                    nationLevel = labelValue(line);
                } else {
                    level = labelValue(line);
                }
            } else if (!nationSection && hasLabel(line, "余额")) {
                balance = labelValue(line);
            } else if (!nationSection && hasLabel(line, "区块")) {
                chunks = integer(labelValue(line).orElse(""));
            } else if (!nationSection && hasLabel(line, "所有者")) {
                owner = labelValue(line);
            } else if (nationSection && hasLabel(line, "首都")) {
                nationCapital = labelValue(line);
            } else {
                return false;
            }
            return true;
        }

        private void findOwnerFallback() {
            if (owner.isPresent()) {
                return;
            }
            for (String line : rawText.lines().toList()) {
                Matcher fallback = OWNER_FALLBACK.matcher(line);
                if (fallback.matches()) {
                    owner = optional(fallback.group(1));
                    if (owner.isPresent()) {
                        return;
                    }
                }
            }
        }

        private void classifyRemainingLines() {
            for (String line : rawText.lines().toList()) {
                if (!line.isBlank() && !recognized.contains(line)) {
                    unknown.add(line);
                    if (unknown.size() == MAX_UNKNOWN_LINES) {
                        return;
                    }
                }
            }
        }

        private LandDetails build() {
            return new LandDetails(
                    header.name(), header.description(), level, owner, balance, chunks, playerCount, members,
                    nationName, nationLevel, nationCapital, territoryCount, nationPlayerCount, territories,
                    rawText, unknown
            );
        }

        private static boolean hasLabel(String line, String label) {
            return line.matches("^\\s*" + Pattern.quote(label) + "\\s*[:：].*$");
        }

        private static Optional<String> labelValue(String line) {
            int ascii = line.indexOf(':');
            int fullWidth = line.indexOf('：');
            int colon = ascii < 0 ? fullWidth : fullWidth < 0 ? ascii : Math.min(ascii, fullWidth);
            return colon < 0 ? Optional.empty() : optional(line.substring(colon + 1));
        }

        private static OptionalInt integer(String text) {
            try {
                return OptionalInt.of(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return OptionalInt.empty();
            }
        }
    }
}
