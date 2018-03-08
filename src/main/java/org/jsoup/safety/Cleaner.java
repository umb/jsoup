package org.jsoup.safety;

import org.jsoup.helper.Validate;
import org.jsoup.nodes.*;
import org.jsoup.parser.ParseErrorList;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.List;


/**
 * The whitelist based HTML cleaner. Use to ensure that end-user provided HTML contains only the elements and attributes
 * that you are expecting; no junk, and no cross-site scripting attacks!
 * <p>
 * The HTML cleaner parses the input as HTML and then runs it through a white-list, so the output HTML can only contain
 * HTML that is allowed by the whitelist.
 * </p>
 * <p>
 * It is assumed that the input HTML is a body fragment; the clean methods only pull from the source's body, and the
 * canned white-lists only allow body contained tags.
 * </p>
 * <p>
 * Rather than interacting directly with a Cleaner object, generally see the {@code clean} methods in {@link org.jsoup.Jsoup}.
 * </p>
 */
public class Cleaner {
    private Whitelist whitelist;

    /**
     * Create a new cleaner, that sanitizes documents using the supplied whitelist.
     *
     * @param whitelist white-list to clean with
     */
    public Cleaner(Whitelist whitelist) {
        Validate.notNull(whitelist);
        this.whitelist = whitelist;
    }

    /**
     * Creates a new, clean cleanedDocument, from the original dirty cleanedDocument, containing only elements allowed by the whitelist.
     * The original cleanedDocument is not modified. Only elements from the dirt cleanedDocument's <code>body</code> are used.
     *
     * @param dirtyDocument Untrusted base cleanedDocument to clean.
     *
     * @return cleaned cleanedDocument.
     */
    public CleaningResult clean(Document dirtyDocument) {
        Validate.notNull(dirtyDocument);

        Document cleanDocument = Document.createShell(dirtyDocument.baseUri());
        CleaningResult cleaningResult = new CleaningResult();

        // frameset documents won't have a body. the clean doc will have empty body.
        if (dirtyDocument.body() != null) {
            copySafeNodes(dirtyDocument.body(), cleanDocument.body(), cleaningResult);
        }

        cleaningResult.setCleanedDocument(cleanDocument);

        return cleaningResult;
    }

    /**
     * Determines if the input cleanedDocument <b>body</b>is valid, against the whitelist. It is considered valid if all the tags and attributes
     * in the input HTML are allowed by the whitelist, and that there is no content in the <code>head</code>.
     * <p>
     * This method can be used as a validator for user input. An invalid cleanedDocument will still be cleaned successfully
     * using the {@link #clean(Document)} cleanedDocument. If using as a validator, it is recommended to still clean the cleanedDocument
     * to ensure enforced attributes are set correctly, and that the output is tidied.
     * </p>
     *
     * @param dirtyDocument cleanedDocument to test
     *
     * @return true if no tags or attributes need to be removed; false if they do
     */
    public boolean isValid(Document dirtyDocument) {
        Validate.notNull(dirtyDocument);

        Document clean = Document.createShell(dirtyDocument.baseUri());
        int numDiscarded = copySafeNodes(dirtyDocument.body(), clean.body());
        return numDiscarded == 0 && dirtyDocument.head()
                                                 .childNodes()
                                                 .size() == 0; // because we only look at the body, but we start from a shell, make sure there's nothing in the head
    }

    public boolean isValidBodyHtml(String bodyHtml) {
        Document clean = Document.createShell("");
        Document dirty = Document.createShell("");
        ParseErrorList errorList = ParseErrorList.tracking(1);
        List<Node> nodes = Parser.parseFragment(bodyHtml, dirty.body(), "", errorList);
        dirty.body().insertChildren(0, nodes);
        int numDiscarded = copySafeNodes(dirty.body(), clean.body());
        return numDiscarded == 0 && errorList.size() == 0;
    }


    /**
     * Parses the html-string and returns all parse errors
     *
     * @param html
     *
     * @return List of parse-errors
     */
    public ParseErrorList checkForParseErrors(String html) {
        Document clean = Document.createShell("");
        Document dirty = Document.createShell("");
        ParseErrorList errorList = ParseErrorList.tracking(1);
        List<Node> nodes = Parser.parseFragment(html, dirty.body(), "", errorList);
        return errorList;
    }

    private int copySafeNodes(Element source, Element dest) {
        return copySafeNodes(source, dest, new CleaningResult());
    }

    private int copySafeNodes(Element source, Element dest, CleaningResult cleaningResult) {
        CleaningVisitor cleaningVisitor = new CleaningVisitor(source, dest, cleaningResult);
        NodeTraversor.traverse(cleaningVisitor, source);
        return cleaningResult.getRemovedAttributes().size() + cleaningResult.getRemovedNodes().size();
    }

    private Element createSafeElement(Element sourceEl, CleaningResult cleaningResult) {
        String sourceTag = sourceEl.tagName();
        Attributes destAttrs = new Attributes();
        Element dest = new Element(Tag.valueOf(sourceTag), sourceEl.baseUri(), destAttrs);

        Attributes sourceAttrs = sourceEl.attributes();

        for (Attribute sourceAttr : sourceAttrs) {
            if (whitelist.isSafeAttribute(sourceTag, sourceEl, sourceAttr)) {
                destAttrs.put(sourceAttr);
            } else {
                cleaningResult.getRemovedAttributes().add(sourceAttr);
            }
        }

        Attributes enforcedAttrs = whitelist.getEnforcedAttributes(sourceTag);
        destAttrs.addAll(enforcedAttrs);

        return dest;
    }

    /**
     * Iterates the input and copies trusted nodes (tags, attributes, text) into the destination.
     */
    private final class CleaningVisitor implements NodeVisitor {
        private final Element root;
        private final CleaningResult cleaningResult;
        private Element destination; // current element to append nodes to

        private CleaningVisitor(Element root, Element destination, CleaningResult cleaningResult) {
            this.root = root;
            this.destination = destination;
            this.cleaningResult = cleaningResult;
        }

        public void head(Node source, int depth) {
            if (source instanceof Element) {
                Element sourceEl = (Element) source;

                if (whitelist.isSafeTag(sourceEl.tagName())) { // safe, clone and copy safe attrs
                    Element destChild = createSafeElement(sourceEl, cleaningResult);
                    destination.appendChild(destChild);

                    destination = destChild;
                } else if (source != root) { // not a safe tag, so don't add. don't count root against discarded.
                    cleaningResult.getRemovedNodes().add(source);
                }
            } else if (source instanceof TextNode) {
                TextNode sourceText = (TextNode) source;
                TextNode destText = new TextNode(sourceText.getWholeText());
                destination.appendChild(destText);
            } else if (source instanceof DataNode && whitelist.isSafeTag(source.parent().nodeName())) {
                DataNode sourceData = (DataNode) source;
                DataNode destData = new DataNode(sourceData.getWholeData());
                destination.appendChild(destData);
            } else { // else, we don't care about comments, xml proc instructions, etc
                cleaningResult.getRemovedNodes().add(source);
            }
        }

        public void tail(Node source, int depth) {
            if (source instanceof Element && whitelist.isSafeTag(source.nodeName())) {
                destination = destination.parent(); // would have descended, so pop destination stack
            }
        }
    }

}