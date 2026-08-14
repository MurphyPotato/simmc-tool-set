package com.murphypotato.simmctoolset.internal.map.parse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Converts untrusted squaremap HTML to bounded plain text without retaining executable content. */
public final class HtmlTextSanitizer {
    static final int MAX_INPUT_CHARS = 131_072;
    static final int MAX_OUTPUT_CHARS = 65_536;
    static final int MAX_DOM_NODES = 10_000;
    static final int MAX_DOM_DEPTH = 256;
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[\\t\\x0B\\f ]+");

    private HtmlTextSanitizer() {
    }

    public static String sanitize(String html) {
        return parse(html).text();
    }

    /** Parses once so the Lands field parser can safely reuse the same bounded DOM. */
    static SanitizedDocument parse(String html) {
        if (html == null || html.isBlank()) {
            return emptyDocument();
        }
        String boundedInput = html.length() <= MAX_INPUT_CHARS ? html : html.substring(0, MAX_INPUT_CHARS);
        try {
            Document document = Jsoup.parseBodyFragment(boundedInput);
            document.select("script,style,noscript,template").remove();
            Traversal traversal = traverse(document.body());
            return new SanitizedDocument(document, traversal.text(), traversal.elements());
        } catch (RuntimeException ignoredMalformedHtml) {
            return emptyDocument();
        }
    }

    static String textOf(Node node) {
        return traverse(node).text();
    }

    static String normalizePlainText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] rawLines = text.replace('\u00A0', ' ').replace("\r", "\n").split("\\n");
        List<String> lines = new ArrayList<>();
        int length = 0;
        for (String rawLine : rawLines) {
            String line = HORIZONTAL_SPACE.matcher(rawLine).replaceAll(" ").trim();
            if (line.isEmpty()) {
                continue;
            }
            int separator = lines.isEmpty() ? 0 : 1;
            int remaining = MAX_OUTPUT_CHARS - length - separator;
            if (remaining <= 0) {
                break;
            }
            if (line.length() > remaining) {
                line = line.substring(0, remaining);
            }
            lines.add(line);
            length += separator + line.length();
        }
        return String.join("\n", lines);
    }

    private static Traversal traverse(Node root) {
        StringBuilder text = new StringBuilder();
        List<Element> elements = new ArrayList<>();
        ArrayDeque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(root, 0, false));
        int visited = 0;
        while (!stack.isEmpty() && visited < MAX_DOM_NODES && text.length() < MAX_OUTPUT_CHARS) {
            Frame frame = stack.pop();
            Node node = frame.node();
            if (frame.exit()) {
                if (node instanceof Element element && element.tag().isBlock()) {
                    appendBoundary(text);
                }
                continue;
            }
            if (frame.depth() > MAX_DOM_DEPTH) {
                continue;
            }
            visited++;
            if (node instanceof TextNode textNode) {
                appendLimited(text, textNode.getWholeText());
                continue;
            }
            if (node instanceof Element element) {
                elements.add(element);
                if (element.normalName().equals("br")) {
                    appendLimited(text, "\n");
                } else if (element.tag().isBlock()) {
                    appendBoundary(text);
                }
            }
            stack.push(new Frame(node, frame.depth(), true));
            List<Node> children = node.childNodes();
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(new Frame(children.get(index), frame.depth() + 1, false));
            }
        }
        return new Traversal(normalizePlainText(text.toString()), List.copyOf(elements));
    }

    private static void appendBoundary(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            appendLimited(text, "\n");
        }
    }

    private static void appendLimited(StringBuilder target, String value) {
        int remaining = MAX_OUTPUT_CHARS - target.length();
        if (remaining > 0 && value != null && !value.isEmpty()) {
            target.append(value, 0, Math.min(remaining, value.length()));
        }
    }

    private static SanitizedDocument emptyDocument() {
        Document document = Jsoup.parseBodyFragment("");
        return new SanitizedDocument(document, "", List.of());
    }

    record SanitizedDocument(Document document, String text, List<Element> elements) {
        SanitizedDocument {
            elements = List.copyOf(elements);
        }
    }

    private record Traversal(String text, List<Element> elements) {
    }

    private record Frame(Node node, int depth, boolean exit) {
    }
}
