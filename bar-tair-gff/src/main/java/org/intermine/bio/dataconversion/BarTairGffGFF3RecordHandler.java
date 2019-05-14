package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2016 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for the BarTairGff dataset via GFF files.
 */

public class BarTairGffGFF3RecordHandler extends GFF3RecordHandler {

    private final Map<String, Item> pubmedIdMap = new HashMap<String, Item>();
    private final Map<String, Item> protIdMap = new HashMap<String, Item>(); // This will store Uniprot IDs

    /**
     * Create a new BarTairGffGFF3RecordHandler for the given data model.
     *
     * @param model the model for which items will be created
     */
    public BarTairGffGFF3RecordHandler(Model model) {
        super(model);
        refsAndCollections.put("Exon", "transcripts");
        refsAndCollections.put("CDS", "transcripts");
        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("LncRNA", "gene");
        refsAndCollections.put("MRNA", "gene");
        refsAndCollections.put("TransposonFragment", "transposableElement");
        refsAndCollections.put("AntisenseRNA", "gene");
        refsAndCollections.put("AntisenseLncRNA", "gene");
        refsAndCollections.put("MiRNAPrimaryTranscript", "gene");
        refsAndCollections.put("NcRNA", "gene");
        refsAndCollections.put("TRNA", "gene");
        refsAndCollections.put("TranscriptRegion", "gene");
        refsAndCollections.put("PseudogenicTranscript", "pseudogene");
        refsAndCollections.put("PseudogenicExon", "pseudogenicTranscripts");
        refsAndCollections.put("PseudogenicTRNA", "pseudogene");
        refsAndCollections.put("UORF", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        // This method is called for every line of GFF3 file(s) being read.  Features and their
        // locations are already created but not stored so you can make changes here.  Attributes
        // are from the last column of the file are available in a map with the attribute name as
        // the key.
        Item feature = getFeature();
        String clsName = feature.getClassName();
        String type = record.getType();

        // The type column in GFF3
        switch (type) {
            case "gene":
                // Set Araport/TAIR gene id
                feature.setClassName("Gene");

                // This might not be needed.
                String geneID = record.getAttributes().get("ID").iterator().next();
                feature.setAttribute("primaryIdentifier", geneID);

                // This is gene alias
                if (record.getAttributes().get("symbol") != null) {
                    String symbol = record.getAttributes().get("symbol").iterator().next();
                    feature.setAttribute("symbol", symbol);
                }

                // Use computational description as brief description
                if (record.getAttributes().get("computational_description") != null) {
                    String description = record.getAttributes().get("computational_description").iterator().next();
                    feature.setAttribute("briefDescription", description);
                }
                break;
            case "transcript":
            case "MRNA":
            case "mRNA":
                if ("MRNA".equalsIgnoreCase(type)) {
                    feature.setClassName("MRNA");
                } else {
                    feature.setClassName("Transcript");
                }

                // Set ID
                String dataID = record.getAttributes().get("ID").iterator().next();
                feature.setAttribute("primaryIdentifier", dataID);

                // Use parent as name
                if (record.getAttributes().get("Parent") != null) {
                    String description = record.getAttributes().get("Parent").iterator().next();
                    feature.setAttribute("name", description);
                }

                // The code below is from Vivek!
                // The Protein thing did not work!
                List<String> dbxrefs = record.getDbxrefs();
                if (dbxrefs != null) {
                    Iterator<String> dbxrefsIter = dbxrefs.iterator();

                    while (dbxrefsIter.hasNext()) {
                        String dbxref = dbxrefsIter.next();

                        List<String> refList = new ArrayList<String>(
                                Arrays.asList(StringUtil.split(dbxref, ",")));
                        for (String ref : refList) {
                            ref = ref.trim();
                            int colonIndex = ref.indexOf(":");
                            if (colonIndex == -1) {
                                throw new RuntimeException("external reference not understood: " + ref);
                            }

                            if (ref.startsWith("gene:") || ref.startsWith("locus:")) {
                                feature.setAttribute("secondaryIdentifier", ref);
                            } else if (ref.startsWith("PMID:")) {
                                String pmid = ref.substring(colonIndex + 1);
                                Item pubmedItem;
                                if (pubmedIdMap.containsKey(pmid)) {
                                    pubmedItem = pubmedIdMap.get(pmid);
                                } else {
                                    pubmedItem = converter.createItem("Publication");
                                    pubmedIdMap.put(pmid, pubmedItem);
                                    pubmedItem.setAttribute("pubMedId", pmid);
                                    addItem(pubmedItem);
                                }
                                addPublication(pubmedItem);
                            } else if (ref.startsWith("UniProt:")) {
                                String uniprotAcc = ref.substring(colonIndex + 1);

                                Item proteinItem;
                                if (protIdMap.containsKey(uniprotAcc)) {
                                    proteinItem = protIdMap.get(uniprotAcc);
                                } else {
                                    proteinItem = converter.createItem("Protein");
                                    proteinItem.setAttribute("primaryAccession", uniprotAcc);
                                    proteinItem.setReference("organism", getOrganism());
                                    addItem(proteinItem);

                                    protIdMap.put(uniprotAcc, proteinItem);
                                }
                                feature.setReference("protein", proteinItem);
                            } else {
                                throw new RuntimeException("unknown external reference type: " + ref);
                            }
                        }
                    }
                }

                break;
            case "exon":
            case "CDS":
                if ("exon".equalsIgnoreCase(type)) {
                    feature.setClassName("Exon");
                } else {
                    feature.setClassName("CDS");
                }

                // Again, This might not be needed
                String exonID = record.getAttributes().get("ID").iterator().next();
                feature.setAttribute("primaryIdentifier", exonID);
                break;
        }
    }
}
