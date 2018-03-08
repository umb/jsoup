package org.jsoup.safety;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains information about the cleaning-process.
 * It saves all removed nodes and all attributes that were removed from a not removed node
 */
public class CleaningResult {
    private final List<Node> removedNodes = new ArrayList<>();
    private final List<Attribute> removedAttributes = new ArrayList<>();
    private Document cleanedDocument;


    /**
     * Get a list of all nodes that were completely removed during the cleaning process
     * @return
     */
    public List<Node> getRemovedNodes() {
        return removedNodes;
    }

    /**
     * Get a list of all attributes that were removed. Only Attributes from nodes which were not removed, are in this list
     * @return List of removed Attributes
     */
    public List<Attribute> getRemovedAttributes() {
        return removedAttributes;
    }

    /**
     * The new document created by the cleaning process
     * @return cleaned document
     */
    public Document getCleanedDocument() {
        return cleanedDocument;
    }

    public void setCleanedDocument(Document cleanedDocument) {
        this.cleanedDocument = cleanedDocument;
    }
}
